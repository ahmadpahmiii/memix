package app.memix.core.domain.project

import app.memix.core.domain.media.newMainClip
import app.memix.core.model.project.FrameFit
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.TimelineItem
import app.memix.core.model.project.TrackKind

/*
 * The video editor's media edits (spec docs/ux/specs/P1-06-clip-edits-and-audio.md → Add media, Replace). Like the
 * clip edits in TimelineEdits.kt, each returns a new Project, or this one unchanged when it can't apply, and the
 * main video track stays gap-free from 0:00 while every other track keeps its times.
 */

/** Where Add media puts a pick on the main video track (spec P1-06 → Add media → Where the items land). */
sealed interface MediaPlace {
    /**
     * The Add media tool: after the main clip under the playhead at [atUs], never splitting it. Exactly on a boundary
     * between two clips, the items go there; at or past the end of the video, at the end.
     */
    data class AfterClipAt(val atUs: Long) : MediaPlace

    /** The Add media tile at the end of the video: always at the end, wherever the playhead is. */
    data object End : MediaPlace
}

/**
 * Puts [media] on the main video track at [place], back to back in pick order, as new clips named by [clipIds] (one
 * id per item, in the same order): videos at full length with their own sound, photos for the import's photo length.
 * They take the fit every main clip shares, Fit when the clips differ (spec P1-11 → Interactions). Later main clips move
 * right by the added length. An empty project gets them from 0:00.
 */
fun Project.insertMedia(media: List<MediaRef>, clipIds: List<String>, place: MediaPlace): Project {
    require(media.size == clipIds.size) { "One clip id per added item" }
    val track = mainTrack() ?: return this
    if (media.isEmpty()) return this
    val clips = mainClipsInOrder(track)
    val index = insertionIndex(clips, place)
    val fit = sharedClipsFit() ?: FrameFit.FIT
    val newClips = media.zip(clipIds) { ref, id -> newMainClip(ref, id, startUs = 0).copy(fit = fit) }
    return replaceTrack(track.copy(items = repacked(clips.subList(0, index) + newClips + clips.subList(index, clips.size))))
}

/** Whether a picked item can take a main clip's place (spec P1-06 → Replace → What fits). */
enum class ReplaceFit {
    /** A photo, or a video at least as long as the clip. */
    FITS,

    /** A video shorter than the clip: using it would shorten the clip and pull everything after it out of sync. */
    TOO_SHORT,
}

/**
 * Whether [media] fits main clip [itemId]'s slot; null when [itemId] isn't a video or photo clip on the main track.
 * A video whose length wasn't measured counts as too short: it can't be shown to cover the slot.
 */
fun Project.replaceFit(itemId: String, media: MediaRef): ReplaceFit? {
    val clip = mainClip(itemId) ?: return null
    return when (media.kind) {
        MediaKind.IMAGE -> ReplaceFit.FITS
        MediaKind.VIDEO -> if ((media.durationUs ?: 0L) >= clip.durationUs) ReplaceFit.FITS else ReplaceFit.TOO_SHORT
        MediaKind.AUDIO -> null
    }
}

/**
 * Swaps main clip [itemId]'s picture for [media], used from its start for the clip's length. The clip keeps its id,
 * start, length, place and volume, and nothing else in the project changes. A clip whose sound was detached gets its
 * new sound attached at 0%: the detached audio clip stays as it is and goes on playing, so the slot sounds the same.
 * Returns this project when [media] doesn't fit ([replaceFit]).
 */
fun Project.replaceClipMedia(itemId: String, media: MediaRef): Project {
    if (replaceFit(itemId, media) != ReplaceFit.FITS) return this
    val track = mainTrack() ?: return this
    val clip = mainClip(itemId) ?: return this
    val replaced = clip.copy(
        source = media,
        trimInUs = 0,
        trimOutUs = clip.durationUs,
        audioDetached = false,
        volume = if (clip.audioDetached) 0f else clip.volume,
    )
    return replaceTrack(track.copy(items = track.items.map { if (it.id == itemId) replaced else it }))
}

/** The main video or photo clip with [itemId], or null. */
fun Project.mainClip(itemId: String): MediaClip? {
    val found = findItem(itemId)?.takeIf { it.track.kind == TrackKind.MAIN_VIDEO } ?: return null
    return found.item as? MediaClip
}

/** Where in the play order [place] falls. [clips] are the main clips in play order, back to back from 0:00. */
private fun insertionIndex(clips: List<TimelineItem>, place: MediaPlace): Int = when (place) {
    MediaPlace.End -> clips.size
    is MediaPlace.AfterClipAt -> {
        // Where one clip ends and the next starts, "after the clip under the playhead" would skip the clip the
        // playhead is drawn at the start of; on the line, the items go on the line. 0:00 isn't between two clips.
        val startsHere = clips.indexOfFirst { it.startUs == place.atUs }
        val under = clips.indexOfFirst { place.atUs >= it.startUs && place.atUs < it.startUs + it.durationUs }
        when {
            startsHere > 0 -> startsHere
            under >= 0 -> under + 1
            else -> clips.size
        }
    }
}
