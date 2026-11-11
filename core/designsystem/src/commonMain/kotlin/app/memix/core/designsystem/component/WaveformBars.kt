package app.memix.core.designsystem.component

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

/**
 * The bars of a sound: [barCount] values from 0 to 1, each the loudest peak in its share of [peaks] (a few peaks
 * spread over more bars repeat), then the display curve. Pure, so the shape is easy to check.
 */
fun waveformBarHeights(peaks: List<Float>, barCount: Int): FloatArray {
    if (peaks.isEmpty() || barCount <= 0) return FloatArray(0)
    return FloatArray(barCount) { bar ->
        val start = floor(bar * peaks.size.toFloat() / barCount).toInt().coerceIn(0, peaks.lastIndex)
        val end = ceil((bar + 1) * peaks.size.toFloat() / barCount).toInt().coerceIn(start + 1, peaks.size)
        var loudest = 0f
        for (index in start until end) loudest = max(loudest, peaks[index])
        loudest.coerceIn(0f, 1f).pow(DISPLAY_CURVE_EXPONENT)
    }
}

/** How many bars of [barWidth] with [gap] between them fit in [width], at least one. */
internal fun waveformBarCount(width: Float, barWidth: Float, gap: Float): Int =
    ((width + gap) / (barWidth + gap)).toInt().coerceAtLeast(1)

/**
 * Peaks are linear, so on a sound with one loud hit the rest is a thin dotted line. The square root lifts quiet bars
 * while keeping the loudest at full height. Chosen on synthetic shapes: look again with the real starter waveforms
 * (P3-12b) and change this one number if they read badly.
 */
private const val DISPLAY_CURVE_EXPONENT = 0.5f
