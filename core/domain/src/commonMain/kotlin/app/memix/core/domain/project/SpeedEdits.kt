package app.memix.core.domain.project

import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.Project
import app.memix.core.model.project.SpeedCatalog
import app.memix.core.model.project.SpeedSound
import kotlin.math.ceil
import kotlin.math.roundToLong

/*
 * The Speed tool's edits (P4-09a, spec docs/ux/specs/P4-09-speed.md → How speed, sound and P3-09 fit together). They act on a video clip of the main
 * track or of an overlay layer; a photo and a freeze frame have no speed. Like every edit, each returns a new Project, or this one unchanged when it
 * can't apply, so the edit session records one undo step or none.
 *
 * A speed change moves the clip's end, never its start or its source range. In the same step: later main clips close up (ripple); an overlay that now
 * overlaps the next one on its layer moves to a free layer like a dragged one; the keyframes keep their share of the clip's length; and a transition
 * that no longer fits is settled where the step commits ([settleTransitions]). Sounds, captions, effects and overlays do not follow a ripple (P1-06).
 */

/** The video clip [itemId] on the main track or an overlay layer that has a speed to set; null for a photo, a freeze frame and anything else. */
fun Project.speedClip(itemId: String): MediaClip? {
    val clip = mainMediaClip(itemId) ?: overlayClip(itemId) ?: return null
    return clip.takeIf { it.source.kind == MediaKind.VIDEO && it.freeze == null }
}

/** The slowest speed clip [itemId] can be set to: slower would make it longer than [SpeedCatalog.MAX_CLIP_LENGTH_US]. Never above 1x, so 1x is always reachable. */
fun Project.minSpeedFor(itemId: String): Float {
    val clip = speedClip(itemId) ?: return SpeedCatalog.MIN_SPEED
    val limit = ceil(clip.sourceSpanUs.toDouble() / SpeedCatalog.MAX_CLIP_LENGTH_US * ROUNDING_STEPS) / ROUNDING_STEPS
    return limit.toFloat().coerceIn(SpeedCatalog.MIN_SPEED, SpeedCatalog.NORMAL)
}

/** How long clip [itemId] would be at [speed], µs: the readout next to the slider. */
fun Project.lengthAtSpeed(itemId: String, speed: Float): Long =
    speedClip(itemId)?.let { SpeedCatalog.timelineLengthOf(it.sourceSpanUs, usableSpeed(itemId, speed)) } ?: 0L

/** Sets clip [itemId]'s speed to [speed] (0.1x to 10x, two decimals, stopped at [minSpeedFor]); a new layer for an overlay that grew into a neighbor gets [newTrackId]. */
fun Project.setClipSpeed(itemId: String, speed: Float, newTrackId: String): Project =
    withSpeedChange(itemId, newTrackId) { it.copy(speed = usableSpeed(itemId, speed)) }

/** Makes clip [itemId]'s own sound follow its speed ([SpeedSound.KEEP]) or go silent ([SpeedSound.MUTE]). The length doesn't change. */
fun Project.setClipSoundMode(itemId: String, mode: SpeedSound): Project =
    withSpeedChange(itemId, newTrackId = null) { it.copy(soundMode = mode) }

/** Puts clip [itemId] back to 1x, no curve, sound kept: the Speed panel's Reset, one step. Back to 1x can make an overlay longer, so the layer rule applies. */
fun Project.resetClipSpeed(itemId: String, newTrackId: String): Project =
    withSpeedChange(itemId, newTrackId) { it.copy(speed = SpeedCatalog.NORMAL, speedCurve = null, soundMode = SpeedSound.KEEP) }

/** Whether Reset would change clip [itemId]: false at 1x, no curve and Keep, where the panel says "Speed is already normal". */
fun Project.hasSpeedSet(itemId: String): Boolean =
    speedClip(itemId)?.let { it.speed != SpeedCatalog.NORMAL || it.speedCurve != null || it.soundMode != SpeedSound.KEEP } == true

/**
 * Whether Detach audio has to refuse clip [itemId] because of its speed (spec P4-09 → Detach audio): the new sound clip can only follow a constant speed from
 * 0.5x to 2x with the sound kept, so a curve, a speed outside that range and Mute all explain instead.
 */
fun Project.detachRefusedBySpeed(itemId: String): Boolean {
    val clip = speedClip(itemId) ?: return false
    return clip.speedCurve != null || clip.soundMode == SpeedSound.MUTE || clip.speed !in SpeedCatalog.DETACH_RANGE
}

private const val ROUNDING_STEPS = 100.0

private fun Project.usableSpeed(itemId: String, speed: Float): Float = SpeedCatalog.cleanSpeed(speed).coerceAtLeast(minSpeedFor(itemId))

// [newTrackId] is null for a change that cannot alter the clip's length.
private fun Project.withSpeedChange(itemId: String, newTrackId: String?, change: (MediaClip) -> MediaClip): Project {
    val clip = speedClip(itemId) ?: return this
    val changed = change(clip)
    if (changed == clip) return this
    val resized = changed.withKeyframesScaled(oldLengthUs = clip.durationUs, newLengthUs = changed.durationUs)
    val mainTrack = mainTrack()
    if (mainTrack != null && mainTrack.items.any { it.id == itemId }) {
        return replaceTrack(mainTrack.copy(items = repacked(mainClipsInOrder(mainTrack).map { if (it.id == itemId) resized else it })))
    }
    val (track, _) = findItem(itemId) ?: return this
    val edited = replaceTrack(track.copy(items = track.items.map { if (it.id == itemId) resized else it }))
    return if (newTrackId != null && resized.durationUs > clip.durationUs) edited.moveItem(itemId, resized.startUs, LaneTarget.Lane(track.id), newTrackId) else edited
}

// Each keyframe keeps its share of the clip's length, so a keyframe at 40% stays at 40% (spec P4-09 → Keyframes); easing is untouched.
private fun MediaClip.withKeyframesScaled(oldLengthUs: Long, newLengthUs: Long): MediaClip {
    if (keyframes.isEmpty() || oldLengthUs <= 0 || oldLengthUs == newLengthUs) return this
    val ratio = newLengthUs.toDouble() / oldLengthUs
    return copy(keyframes = keyframes.map { it.copy(timeUs = (it.timeUs * ratio).roundToLong()) })
}
