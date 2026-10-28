package app.memix.core.model.project

import kotlin.math.ceil
import kotlin.math.floor

/*
 * The Position panel's steps (spec docs/ux/specs/P1-10-text.md → Position panel), the same for captions and layers: a
 * size ladder per kind of thing, a turn to the next 15°, a move of 1% of the canvas.
 */

/** The turn steps to the next multiple of this, so 0° is always reachable. */
const val ROTATION_STEP_DEGREES = 15f

/** One Move arrow tap: 1% of the canvas width (left, right) or height (up, down). */
const val MOVE_STEP = 0.01f

/** The sizes a Size step moves between, as scales in ascending order; 100% is always one of them. */
class SizeLadder(private val steps: List<Float>) {
    /** The next step strictly above [scale], or null at the biggest size. */
    fun above(scale: Float): Float? = steps.firstOrNull { it > scale + SCALE_TOLERANCE }

    /** The next step strictly below [scale], or null at the smallest size. */
    fun below(scale: Float): Float? = steps.lastOrNull { it < scale - SCALE_TOLERANCE }

    private companion object {
        const val SCALE_TOLERANCE = 0.001f
    }
}

/** [degrees] turned to the next multiple of [ROTATION_STEP_DEGREES]: clockwise when [clockwise], else back. */
fun nextRotation(degrees: Float, clockwise: Boolean): Float {
    val steps = degrees / ROTATION_STEP_DEGREES
    val next = if (clockwise) floor(steps + STEP_TOLERANCE) + 1 else ceil(steps - STEP_TOLERANCE) - 1
    return normalizedDegrees(next * ROTATION_STEP_DEGREES)
}

/** [degrees] as −180° (exclusive) to 180°. */
fun normalizedDegrees(degrees: Float): Float {
    var value = degrees % FULL_TURN
    if (value > HALF_TURN) value -= FULL_TURN
    if (value <= -HALF_TURN) value += FULL_TURN
    return value
}

private const val STEP_TOLERANCE = 0.001f
private const val FULL_TURN = 360f
private const val HALF_TURN = 180f
