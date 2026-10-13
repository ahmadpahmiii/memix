package app.memix.feature.videoeditor.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import app.memix.core.model.project.TrackKind
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.a11y_clip_label
import memix.feature.video_editor.generated.resources.timeline_original_audio
import memix.feature.video_editor.generated.resources.track_audio
import memix.feature.video_editor.generated.resources.track_effect
import memix.feature.video_editor.generated.resources.track_meme_sound
import memix.feature.video_editor.generated.resources.track_overlay
import memix.feature.video_editor.generated.resources.track_sticker
import memix.feature.video_editor.generated.resources.track_text
import memix.feature.video_editor.generated.resources.track_video
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * How spoken text names one timeline item: "clip 2", "Boom", "Original audio", or for an item with no name its
 * track's name. Used where the item is one part of a sentence: edit announcements ("Delete: clip 2", spec P1-06 →
 * Accessibility) and trim handles ("Start of Boom", spec P1-05 → Accessibility).
 */
@Immutable
sealed interface ItemName {
    data class Clip(val number: Int) : ItemName

    data class Written(val text: String) : ItemName

    data object OriginalAudio : ItemName

    data class Unnamed(val trackKind: TrackKind) : ItemName
}

/** The track holding the item with [itemId], or null when none does. */
internal fun TimelineUi.trackOf(itemId: String): TrackUi? = tracks.firstOrNull { track -> track.items.any { it.id == itemId } }

/** The spoken name of the item with [itemId], or null when no track holds it. */
internal fun TimelineUi.nameOf(itemId: String): ItemName? {
    val track = trackOf(itemId) ?: return null
    val item = track.items.first { it.id == itemId }
    return nameOf(track, item)
}

internal fun nameOf(track: TrackUi, item: ItemUi): ItemName {
    if (track.kind == TrackKind.MAIN_VIDEO) return ItemName.Clip(item.clipNumber)
    return when (val label = item.label) {
        is ItemLabel.Written -> ItemName.Written(label.text)
        ItemLabel.OriginalAudio -> ItemName.OriginalAudio
        ItemLabel.None -> ItemName.Unnamed(track.kind)
    }
}

@Composable
internal fun spokenName(name: ItemName): String = when (name) {
    is ItemName.Clip -> stringResource(Res.string.a11y_clip_label, name.number)
    is ItemName.Written -> name.text
    ItemName.OriginalAudio -> stringResource(Res.string.timeline_original_audio)
    is ItemName.Unnamed -> stringResource(trackNameOf(name.trackKind))
}

internal fun trackNameOf(kind: TrackKind): StringResource = when (kind) {
    TrackKind.MAIN_VIDEO -> Res.string.track_video
    TrackKind.OVERLAY -> Res.string.track_overlay
    TrackKind.TEXT -> Res.string.track_text
    TrackKind.STICKER -> Res.string.track_sticker
    TrackKind.MEME_SOUND -> Res.string.track_meme_sound
    TrackKind.AUDIO -> Res.string.track_audio
    TrackKind.EFFECT -> Res.string.track_effect
}
