package app.memix.feature.videoeditor.timeline

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt
import app.memix.core.domain.project.videoLengthUs
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.ImageAdjust
import app.memix.core.model.project.look
import app.memix.core.model.project.EffectItem
import app.memix.core.model.project.MaskShape
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.SpeedCatalog
import app.memix.core.model.project.StickerItem
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TransitionCatalog
import app.memix.core.model.project.TimelineItem
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.isVoiceover
import app.memix.core.model.project.keyframeList
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

    /** The files of every sound on the timeline, for the waveforms. */
    fun soundSources(): List<MediaRef> = tracks.flatMap { track -> track.items.mapNotNull { it.sound?.source } }.distinct()

    /** Where main clip [itemId] starts, µs; null when [itemId] is null or not a main clip. */
    fun mainClipStartUs(itemId: String?): Long? = mainClip(itemId)?.startUs

    /** Where main clip [itemId] ends, µs; null when [itemId] is null or not a main clip. */
    fun mainClipEndUs(itemId: String?): Long? = mainClip(itemId)?.endUs

    private fun mainClip(itemId: String?): ItemUi? {
        if (itemId == null) return null
        return tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO }?.items?.firstOrNull { it.id == itemId }
    }

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
    /** Main video clips: where the strip's first tile starts in the source. Sounds: where the waveform starts in the file. */
    val trimInUs: Long = 0,
    val trimOutUs: Long = 0,
    /** Main video clips: 1 for the first clip, for "clip 2 of 3". 0 for every other item. */
    val clipNumber: Int = 0,
    val isPhoto: Boolean = false,
    /** A video clip whose own sound was moved to an audio track (P1-09). */
    val soundDetached: Boolean = false,
    /** A video clip with sound whose volume is 0. */
    val silenced: Boolean = false,
    /** A meme sound or an audio clip: what its waveform and its spoken name need (P3-09); null for every other item. */
    val sound: SoundLook? = null,
    /** How many keyframes the item has; above 0 it wears the keyframe badge (P4-02b). */
    val keyframeCount: Int = 0,
    /** The clip has a green screen on (P4-04): its spoken description says so. */
    val keyed: Boolean = false,
    /** The shape of the clip's mask (P4-03b), or null when it has none: its spoken description says so. */
    val masked: MaskShape? = null,
    /** The clip's filter id (P4-07), or null when it has none: its spoken description says "Filter: Vivid". */
    val filterId: String? = null,
    /** The clip has adjustments set and no filter (P4-07): its spoken description says "Adjusted". */
    val adjusted: Boolean = false,
    /** The transition into the next main clip (P4-08), when this build draws it: its cue sits on the cut, and the clip's spoken description names it. */
    val transitionId: String? = null,
    /** How fast a video clip plays (P4-09a); 1 for everything else. Above or below 1 the clip wears the retime chip and says so when read aloud. */
    val speed: Float = 1f,
    /** A video clip that plays backward (P4-10): its retime chip shows the reverse icon, its strip shows the frames in playing order, and it says so when read aloud. */
    val reversed: Boolean = false,
    /** A frozen frame (P4-10): a photo clip with the freeze icon on its chip, described as "Frozen frame". */
    val frozen: Boolean = false,
) {
    val endUs: Long get() = startUs + durationUs
}

/**
 * What a sound clip's bars follow (spec P3-09 → What a bar is): the file's peaks [source] from the item's trim-in at [speed], scaled by
 * [volume] and shaped by the two fades (µs of timeline time). [hasEffects]: any Audio effect is set, which the clip says when read aloud.
 */
@Immutable
data class SoundLook(
    val source: MediaRef,
    val speed: Float,
    val volume: Float,
    val fadeInUs: Long,
    val fadeOutUs: Long,
    val hasEffects: Boolean,
)

/** The words on an item. Names the model doesn't hold yet arrive with their tickets. */
@Immutable
sealed interface ItemLabel {
    /** Text the user wrote: a caption's first line. */
    data class Written(val text: String) : ItemLabel

    /** Detached original audio: "Original audio". */
    data object OriginalAudio : ItemLabel

    /** A voiceover take recorded in Memix (P3-08): "Voiceover". */
    data object Voiceover : ItemLabel

    /** A My sound that had no file name (P3-07): "My sound" in the app's language. */
    data object UnnamedMySound : ItemLabel

    /** A meme sound's title from the catalog, in every app language; the timeline shows the app's language. */
    data class SoundTitle(val title: LocalizedText) : ItemLabel

    /** An effect item (P4-06): the effect's name, and its intensity for the spoken description. */
    data class Effect(val effectId: String, val intensityPercent: Int) : ItemLabel

    /** Main video clips (thumbnails instead), and items whose name isn't in the model yet. */
    data object None : ItemLabel
}

/**
 * Builds the timeline from [project]. [soundTitles] are the catalog's titles by catalog id, for meme sound labels (the
 * project keeps only the id, so titles follow the app's language and later catalog fixes).
 */
internal fun timelineUiOf(project: Project, soundTitles: Map<String, LocalizedText> = emptyMap()): TimelineUi {
    val tracks = project.video?.tracks.orEmpty()
        // Stable sort: tracks of one kind keep the model's order (spec → Track order).
        .sortedBy { it.kind.ordinal }
        .filter { it.kind == TrackKind.MAIN_VIDEO || it.items.isNotEmpty() }
        .map { trackUiOf(it, soundTitles) }
    return TimelineUi(lengthUs = project.videoLengthUs(), tracks = tracks.toImmutableList())
}

private fun trackUiOf(track: Track, soundTitles: Map<String, LocalizedText>): TrackUi {
    val sorted = track.items.sortedBy { it.startUs }
    var clipNumber = 0
    val items = sorted.map { item ->
        if (track.kind == TrackKind.MAIN_VIDEO) clipNumber++
        itemUiOf(item, track.kind, if (track.kind == TrackKind.MAIN_VIDEO) clipNumber else 0, soundTitles).copy(keyframeCount = item.keyframeList().size, keyed = (item as? MediaClip)?.chromaKey != null, masked = (item as? MediaClip)?.mask?.shape, transitionId = (item as? MediaClip)?.takeIf { track.kind == TrackKind.MAIN_VIDEO }?.transitionOut?.id?.takeIf(TransitionCatalog::isBuilt), speed = (item as? MediaClip)?.let(SpeedCatalog::speedOf) ?: 1f, reversed = (item as? MediaClip)?.let { it.reversed && it.source.kind == MediaKind.VIDEO } == true, frozen = (item as? MediaClip)?.freeze != null).withLookOf(item)
    }
    return TrackUi(track.id, track.kind, items.toImmutableList(), track.muted)
}

private fun itemUiOf(item: TimelineItem, kind: TrackKind, clipNumber: Int, soundTitles: Map<String, LocalizedText>): ItemUi = when (item) {
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
    is AudioClip -> ItemUi(
        id = item.id,
        startUs = item.startUs,
        durationUs = item.durationUs,
        label = audioLabelOf(item, kind, soundTitles),
        trimInUs = item.trimInUs,
        trimOutUs = item.trimOutUs,
        sound = SoundLook(item.source, item.effects.speed, item.volume, item.effects.fadeInUs, item.effects.fadeOutUs, !item.effects.isDefault),
    )
    is TextItem -> ItemUi(item.id, item.startUs, item.durationUs, writtenLabelOf(item.text))
    // TODO(P4-12): the sticker's name from its pack.
    is StickerItem -> ItemUi(item.id, item.startUs, item.durationUs, ItemLabel.None)
    is EffectItem -> ItemUi(item.id, item.startUs, item.durationUs, ItemLabel.Effect(item.effectId, (item.intensity * PERCENT).roundToInt()))
}

// A video file on an audio track is a clip's detached sound (P1-09); a catalog sound shows its title, a My sound its name.
private fun audioLabelOf(clip: AudioClip, kind: TrackKind, soundTitles: Map<String, LocalizedText>): ItemLabel {
    if (clip.source.isVoiceover()) return ItemLabel.Voiceover
    if (kind == TrackKind.AUDIO && clip.source.kind == MediaKind.VIDEO) return ItemLabel.OriginalAudio
    return when (val origin = clip.source.origin) {
        is MediaOrigin.CatalogItem -> soundTitles[origin.itemId]?.let(ItemLabel::SoundTitle) ?: ItemLabel.None
        // A My sound keeps the name it had when it was added (P3-07), so the label survives a rename or a delete.
        is MediaOrigin.MySound -> if (origin.name.isBlank()) ItemLabel.UnnamedMySound else ItemLabel.SoundTitle(LocalizedText(mapOf("en" to origin.name)))
        is MediaOrigin.GalleryUri, is MediaOrigin.PhotoAsset, MediaOrigin.Recording -> ItemLabel.None
    }
}

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

// The clip's look as the spoken description needs it: the filter's id when there is one, else whether adjustments are set (P4-07).
private fun ItemUi.withLookOf(item: TimelineItem): ItemUi {
    val look = (item as? MediaClip)?.look ?: return this
    return copy(filterId = look.filter?.takeIf { it.intensity > 0f }?.id, adjusted = look.adjust != ImageAdjust())
}

private const val PERCENT = 100f
