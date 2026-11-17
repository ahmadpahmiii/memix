package app.memix.core.domain.project

import app.memix.core.model.project.Easing
import app.memix.core.model.project.ItemLook
import app.memix.core.model.project.Keyframe
import app.memix.core.model.project.KeyframeCatalog
import app.memix.core.model.project.Project
import app.memix.core.model.project.TimelineItem
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.keyframeList
import app.memix.core.model.project.staticLook
import kotlin.math.abs
import kotlin.math.pow

/*
 * The one evaluation of keyframes (P4-02; spec docs/ux/specs/P4-02b-keyframes.md → How it draws). The editor's stage, the preview and the
 * export all call it, so the three can't disagree: the engine hands it the item's keyframes and a time, and draws the look it returns.
 * Pure and allocation-light (one ItemLook per call): it runs per frame on the GL thread and per sample on the audio thread.
 */

/**
 * The look at [relativeUs] after the item's start, or null when [keyframes] is empty (the item's static look holds). Before the first
 * keyframe and after the last, that keyframe's look holds. Between two, the progress is reshaped by the left keyframe's easing, then:
 * position, rotation, opacity and volume go linearly, scale geometrically (50% to 200% passes through 100% halfway).
 * [keyframes] must be sorted by time, as every edit keeps them.
 */
fun interpolatedLook(keyframes: List<Keyframe>, relativeUs: Long): ItemLook? {
    if (keyframes.isEmpty()) return null
    val rightIndex = rightIndexOf(keyframes, relativeUs)
    if (rightIndex == 0) return keyframes.first().look
    if (rightIndex == keyframes.size) return keyframes.last().look
    val left = keyframes[rightIndex - 1]
    val right = keyframes[rightIndex]
    return blend(left.look, right.look, progressBetween(left, right, relativeUs))
}

/**
 * The volume of [interpolatedLook] at [relativeUs], as a plain float: nothing is boxed or allocated, so the audio thread can ask often.
 * 1 (unchanged) when [keyframes] is empty. Always equal to `interpolatedLook(keyframes, relativeUs)?.volume`: both share the segment and progress rules.
 */
fun volumeAt(keyframes: List<Keyframe>, relativeUs: Long): Float {
    if (keyframes.isEmpty()) return 1f
    val rightIndex = rightIndexOf(keyframes, relativeUs)
    if (rightIndex == 0) return keyframes.first().look.volume
    if (rightIndex == keyframes.size) return keyframes.last().look.volume
    val left = keyframes[rightIndex - 1]
    val right = keyframes[rightIndex]
    val progress = progressBetween(left, right, relativeUs)
    if (progress <= 0.0) return left.look.volume
    if (progress >= 1.0) return right.look.volume
    return lerp(left.look.volume, right.look.volume, progress.toFloat())
}

/** [volumeAt], or null when [keyframes] is empty (the item's own volume holds). */
fun interpolatedVolume(keyframes: List<Keyframe>, relativeUs: Long): Float? =
    if (keyframes.isEmpty()) null else volumeAt(keyframes, relativeUs)

// The index of the first keyframe after the time: 0 before or on the first keyframe (its look holds), size at or after the last.
private fun rightIndexOf(keyframes: List<Keyframe>, relativeUs: Long): Int {
    if (relativeUs <= keyframes.first().timeUs) return 0
    if (relativeUs >= keyframes.last().timeUs) return keyframes.size
    var index = 1
    while (keyframes[index].timeUs <= relativeUs) index++
    return index
}

private fun progressBetween(left: Keyframe, right: Keyframe, relativeUs: Long): Double =
    progressOf(left.easing, (relativeUs - left.timeUs).toDouble() / (right.timeUs - left.timeUs))

/** The look of [this] item at [atUs] on the project timeline: interpolated when it has keyframes, else its static look. [onOverlay] as in [staticLook]. */
fun TimelineItem.lookAt(atUs: Long, onOverlay: Boolean = false): ItemLook =
    interpolatedLook(keyframeList(), atUs - startUs) ?: staticLook(onOverlay)

/** The look of item [itemId] at [atUs] on the project timeline, or null when there is no such item. */
fun Project.lookOf(itemId: String, atUs: Long): ItemLook? {
    val (track, item) = findItem(itemId) ?: return null
    return item.lookAt(atUs, onOverlay = track.kind == TrackKind.OVERLAY)
}

/**
 * How far along a segment is, 0 to 1, once [easing] has shaped the linear fraction [t] (0 to 1) of its time. [Easing.HOLD] stays at
 * 0 until the segment is over: the jump happens at the next keyframe's time.
 */
fun progressOf(easing: Easing, t: Double): Double = when (easing) {
    Easing.LINEAR -> t
    Easing.HOLD -> 0.0
    Easing.SMOOTH, Easing.EASE_IN, Easing.EASE_OUT -> {
        val points = KeyframeCatalog.controlPoints(easing)
        if (points == null) t else bezierProgress(points[0].toDouble(), points[1].toDouble(), points[2].toDouble(), points[3].toDouble(), t)
    }
}

private fun blend(from: ItemLook, to: ItemLook, progress: Double): ItemLook {
    if (progress <= 0.0) return from
    if (progress >= 1.0) return to
    val p = progress.toFloat()
    return ItemLook(
        centerX = lerp(from.centerX, to.centerX, p),
        centerY = lerp(from.centerY, to.centerY, p),
        scale = geometricScale(from.scale, to.scale, progress),
        rotationDegrees = lerp(from.rotationDegrees, to.rotationDegrees, p),
        opacity = lerp(from.opacity, to.opacity, p),
        volume = lerp(from.volume, to.volume, p),
    )
}

private fun lerp(from: Float, to: Float, progress: Float): Float = from + (to - from) * progress

// A zero or negative scale has no ratio; the clamps keep real scales positive, so this only guards a damaged draft.
private fun geometricScale(from: Float, to: Float, progress: Double): Float =
    if (from <= 0f || to <= 0f) lerp(from, to, progress.toFloat()) else (from * (to.toDouble() / from).pow(progress)).toFloat()

/**
 * y of the cubic Bézier through (0,0), (x1,y1), (x2,y2), (1,1) at the point whose x is [x]: the CSS timing function. Newton's method
 * from the straight line, with bisection when the slope is too flat to trust it.
 */
internal fun bezierProgress(x1: Double, y1: Double, x2: Double, y2: Double, x: Double): Double {
    if (x <= 0.0) return 0.0
    if (x >= 1.0) return 1.0
    var s = x
    for (step in 0 until NEWTON_STEPS) {
        val error = bezier(s, x1, x2) - x
        if (abs(error) < BEZIER_TOLERANCE) return bezier(s, y1, y2)
        val slope = bezierSlope(s, x1, x2)
        // Too flat to trust a Newton step: finish by bisection instead.
        if (abs(slope) < MIN_SLOPE) break
        s = (s - error / slope).coerceIn(0.0, 1.0)
    }
    var low = 0.0
    var high = 1.0
    s = x
    repeat(BISECTION_STEPS) {
        if (bezier(s, x1, x2) < x) low = s else high = s
        s = (low + high) / 2
    }
    return bezier(s, y1, y2)
}

// One coordinate of the curve at parameter s, with the end points 0 and 1.
private fun bezier(s: Double, p1: Double, p2: Double): Double {
    val inverse = 1 - s
    return 3 * inverse * inverse * s * p1 + 3 * inverse * s * s * p2 + s * s * s
}

private fun bezierSlope(s: Double, p1: Double, p2: Double): Double {
    val inverse = 1 - s
    return 3 * inverse * inverse * p1 + 6 * inverse * s * (p2 - p1) + 3 * s * s * (1 - p2)
}

private const val NEWTON_STEPS = 6
private const val BISECTION_STEPS = 40
private const val BEZIER_TOLERANCE = 1e-7
private const val MIN_SLOPE = 1e-6
