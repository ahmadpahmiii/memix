package app.memix.core.domain.project

import app.memix.core.model.project.FreezeReverseCatalog
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.Project

/*
 * The Reverse tool's edits (P4-10, spec docs/ux/specs/P4-10-reverse-and-freeze.md → Reverse). Reverse is a switch on a video clip of the main track or of an
 * overlay layer. It changes no length, start, trim, keyframe or transition: the trimmed source plays from its last frame to its first, the sound too, and
 * the engine prepares the backward picture from the flag (a copy in the app cache, never in the project). A photo and a frozen frame have no direction.
 */

/** The video clip [itemId] that can be played backwards: a video on the main track or an overlay layer; null for a photo, a frozen frame and anything else. */
fun Project.reversibleClip(itemId: String): MediaClip? = speedClip(itemId)

/** Why Reverse can't be switched on for a clip. */
enum class ReverseRefusal {
    /** The trimmed source is over [FreezeReverseCatalog.MAX_REVERSE_SOURCE_US]: "Reverse works on clips up to 60 seconds. Split this one first." */
    TOO_LONG,
}

/** What stops Reverse from being switched on for clip [itemId]; null when it can be. Switching it off is always allowed. */
fun Project.reverseRefusal(itemId: String): ReverseRefusal? {
    val clip = reversibleClip(itemId) ?: return null
    return if (!clip.reversed && clip.sourceSpanUs > FreezeReverseCatalog.MAX_REVERSE_SOURCE_US) ReverseRefusal.TOO_LONG else null
}

/** Switches Reverse on or off for clip [itemId]; this project when the clip can't be reversed, is already as asked or is too long ([reverseRefusal]). */
fun Project.setClipReversed(itemId: String, reversed: Boolean): Project {
    val clip = reversibleClip(itemId) ?: return this
    if (clip.reversed == reversed || reverseRefusal(itemId) != null) return this
    return replaceMediaClip(itemId) { it.copy(reversed = reversed) }
}

// A length-neutral change of one clip on the main track or an overlay layer: nothing moves.
private fun Project.replaceMediaClip(itemId: String, change: (MediaClip) -> MediaClip): Project {
    val (track, _) = findItem(itemId) ?: return this
    return replaceTrack(track.copy(items = track.items.map { if (it.id == itemId && it is MediaClip) change(it) else it }))
}

/** Whether any video clip of the project, main or overlay, is reversed. */
fun Project.hasReversedClips(): Boolean = video?.tracks.orEmpty().any { track -> track.items.any { it is MediaClip && it.reversed && it.freeze == null } }

/** Whether clip [itemId]'s own sound can't be detached because the clip is reversed (a sound clip has no direction): "Reversed clips can't detach their sound." */
fun Project.detachRefusedByReverse(itemId: String): Boolean = reversibleClip(itemId)?.reversed == true
