package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import app.memix.core.model.project.SpeedCatalog
import kotlin.math.roundToInt

/**
 * The Speed panel of video clip [itemId] (P4-09a; spec docs/ux/specs/P4-09-speed.md → The Speed panel): [speed] follows the slider while it moves
 * (nothing is committed until it is let go), [lengthUs] is the clip's length at that speed, [minSpeed] the slowest the 10-minute limit allows,
 * [keepSound] the Sound choice, and [hasSpeedSet] whether Reset would change anything.
 */
@Immutable
data class SpeedPanelUi(
    val itemId: String,
    val speed: Float,
    val lengthUs: Long,
    val minSpeed: Float,
    val keepSound: Boolean,
    val hasSpeedSet: Boolean,
) {
    /** With Keep, outside 0.5x to 2x the stretched sound gets rough: the panel says so with one hint line (spec P4-09 → Rough sound). */
    val soundIsRough: Boolean get() = keepSound && speed !in SpeedCatalog.DETACH_RANGE
}

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
