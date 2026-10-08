package app.memix.core.domain.media

import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind
import kotlin.uuid.Uuid

/** How long a photo shows on the video timeline when it's added (PM decision, 7 Oct 2026). The user trims it in P1-06. */
const val PHOTO_CLIP_DURATION_US = 3_000_000L

/**
 * Adds [media] at the end of the main video track, back to back in the given order: videos at full length with
 * their own sound, photos for [PHOTO_CLIP_DURATION_US]. A new project's media starts at 0 µs. Returns a new
 * project; this one is unchanged.
 */
fun Project.withMediaAppended(media: List<MediaRef>): Project {
    val timeline = requireNotNull(video) { "Only a video project has a main video track" }
    val mainTrack = timeline.tracks.single { it.kind == TrackKind.MAIN_VIDEO }
    var nextStartUs = mainTrack.items.maxOfOrNull { it.startUs + it.durationUs } ?: 0L
    val newClips = buildList {
        for (ref in media) {
            val clip = clipFor(ref, nextStartUs)
            add(clip)
            nextStartUs += clip.durationUs
        }
    }
    val updatedMainTrack = mainTrack.copy(items = mainTrack.items + newClips)
    return copy(video = timeline.copy(tracks = timeline.tracks.map { if (it.id == mainTrack.id) updatedMainTrack else it }))
}

private fun clipFor(media: MediaRef, startUs: Long): MediaClip {
    val lengthUs = when (media.kind) {
        MediaKind.VIDEO -> requireNotNull(media.durationUs) { "A video needs its duration, measured at import" }
        MediaKind.IMAGE -> PHOTO_CLIP_DURATION_US
        MediaKind.AUDIO -> throw IllegalArgumentException("Audio goes on an audio track, not the main video track")
    }
    return MediaClip(id = Uuid.random().toString(), startUs = startUs, source = media, trimInUs = 0, trimOutUs = lengthUs)
}
