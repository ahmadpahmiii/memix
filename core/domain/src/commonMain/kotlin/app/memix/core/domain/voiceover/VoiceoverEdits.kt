package app.memix.core.domain.voiceover

import app.memix.core.domain.project.tracks
import app.memix.core.domain.project.withItemOnFreeLane
import app.memix.core.domain.project.videoLengthUs
import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind

/** A take shorter than this is not kept (spec P3-08 → What recording does). */
const val MIN_TAKE_US = 500_000L

/** A playhead this close to the end of the video can't start a take: nothing would be left to record against. */
const val NO_ROOM_AT_END_US = 500_000L

/**
 * Lands [take] (the finished file, with its measured length) as audio clip [clipId] at [placement], on the first
 * Audio lane that's free for its whole length, else on a new lane [newTrackId] (spec P3-08 → Where it lands). The clip
 * ends where the recording ended ([endUs], the preview's time when it stopped) or at the end of the video, whichever is
 * first: the take keeps a few more samples after the picture stops, and the project never lengthens to fit a sound. The
 * next take then starts at the playhead on the same lane. Nothing changes when the clip would be shorter than
 * [MIN_TAKE_US] or starts at or after the end of the video.
 */
fun Project.addVoiceover(take: MediaRef, clipId: String, placement: TakePlacement, endUs: Long, newTrackId: String): Project {
    val takeLengthUs = take.durationUs ?: return this
    val roomUs = minOf(videoLengthUs(), endUs) - placement.startUs
    val trimOutUs = minOf(takeLengthUs, placement.trimInUs + roomUs)
    val clip = AudioClip(id = clipId, startUs = placement.startUs, source = take, trimInUs = placement.trimInUs, trimOutUs = trimOutUs)
    if (roomUs <= 0 || clip.durationUs < MIN_TAKE_US) return this
    return withItemOnFreeLane(clip, TrackKind.AUDIO, tracks().filter { it.kind == TrackKind.AUDIO }, newTrackId)
}

/** Whether Record can start at [playheadUs] in a video [videoLengthUs] long: not within [NO_ROOM_AT_END_US] of the end. */
fun canRecordAt(playheadUs: Long, videoLengthUs: Long): Boolean = playheadUs < videoLengthUs - NO_ROOM_AT_END_US
