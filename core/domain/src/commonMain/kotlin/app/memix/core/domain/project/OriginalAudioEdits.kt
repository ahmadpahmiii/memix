package app.memix.core.domain.project

import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind

/*
 * The video editor's original-audio edits (spec docs/ux/specs/P1-06-clip-edits-and-audio.md → Original audio): the
 * sound recorded in the user's own videos. Like the other edits, each returns a new Project, or this one unchanged when
 * it can't apply.
 */

/** Whether every main clip's own sound is muted (`Track.muted` on the main video track). */
fun Project.isOriginalMuted(): Boolean = mainTrack()?.muted == true

/**
 * Whether any main clip still plays its own sound when unmuted: a video with a sound track whose sound isn't detached.
 * The timeline's original-audio toggle shows only then (spec P1-05 → Leading area).
 */
fun Project.hasOriginalAudio(): Boolean =
    mainTrack()?.items.orEmpty().any { it is MediaClip && it.hasOwnSound() && !it.audioDetached }

/** Mutes or unmutes every main clip's own sound at once. Detached audio isn't affected. */
fun Project.setOriginalMuted(muted: Boolean): Project {
    val track = mainTrack() ?: return this
    if (track.muted == muted) return this
    return replaceTrack(track.copy(muted = muted))
}

/** Why a main clip's sound can't be adjusted or detached, or [OriginalSound.PLAYS] when it can. */
enum class OriginalSound {
    /** A video whose own sound plays from the clip: Volume and Detach audio act. */
    PLAYS,

    /** A photo, or a video with no sound track: "This clip has no sound." */
    NONE,

    /** Already moved to its own audio clip: "This clip's sound is on its own track now. Select it there." */
    DETACHED,
}

/** The state of main clip [itemId]'s own sound; null when [itemId] isn't a main clip. */
fun Project.originalSoundOf(itemId: String): OriginalSound? {
    val clip = mainClip(itemId) ?: return null
    return when {
        !clip.hasOwnSound() -> OriginalSound.NONE
        clip.audioDetached -> OriginalSound.DETACHED
        else -> OriginalSound.PLAYS
    }
}

/**
 * Moves main clip [itemId]'s own sound to a new audio clip [audioClipId]: the same source, trim and volume, starting at
 * the clip's start, on the first Audio lane free for that span, else a new lane [newTrackId] after the last one. The
 * clip's `audioDetached` becomes true, so its sound plays only from the new clip. While every clip is muted, the new
 * clip starts at 0%, so nothing suddenly gets louder. Nothing changes unless the clip's sound plays
 * ([originalSoundOf] is [OriginalSound.PLAYS]).
 */
fun Project.detachAudio(itemId: String, audioClipId: String, newTrackId: String): Project {
    if (originalSoundOf(itemId) != OriginalSound.PLAYS) return this
    val clip = mainClip(itemId) ?: return this
    val track = mainTrack() ?: return this
    val audio = AudioClip(
        id = audioClipId,
        startUs = clip.startUs,
        source = clip.source,
        trimInUs = clip.trimInUs,
        trimOutUs = clip.trimOutUs,
        volume = if (track.muted) 0f else clip.volume,
    )
    val detached = replaceTrack(track.copy(items = track.items.map { if (it.id == itemId) clip.copy(audioDetached = true) else it }))
    return detached.withItemOnFreeLane(audio, TrackKind.AUDIO, detached.tracks().filter { it.kind == TrackKind.AUDIO }, newTrackId)
}

// A video measured with a sound track this phone can decode; media from before schema 2 isn't measured (null) and
// counts as having one, as the engine treats it (P1-03).
private fun MediaClip.hasOwnSound(): Boolean = source.kind == MediaKind.VIDEO && source.hasAudio != false
