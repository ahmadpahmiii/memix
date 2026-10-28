package app.memix.core.domain.project

import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import app.memix.core.model.project.TrackKind

/**
 * True for a video project whose main video track has no clip left, for example a new project undone back to
 * nothing. Such a draft can't play or export, so leaving the editor deletes it (owner, 8 Oct 2026). Photo
 * projects are empty by [hasNoLayers] instead.
 */
fun Project.hasNoClips(): Boolean = type == ProjectType.VIDEO && mainVideoClips().isEmpty()

/**
 * True when there is nothing worth keeping: a video with no clip, or a photo with no layer (a blank canvas, or every
 * layer deleted). Leaving the editor then deletes the draft with no message (spec P2-01 → Opening and closing).
 */
fun Project.isEmptyDraft(): Boolean = hasNoClips() || hasNoLayers()

/** How long a video project plays: where its last main video clip ends, in µs. 0 when it has no clip. */
fun Project.videoLengthUs(): Long = mainVideoClips().maxOfOrNull { it.startUs + it.durationUs } ?: 0L

/** What a draft's thumbnail shows (P1-14): [source] at [atUs] in the source file (0 for a photo). */
class DraftCover(val source: MediaRef, val atUs: Long)

/**
 * The picture a draft's thumbnail shows (P1-14, P2-01): a video's first main clip at its trim-in; a photo's lowest image
 * layer, whatever its place, opacity or visibility (no scene is rendered for it). Null when there is none.
 */
fun Project.cover(): DraftCover? {
    val clip = mainVideoClips().minByOrNull { it.startUs }
    if (clip != null) return DraftCover(clip.source, clip.trimInUs)
    val image = photo?.layers.orEmpty().firstOrNull { it is ImageLayer } as? ImageLayer ?: return null
    return DraftCover(image.source, atUs = 0L)
}

private fun Project.mainVideoClips(): List<MediaClip> =
    video?.tracks.orEmpty()
        .filter { it.kind == TrackKind.MAIN_VIDEO }
        .flatMap { it.items }
        .filterIsInstance<MediaClip>()
        .filter { it.durationUs > 0 }
