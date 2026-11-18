package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import app.memix.core.model.project.SpeedCatalog
import app.memix.core.model.project.SpeedPoint
import app.memix.core.model.project.SpeedPreset
import app.memix.core.model.project.matchingPreset
import kotlin.math.roundToInt

/** The two views of the Speed panel (P4-09b): one speed for the whole clip, or a curve over it. */
enum class SpeedTab { NORMAL, CURVE }

/**
 * The Speed panel of video clip [itemId] (P4-09a; spec docs/ux/specs/P4-09-speed.md → The Speed panel): [speed] follows the slider while it moves
 * (nothing is committed until it is let go), [lengthUs] is the clip's length at that speed (or with [curve]), [minSpeed] the slowest the 10-minute limit
 * allows, [keepSound] the Sound choice, and [hasSpeedSet] whether Reset would change anything. P4-09b: [tab] is the view shown; [curve] is what the Curve
 * tab draws (the clip's own curve, or a flat line at its speed; while a point is dragged, the curve as it would be); [hasCurve] says the clip plays it;
 * [selectedPoint] is the index of the point whose controls show, or -1.
 */
@Immutable
data class SpeedPanelUi(
    val itemId: String,
    val speed: Float,
    val lengthUs: Long,
    val minSpeed: Float,
    val keepSound: Boolean,
    val hasSpeedSet: Boolean,
    val tab: SpeedTab = SpeedTab.NORMAL,
    val curve: List<SpeedPoint> = emptyList(),
    val hasCurve: Boolean = false,
    val selectedPoint: Int = NO_POINT,
) {
    /** The preset tile that is white: the curve is a preset, untouched. An edited curve has none. */
    val preset: SpeedPreset? get() = if (hasCurve) curve.matchingPreset() else null

    /** The point whose controls show, if any. */
    val selected: SpeedPoint? get() = curve.getOrNull(selectedPoint)

    /** With Keep, outside 0.5x to 2x the stretched sound gets rough: the panel says so with one hint line (spec P4-09 → Rough sound). */
    val soundIsRough: Boolean get() = keepSound && speed !in SpeedCatalog.DETACH_RANGE
}

/** What the Curve tab can do to the curve (P4-09b); the control turns each into one step, or a live preview while a drag or a hold goes on. */
sealed interface SpeedCurveAction {
    data class SelectPoint(val index: Int) : SpeedCurveAction
    data object PreviousPoint : SpeedCurveAction
    data object NextPoint : SpeedCurveAction

    /** A point is being dragged to [fraction] of the picture and [speed]; nothing is committed until [DragEnd]. */
    data class Drag(val index: Int, val fraction: Float, val speed: Float) : SpeedCurveAction
    data object DragEnd : SpeedCurveAction

    /** A tap on the graph at [fraction]: adds a point there. */
    data class Tap(val fraction: Float) : SpeedCurveAction
    data object AddPoint : SpeedCurveAction
    data object RemovePoint : SpeedCurveAction

    /** The selected point moved [count] steps (2% each; negative is earlier). [held] is a repeat while the button is down: shown, not committed. */
    data class StepTime(val count: Int, val held: Boolean) : SpeedCurveAction

    /** The selected point moved [count] places on the speed grid (negative is slower). */
    data class StepSpeed(val count: Int, val held: Boolean) : SpeedCurveAction
}

/** The value of [SpeedPanelUi.selectedPoint] with no point selected. */
const val NO_POINT = -1

/** "2", "0.5", "0.25": the speed as the tiles, the chip and the readout write it, with a point in every language (the "×" is added by the string). */
internal fun speedText(speed: Float): String {
    val hundredths = (speed * HUNDREDTHS).roundToInt()
    val whole = hundredths / HUNDREDTHS.toInt()
    val fraction = hundredths % HUNDREDTHS.toInt()
    return when {
        fraction == 0 -> "$whole"
        fraction % TENTH_IN_HUNDREDTHS == 0 -> "$whole.${fraction / TENTH_IN_HUNDREDTHS}"
        else -> "$whole.${fraction.toString().padStart(2, '0')}"
    }
}

/** "1.5": a clip's length in seconds, to a tenth and rounded down like a timecode, with a point in every language (the unit comes from the string). */
internal fun lengthSecondsText(lengthUs: Long): String {
    val tenths = lengthUs.coerceAtLeast(0) / MICROS_PER_TENTH
    return "${tenths / TENTHS_PER_SECOND}.${tenths % TENTHS_PER_SECOND}"
}

private const val MICROS_PER_TENTH = 100_000L
private const val TENTHS_PER_SECOND = 10L
private const val HUNDREDTHS = 100f
private const val TENTH_IN_HUNDREDTHS = 10
