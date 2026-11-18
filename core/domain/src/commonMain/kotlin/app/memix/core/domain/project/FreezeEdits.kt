package app.memix.core.domain.project

import app.memix.core.model.project.FreezeReverseCatalog
import app.memix.core.model.project.FreezeSource
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.SpeedSound
import app.memix.core.model.project.TimelineItem

/*
 * The Freeze frame tool's edit (P4-10, spec docs/ux/specs/P4-10-reverse-and-freeze.md → Freeze frame). It acts on a main-track video clip at the playhead: the clip
 * is split there and a 2 s still of that frame, a photo-like clip, goes between the two pieces; everything after moves later by the freeze's length (the
 * main track's ripple; sounds, captions, overlays and effects stay where they are). The frame itself is read from the video by the engine into a JPEG in the
 * project's media folder ([FreezePlan.sourceTimeUs] says which), and [freezeFrame] puts that picture on the timeline.
 */

/** Where Freeze frame would put the still. */
enum class FreezePlace {
    /** Inside the clip: the clip is split and the still goes between the pieces. */
    SPLIT,

    /** Within [FreezeReverseCatalog.NO_SLIVER_US] of the clip's start: the still lands before the clip, with its first frame. */
    BEFORE,

    /** Within [FreezeReverseCatalog.NO_SLIVER_US] of the clip's end: the still lands after the clip, with its last frame. */
    AFTER,
}

/** What Freeze frame does at the playhead [atUs]: [sourceTimeUs] is the moment of the clip's video to read, as the viewer sees it there. */
data class FreezePlan(val clipId: String, val place: FreezePlace, val atUs: Long, val sourceTimeUs: Long)

/**
 * The plan for freezing main clip [clipId] at project time [atUs]. Null when the clip isn't a video on the main track (a photo or an already frozen frame has
 * nothing to hold) or the playhead isn't on it, edges included; the editor then explains.
 */
fun Project.freezePlan(clipId: String, atUs: Long): FreezePlan? {
    val clip = mainMediaClip(clipId)?.takeIf { it.source.kind == MediaKind.VIDEO && it.freeze == null } ?: return null
    if (atUs < clip.startUs || atUs > clip.startUs + clip.durationUs) return null
    val offsetUs = atUs - clip.startUs
    val place = when {
        offsetUs < FreezeReverseCatalog.NO_SLIVER_US -> FreezePlace.BEFORE
        clip.durationUs - offsetUs < FreezeReverseCatalog.NO_SLIVER_US -> FreezePlace.AFTER
        else -> FreezePlace.SPLIT
    }
    val shownUs = when (place) {
        FreezePlace.BEFORE -> clip.sourceTimeAt(0)
        FreezePlace.AFTER -> clip.sourceTimeAt(clip.durationUs)
        FreezePlace.SPLIT -> clip.sourceTimeAt(offsetUs)
    }
    return FreezePlan(clip.id, place, atUs, shownUs.coerceIn(clip.trimInUs, maxOf(clip.trimInUs, clip.trimOutUs - ONE_FRAME_US)))
}

/**
 * Holds the frame of [plan]: [picture] (the JPEG of that frame, a photo) becomes a [FreezeReverseCatalog.FREEZE_DEFAULT_US] clip [freezeId] on the main
 * track, selected by the caller. [rightPieceId] names the second half when the clip is split. The still copies the clip's look, framing and filters and
 * starts with no keyframes, speed or direction; the clip's transition moves to the piece that leads into the next clip. Returns this project when [plan]
 * no longer matches the clip (it was edited or deleted while the picture was read).
 */
fun Project.freezeFrame(plan: FreezePlan, picture: MediaRef, freezeId: String, rightPieceId: String): Project {
    val track = mainTrack() ?: return this
    val clip = mainMediaClip(plan.clipId)?.takeIf { it.freeze == null && it.source.kind == MediaKind.VIDEO } ?: return this
    val still = clip.asFreezeStill(picture, freezeId, plan.sourceTimeUs)
    val ordered = mainClipsInOrder(track)
    val index = ordered.indexOfFirst { it.id == clip.id }
    val items: List<TimelineItem> = when (plan.place) {
        FreezePlace.BEFORE -> ordered.take(index).map { it.withoutTransitionIfClip() } + still + ordered.drop(index)
        FreezePlace.AFTER -> ordered.take(index) + clip.copy(transitionOut = null) + still.copy(transitionOut = clip.transitionOut) + ordered.drop(index + 1)
        FreezePlace.SPLIT -> {
            val pieces = splitItem(clip.id, plan.atUs, rightPieceId).mainTrack()?.let { mainClipsInOrder(it) }.orEmpty()
            val left = pieces.firstOrNull { it.id == clip.id } ?: return this
            val right = pieces.firstOrNull { it.id == rightPieceId } ?: return this
            ordered.take(index) + left + still + right + ordered.drop(index + 1)
        }
    }
    return replaceTrack(track.copy(items = repacked(items)))
}

// The clip before a cut that changed has its transition dropped, as deleting the clip next to it does (P4-08).
private fun TimelineItem.withoutTransitionIfClip(): TimelineItem = if (this is MediaClip && transitionOut != null) copy(transitionOut = null) else this

// The still: the clip's own look and framing with a photo's source, 2 s, and none of a video's playback fields.
private fun MediaClip.asFreezeStill(picture: MediaRef, freezeId: String, sourceTimeUs: Long): MediaClip = copy(
    id = freezeId,
    source = picture,
    trimInUs = 0,
    trimOutUs = FreezeReverseCatalog.FREEZE_DEFAULT_US,
    volume = 1f,
    audioDetached = false,
    keyframes = emptyList(),
    transitionOut = null,
    speed = 1f,
    speedCurve = null,
    soundMode = SpeedSound.KEEP,
    reversed = false,
    freeze = FreezeSource(sourceTimeUs),
)

/** One frame at 30 fps: the last frame of a range starts this long before its end, so a frame read there is inside the clip. */
private const val ONE_FRAME_US = 33_333L
