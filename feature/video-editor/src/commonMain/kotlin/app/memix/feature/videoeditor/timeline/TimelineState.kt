package app.memix.feature.videoeditor.timeline

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToLong

/**
 * The timeline's fast-changing state: the time under the playhead and the zoom. Both change at frame rate (playback,
 * scrolling, pinching), so only draw and placement code reads them, never composition (mobile-performance skill).
 * Neither is saved: every open starts at 0:00 and the default zoom (spec → Entry and exit).
 */
@Stable
internal class TimelineState {
    /** The time under the fixed playhead, µs. Follows the preview, except while the timeline itself moves time. */
    var playheadUs by mutableLongStateOf(0L)

    /** The zoom, in dp per second of video. */
    var dpPerSecond by mutableFloatStateOf(DEFAULT_DP_PER_SECOND)

    /** The timeline's width in pixels, once laid out. */
    var widthPx by mutableIntStateOf(0)

    /** True while a tap on the ruler or an accessibility action animates the playhead to a time. */
    var isSeekAnimating by mutableStateOf(false)

    /**
     * True from a finger touching the timeline until it lifts. While true the preview's own position reports are
     * ignored, so a report still on its way after the touch paused playback can't pull the playhead back.
     */
    var isFingerDown = false

    /**
     * Moves time by a horizontal drag of [deltaPx] (positive when the finger moves right, which shows earlier time),
     * stopping exactly at 0:00 and at [lengthUs]. Returns the pixels used, so a fling stops at either end.
     */
    fun scrollBy(deltaPx: Float, lengthUs: Long, density: Float): Float {
        val pxPerUs = pxPerUs(dpPerSecond, density)
        if (pxPerUs <= 0f || lengthUs <= 0) return 0f
        val targetUs = playheadUs - (deltaPx / pxPerUs).roundToLong()
        val clampedUs = targetUs.coerceIn(0, lengthUs)
        val consumedPx = if (clampedUs == targetUs) deltaPx else (playheadUs - clampedUs) * pxPerUs
        playheadUs = clampedUs
        return consumedPx
    }

    /** Keeps the playhead inside a project of [lengthUs] and the zoom inside its range, after an edit or a resize. */
    fun keepInRange(lengthUs: Long, minDpPerSecond: Float) {
        playheadUs = playheadUs.coerceIn(0, lengthUs.coerceAtLeast(0))
        dpPerSecond = clampDpPerSecond(dpPerSecond, minDpPerSecond)
    }
}
