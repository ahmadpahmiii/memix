package app.memix.feature.videoeditor.timeline

import androidx.compose.runtime.Immutable
import app.memix.core.domain.project.videoLengthUs
import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.EffectItem
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.StickerItem
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TimelineItem
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * What the timeline draws (spec docs/ux/specs/P1-05-timeline.md): the project's tracks in the spec's fixed order,
 * each with its items sorted by start. Built once per project change, never per frame. Times are µs. Public only
 * because the editor's UiState carries it.
 */
@Immutable
data class TimelineUi(
    /** Where the main video track ends: the project's length. Items past it are washed out and don't play. */
    val lengthUs: Long,
    /** The main video track first, then overlays, text, stickers, meme sounds, audio, effects; empty ones left out. */
    val tracks: ImmutableList<TrackUi>,
) {
    /** True when there's nothing on the main video track (the defensive empty project, P1-04). */
    val isEmpty: Boolean get() = tracks.firstOrNull()?.items.isNullOrEmpty()

    /** The item with [id], or null when no track holds it (any more). */
    fun item(id: String): ItemUi? = tracks.firstNotNullOfOrNull { track -> track.items.firstOrNull { it.id == id } }

    companion object {
        val Empty = TimelineUi(lengthUs = 0, tracks = persistentListOf())
    }
}

@Immutable
data class TrackUi(
    val id: String,
    val kind: TrackKind,
    /** Sorted by start time. Items on one track may overlap (two sounds at once). */
    val items: ImmutableList<ItemUi>,
    /** The original audio of every clip is off (main video track; P1-09 toggles it). */
    val muted: Boolean,
) {
    /** The longest item, so a search by start time can find items that began before the visible window. */
    val longestItemUs: Long = items.maxOfOrNull { it.durationUs } ?: 0L
}

@Immutable
data class ItemUi(
    val id: String,
    val startUs: Long,
    val durationUs: Long,
    val label: ItemLabel,
    /** The picture source of a main video or overlay clip, for its thumbnail strip; null for every other item. */
    val media: MediaRef? = null,
    /** Main video clips: where the strip's first tile starts in the source. */
    val trimInUs: Long = 0,
    val trimOutUs: Long = 0,
    /** Main video clips: 1 for the first clip, for "clip 2 of 3". 0 for every other item. */
    val clipNumber: Int = 0,
    val isPhoto: Boolean = false,
    /** A video clip whose own sound was moved to an audio track (P1-09). */
    val soundDetached: Boolean = false,
    /** A video clip with sound whose volume is 0. */
    val silenced: Boolean = false,
) {
    val endUs: Long get() = startUs + durationUs
}

/** The words on an item. Names the model doesn't hold yet arrive with their tickets. */
@Immutable
sealed interface ItemLabel {
    /** Text the user wrote: a caption's first line. */
    data class Written(val text: String) : ItemLabel

    /** Detached original audio: "Original audio". */
    data object OriginalAudio : ItemLabel

    /** Main video clips (thumbnails instead), and items whose name isn't in the model yet. */
    data object None : ItemLabel
}

/** Builds the timeline from [project]. */
internal fun timelineUiOf(project: Project): TimelineUi {
    val tracks = project.video?.tracks.orEmpty()
        // Stable sort: tracks of one kind keep the model's order (spec → Track order).
        .sortedBy { it.kind.ordinal }
        .filter { it.kind == TrackKind.MAIN_VIDEO || it.items.isNotEmpty() }
        .map(::trackUiOf)
    return TimelineUi(lengthUs = project.videoLengthUs(), tracks = tracks.toImmutableList())
}

private fun trackUiOf(track: Track): TrackUi {
    val sorted = track.items.sortedBy { it.startUs }
    var clipNumber = 0
    val items = sorted.map { item ->
        if (track.kind == TrackKind.MAIN_VIDEO) clipNumber++
        itemUiOf(item, track.kind, if (track.kind == TrackKind.MAIN_VIDEO) clipNumber else 0)
    }
    return TrackUi(track.id, track.kind, items.toImmutableList(), track.muted)
}

private fun itemUiOf(item: TimelineItem, kind: TrackKind, clipNumber: Int): ItemUi = when (item) {
    is MediaClip -> ItemUi(
        id = item.id,
        startUs = item.startUs,
        durationUs = item.durationUs,
        label = ItemLabel.None,
        media = item.source,
        trimInUs = item.trimInUs,
        trimOutUs = item.trimOutUs,
        clipNumber = clipNumber,
        isPhoto = item.source.kind == MediaKind.IMAGE,
        soundDetached = item.audioDetached,
        silenced = item.volume == 0f && item.source.hasAudio != false && item.source.kind == MediaKind.VIDEO,
    )
    is AudioClip -> ItemUi(item.id, item.startUs, item.durationUs, audioLabelOf(item, kind))
    is TextItem -> ItemUi(item.id, item.startUs, item.durationUs, writtenLabelOf(item.text))
    // TODO(P4-12): the sticker's name from its pack.
    is StickerItem -> ItemUi(item.id, item.startUs, item.durationUs, ItemLabel.None)
    // TODO(P4-06): the effect's name from the effect registry.
    is EffectItem -> ItemUi(item.id, item.startUs, item.durationUs, ItemLabel.None)
}

// A video file on an audio track is a clip's detached sound (P1-09). TODO(P1-08): meme sound titles from the pack.
private fun audioLabelOf(clip: AudioClip, kind: TrackKind): ItemLabel =
    if (kind == TrackKind.AUDIO && clip.source.kind == MediaKind.VIDEO) ItemLabel.OriginalAudio else ItemLabel.None

private fun writtenLabelOf(text: String): ItemLabel {
    val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
    return if (firstLine == null) ItemLabel.None else ItemLabel.Written(firstLine)
}

/** Calls [action] for each item that overlaps [startUs]..[endUs], in start order, without allocating. */
internal inline fun TrackUi.forEachItemBetween(startUs: Long, endUs: Long, action: (ItemUi) -> Unit) {
    val first = firstItemReaching(items.size, startUs, longestItemUs) { items[it].startUs }
    for (index in first until items.size) {
        val item = items[index]
        if (item.startUs > endUs) break
        if (item.endUs >= startUs) action(item)
    }
}
