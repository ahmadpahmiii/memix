package app.memix.core.domain.project

import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.Project
import app.memix.core.model.project.SpeedCatalog
import app.memix.core.model.project.SpeedPoint
import app.memix.core.model.project.SpeedPreset
import app.memix.core.model.project.SpeedSound
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
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

/**
 * Sets clip [itemId]'s speed to [speed] (0.1x to 10x, two decimals, stopped at [minSpeedFor]); a new layer for an overlay that grew into a neighbor gets
 * [newTrackId]. A constant speed replaces a curve (spec: setting anything in the other tab switches the clip to that mode), in the same step.
 */
fun Project.setClipSpeed(itemId: String, speed: Float, newTrackId: String): Project =
    withSpeedChange(itemId, newTrackId) { it.copy(speed = usableSpeed(itemId, speed), speedCurve = null) }

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

/**
 * The curve the Curve tab shows for clip [itemId]: its own, or for a clip at a constant speed a flat line at that speed (nothing is saved until a point is
 * added or moved). Empty for a clip that has no speed.
 */
fun Project.shownSpeedCurve(itemId: String): List<SpeedPoint> {
    val clip = speedClip(itemId) ?: return emptyList()
    return SpeedCatalog.curveOf(clip) ?: listOf(SpeedPoint(0f, SpeedCatalog.speedOf(clip)), SpeedPoint(1f, SpeedCatalog.speedOf(clip)))
}

/** How long clip [itemId] would be with [curve], µs: the graph's length caption. */
fun Project.lengthWithCurve(itemId: String, curve: List<SpeedPoint>): Long =
    speedClip(itemId)?.let { SpeedCatalog.retimeOf(SpeedCatalog.speedOf(it), curve, it.sourceSpanUs).timelineLengthUs } ?: 0L

/** Puts [preset]'s points on clip [itemId]. One step; the clip's length, keyframes, neighbors and layers follow as for a speed change. */
fun Project.applySpeedPreset(itemId: String, preset: SpeedPreset, newTrackId: String): Project =
    withSpeedChange(itemId, newTrackId) { it.copy(speedCurve = preset.points) }

/** Whether a point can be added: fewer than [SpeedCatalog.MAX_CURVE_POINTS] points exist. */
fun List<SpeedPoint>.canAddPoint(): Boolean = size < SpeedCatalog.MAX_CURVE_POINTS

/** Where Add point puts a point: the middle of the longest gap between two points. */
fun List<SpeedPoint>.widestGapMiddle(): Float {
    val gap = zipWithNext().maxByOrNull { (a, b) -> b.positionFraction - a.positionFraction } ?: return 0.5f
    return (gap.first.positionFraction + gap.second.positionFraction) / 2f
}

/**
 * Adds a point at [fraction] of clip [itemId]'s picture, at the speed the curve has there (so the shape doesn't change), and returns the project; unchanged
 * when 8 points exist or [fraction] is within [SpeedCatalog.POINT_GAP] of a point.
 */
fun Project.addSpeedPoint(itemId: String, fraction: Float, newTrackId: String): Project = withCurveChange(itemId, newTrackId) { it.withPointAdded(fraction) }

/**
 * Moves point [index] of clip [itemId]'s curve to [fraction] and [speed]; see [withPointMoved].
 */
fun Project.moveSpeedPoint(itemId: String, index: Int, fraction: Float, speed: Float, newTrackId: String): Project =
    withCurveChange(itemId, newTrackId) { it.withPointMoved(index, fraction, speed) }

/** Takes interior point [index] off clip [itemId]'s curve; the first and last points stay (the panel says so). */
fun Project.removeSpeedPoint(itemId: String, index: Int, newTrackId: String): Project = withCurveChange(itemId, newTrackId) { it.withPointRemoved(index) }

/** The curve with a point at [fraction] on it, or unchanged at 8 points or within [SpeedCatalog.POINT_GAP] of a point. */
fun List<SpeedPoint>.withPointAdded(fraction: Float): List<SpeedPoint> {
    val x = fraction.coerceIn(0f, 1f)
    if (!canAddPoint() || any { abs(it.positionFraction - x) < SpeedCatalog.POINT_GAP }) return this
    return (this + SpeedPoint(x, SpeedCatalog.cleanSpeed(SpeedCatalog.curveSpeedAt(this, x)))).sortedBy { it.positionFraction }
}

/**
 * The curve with point [index] at [fraction] and [speed]. The first and last points keep their place (only the speed moves); the others stop
 * [SpeedCatalog.POINT_GAP] short of their neighbors. The speed is kept in range and to two decimals, and snaps to 1x within the graph's snap distance.
 * Pure, so a drag can draw the curve it would make without running a whole edit.
 */
fun List<SpeedPoint>.withPointMoved(index: Int, fraction: Float, speed: Float): List<SpeedPoint> {
    if (index !in indices) return this
    val x = when (index) {
        0 -> 0f
        lastIndex -> 1f
        else -> fraction.coerceIn(this[index - 1].positionFraction + SpeedCatalog.POINT_GAP, this[index + 1].positionFraction - SpeedCatalog.POINT_GAP)
    }
    return mapIndexed { i, point -> if (i == index) SpeedPoint(x, snappedToNormal(SpeedCatalog.cleanSpeed(speed))) else point }
}

/** The curve without interior point [index]; the first and last points stay. */
fun List<SpeedPoint>.withPointRemoved(index: Int): List<SpeedPoint> =
    if (index <= 0 || index >= lastIndex || size <= SpeedCatalog.MIN_CURVE_POINTS) this else filterIndexed { i, _ -> i != index }

/** Whether clip [itemId] with [curve] would be longer than 10 minutes: the edit is refused and the panel says so. */
fun Project.curveTooLong(itemId: String, curve: List<SpeedPoint>): Boolean = lengthWithCurve(itemId, curve) > SpeedCatalog.MAX_CLIP_LENGTH_US

/** [speed] pulled to 1x when it is within the snap distance of it on the graph's log scale (3% of its height; one tick for the user). */
fun snappedToNormal(speed: Float): Float =
    if (abs(log10(speed.toDouble())) / GRAPH_DECADES < SpeedCatalog.SNAP_FRACTION) SpeedCatalog.NORMAL else speed

private const val GRAPH_DECADES = 2.0

private const val ROUNDING_STEPS = 100.0

private fun Project.usableSpeed(itemId: String, speed: Float): Float = SpeedCatalog.cleanSpeed(speed).coerceAtLeast(minSpeedFor(itemId))

// A curve edit starts from the curve the tab shows, so the first edit of a constant-speed clip saves a curve.
private fun Project.withCurveChange(itemId: String, newTrackId: String, change: (List<SpeedPoint>) -> List<SpeedPoint>): Project {
    val shown = shownSpeedCurve(itemId)
    if (shown.isEmpty()) return this
    return withSpeedChange(itemId, newTrackId) { clip ->
        val changed = change(shown)
        if (changed == shown) clip else clip.copy(speedCurve = changed)
    }
}

// [newTrackId] is null for a change that cannot alter the clip's length.
private fun Project.withSpeedChange(itemId: String, newTrackId: String?, change: (MediaClip) -> MediaClip): Project {
    val clip = speedClip(itemId) ?: return this
    val changed = change(clip)
    if (changed == clip) return this
    // A clip may not become longer than 10 minutes; the edit that would make it so does nothing (no toast, spec P4-09 → Maximum length).
    if (changed.durationUs > SpeedCatalog.MAX_CLIP_LENGTH_US && changed.durationUs > clip.durationUs) return this
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
