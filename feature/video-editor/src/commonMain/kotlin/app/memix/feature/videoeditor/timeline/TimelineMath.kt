package app.memix.feature.videoeditor.timeline

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * The timeline's layout math (spec docs/ux/specs/P1-05-timeline.md → Zoom, Ruler, Thumbnail strip). These numbers are
 * the spec's zoom range and ruler ladder, not design tokens; they live here, in one place, so the token audit can tell
 * them from hardcoded design values (spec request 4).
 */

/** One 40 dp thumbnail tile is one second (the board's spacing). */
internal const val DEFAULT_DP_PER_SECOND = 40f

/** One frame at 30 fps is 16 dp: enough to place a sound on an exact frame. */
internal const val MAX_DP_PER_SECOND = 480f

/** The zoom menu multiplies or divides the scale by this. */
internal const val ZOOM_MENU_STEP = 2f

internal const val MICROS_PER_SECOND = 1_000_000L

/** One frame at the editor's 30 fps: the playhead's accessibility step and the thumbnails' finest time step. */
internal const val FRAME_US = MICROS_PER_SECOND / 30

private const val MICROS_PER_HUNDREDTH = 10_000L
private const val HUNDREDTHS_PER_SECOND = 100L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HOUR_US = MICROS_PER_SECOND * SECONDS_PER_MINUTE * MINUTES_PER_HOUR

/**
 * Most zoomed out: the smaller of the default and the scale at which the whole video fits in half the timeline's
 * width, so all of it is on screen wherever the playhead is. The default for an empty project or before layout.
 */
internal fun minDpPerSecond(lengthUs: Long, widthDp: Float): Float {
    if (lengthUs <= 0 || widthDp <= 0f) return DEFAULT_DP_PER_SECOND
    val lengthSeconds = lengthUs.toFloat() / MICROS_PER_SECOND
    return min(DEFAULT_DP_PER_SECOND, (widthDp / 2f) / lengthSeconds)
}

internal fun clampDpPerSecond(dpPerSecond: Float, minDpPerSecond: Float): Float = dpPerSecond.coerceIn(minDpPerSecond, MAX_DP_PER_SECOND)

/** Pixels per microsecond at [dpPerSecond] on a screen of [density] pixels per dp. */
internal fun pxPerUs(dpPerSecond: Float, density: Float): Float = dpPerSecond * density / MICROS_PER_SECOND

/** Where [timeUs] sits on screen: the playhead's time is under the center line ([centerPx]). */
internal fun xOf(timeUs: Long, playheadUs: Long, centerPx: Float, pxPerUs: Float): Float = centerPx + (timeUs - playheadUs) * pxPerUs

/** The time under [x] on screen. */
internal fun timeAt(x: Float, playheadUs: Long, centerPx: Float, pxPerUs: Float): Long = playheadUs + ((x - centerPx) / pxPerUs).roundToLong()

/** One rung of the ruler's ladder: a label every [labelEveryUs], split by [minorTicks] ticks. */
internal class RulerStep(val labelEveryUs: Long, val minorTicks: Int)

/**
 * 0.25 · 0.5 · 1 · 2 · 5 · 10 · 15 · 30 s · 1 · 2 · 5 · 10 min, with the spec's minor ticks per step, plus 15 · 30 min
 * · 1 h for hour-long projects zoomed all the way out (on a 360 dp phone an hour fits in 180 dp, where 10 min is only
 * 30 dp; minor ticks follow the seconds pattern). The three extra rungs await the designer's confirmation.
 */
internal val RULER_LADDER = listOf(
    RulerStep(250_000, 5),
    RulerStep(500_000, 5),
    RulerStep(1_000_000, 4),
    RulerStep(2_000_000, 4),
    RulerStep(5_000_000, 5),
    RulerStep(10_000_000, 5),
    RulerStep(15_000_000, 3),
    RulerStep(30_000_000, 3),
    RulerStep(60_000_000, 4),
    RulerStep(120_000_000, 4),
    RulerStep(300_000_000, 5),
    RulerStep(600_000_000, 5),
    RulerStep(900_000_000, 3),
    RulerStep(1_800_000_000, 3),
    RulerStep(3_600_000_000, 4),
)

/**
 * The smallest step whose labels sit at least [minLabelSpacingPx] apart (the widest label plus `space-4`), so a
 * larger font picks a longer step by itself. Past the ladder's end (projects of several hours), whole multiples of
 * its last step.
 */
internal fun rulerStepFor(pxPerUs: Float, minLabelSpacingPx: Float): RulerStep {
    RULER_LADDER.firstOrNull { it.labelEveryUs * pxPerUs >= minLabelSpacingPx }?.let { return it }
    val longest = RULER_LADDER.last()
    if (pxPerUs <= 0f) return longest
    val multiple = ceil(minLabelSpacingPx / (longest.labelEveryUs * pxPerUs)).toLong().coerceAtLeast(1)
    return RulerStep(longest.labelEveryUs * multiple, longest.minorTicks)
}

/**
 * A ruler label: whole seconds as `MM:SS` (`H:MM:SS` from one hour), anything between as its fraction only (".25").
 * ASCII digits in every language, like the timecode.
 */
internal fun rulerLabel(timeUs: Long): String {
    val hundredths = (timeUs / MICROS_PER_HUNDREDTH) % HUNDREDTHS_PER_SECOND
    if (hundredths != 0L) return "." + hundredths.toString().padStart(2, '0')
    val totalSeconds = timeUs / MICROS_PER_SECOND
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    val totalMinutes = totalSeconds / SECONDS_PER_MINUTE
    val minutes = totalMinutes % MINUTES_PER_HOUR
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutesSeconds = minutes.toString().padStart(2, '0') + ":" + seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$minutesSeconds" else minutesSeconds
}

/**
 * The widest label the ruler can show for a project of [lengthUs]: zoomed all the way out, the trailing half shows
 * up to twice the length, so a project of half an hour or more can show hour labels.
 */
internal fun widestRulerLabel(lengthUs: Long): String = if (lengthUs * 2 >= HOUR_US) "0:00:00" else "00:00"

/**
 * The source time a clip's thumbnail tile shows. The first tile shows the clip's first frame. Every later tile shows
 * the frame at its start, rounded to a whole power-of-two number of frames no longer than the tile, so a zoom that
 * changes the tiles a little asks for frames already in memory, and is clamped inside the clip.
 */
internal fun tileSourceTimeUs(trimInUs: Long, trimOutUs: Long, tileIndex: Int, tileDurationUs: Long): Long {
    if (tileIndex == 0) return trimInUs
    val bucketUs = tileBucketUs(tileDurationUs)
    val startUs = trimInUs + tileIndex * tileDurationUs
    val roundedUs = (startUs + bucketUs / 2) / bucketUs * bucketUs
    return roundedUs.coerceIn(trimInUs, max(trimInUs, trimOutUs - FRAME_US))
}

/** The largest power-of-two number of frames that fits in [tileDurationUs]; one frame at least. */
internal fun tileBucketUs(tileDurationUs: Long): Long {
    val frames = max(1L, tileDurationUs / FRAME_US)
    return FRAME_US * frames.takeHighestOneBit()
}

/**
 * The index of the first item that can reach [timeUs] or later, in items sorted by start: no item starting before
 * `timeUs - longestUs` can. [startAt] gives the start of item `i`.
 */
internal inline fun firstItemReaching(count: Int, timeUs: Long, longestUs: Long, startAt: (Int) -> Long): Int {
    val earliestStartUs = timeUs - longestUs
    var low = 0
    var high = count
    while (low < high) {
        val middle = (low + high) ushr 1
        if (startAt(middle) < earliestStartUs) low = middle + 1 else high = middle
    }
    return low
}
