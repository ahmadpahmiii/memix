package app.memix.core.model.project

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a clip does with its own sound when it plays at another speed (P4-09, the Speed panel's Sound choice). */
@Serializable
enum class SpeedSound {
    /** The sound follows the speed, its pitch kept. */
    @SerialName("keep") KEEP,

    /** The sound is off for the clip's whole span. Separate from Volume and from the main track's mute. */
    @SerialName("mute") MUTE,
}

/**
 * One point of a speed curve (P4-09b): [positionFraction] is where in the clip's own picture (its trimmed source, 0 to 1) and [speed] how fast
 * it plays there. Saved from schema 21 so the curve ticket needs no second bump; nothing writes or plays it before P4-09b.
 */
@Serializable
data class SpeedPoint(val positionFraction: Float, val speed: Float)

/**
 * Marks a main clip as a frozen frame (P4-10): a photo clip whose picture is one frame of a video, kept as a JPEG in the project's media folder (so it
 * plays, frames and trims exactly like a photo, and keeps working if the video is later replaced or deleted). [sourceTimeUs] is where in the
 * video the frame came from, for the editor's description of the clip; nothing else reads it.
 */
@Serializable
data class FreezeSource(val sourceTimeUs: Long)

/**
 * The numbers of the Speed tool (P4-09; spec docs/ux/specs/P4-09-speed.md, design/tokens.json → speed), and the one place that turns a clip's
 * source time into timeline time and back. The length is Media3's own sum (`SpeedProviderUtil.getDurationAfterSpeedProviderApplied`) to the nearest µs, so the
 * plan and the library agree on how long a sped clip is within a µs.
 */
object SpeedCatalog {
    const val MIN_SPEED = 0.1f
    const val MAX_SPEED = 10f
    const val NORMAL = 1f

    /** The tiles of the panel's first row, in order. */
    val TILES: List<Float> = listOf(0.25f, 0.5f, 1f, 2f, 4f, 8f)

    /** Below this the stretched sound gets rough with Keep, and Detach audio refuses (outside [DETACH_RANGE]). */
    val DETACH_RANGE: ClosedFloatingPointRange<Float> = 0.5f..2f

    /** A clip may not get longer than 10 minutes on the timeline; the slider stops there. */
    const val MAX_CLIP_LENGTH_US = 600_000_000L

    /** The slider's keyboard grid: 0.1 steps up to 2x, then 0.5 steps to 10x. */
    val GRID: List<Float> = (1..20).map { it / 10f } + (5..20).map { it / 2f }

    private const val DECIMALS_FACTOR = 100f

    /** The speed a clip really plays at: a photo and a freeze frame have none; a video's is kept to the slider's two decimals, so every place that uses it agrees. */
    fun speedOf(clip: MediaClip): Float = if (clip.source.kind == MediaKind.IMAGE || clip.freeze != null) NORMAL else cleanSpeed(clip.speed)

    /** [speed] kept in range and rounded to two decimals (the slider's resolution). */
    fun cleanSpeed(speed: Float): Float = ((speed.coerceIn(MIN_SPEED, MAX_SPEED)) * DECIMALS_FACTOR).roundToInt() / DECIMALS_FACTOR

    /** The speed at thumb position [position] (0 to 1) of the logarithmic slider: 0.1x at the left, 1x in the middle, 10x at the right. */
    fun speedAtPosition(position: Float): Float = cleanSpeed(10.0.pow(2.0 * position.coerceIn(0f, 1f) - 1.0).toFloat())

    /** Where the thumb sits (0 to 1) for [speed]; the inverse of [speedAtPosition]. */
    fun positionOfSpeed(speed: Float): Float = ((log10(speed.coerceIn(MIN_SPEED, MAX_SPEED).toDouble()) + 1.0) / 2.0).toFloat()

    /** The grid value [steps] places above ([steps] > 0) or below ([steps] < 0) [speed], for the keyboard and screen readers; stays at the ends. */
    fun gridStep(speed: Float, steps: Int): Float {
        val above = GRID.indexOfFirst { it > speed + GRID_TOLERANCE }
        val below = GRID.indexOfLast { it < speed - GRID_TOLERANCE }
        val index = if (steps > 0) (if (above < 0) GRID.lastIndex else above + steps - 1) else (if (below < 0) 0 else below + steps + 1)
        return GRID[index.coerceIn(0, GRID.lastIndex)]
    }

    private const val GRID_TOLERANCE = 0.0001f

    /**
     * How long [sourceSpanUs] of a clip's source lasts on the timeline at [speed], to the nearest µs. Media3 floors the same sum from the speed as a float,
     * which differs by at most a µs (0.1x reads 9.999999 s there); a whole µs here keeps 10 s at 0.1x showing as 10 s.
     */
    fun timelineLengthOf(sourceSpanUs: Long, speed: Float): Long =
        if (speed == NORMAL) sourceSpanUs else (sourceSpanUs * HUNDREDTHS + hundredthsOf(speed) / 2) / hundredthsOf(speed)

    /** How much source plays in [timelineLengthUs] at [speed], rounded down so the length of the result never exceeds it (two pieces of a split never overlap). */
    fun sourceSpanOf(timelineLengthUs: Long, speed: Float): Long =
        if (speed == NORMAL) timelineLengthUs else timelineLengthUs * hundredthsOf(speed) / HUNDREDTHS

    // The speed in whole hundredths (0.1x is 10): a speed has two decimals, so the lengths are integer sums with no float error.
    private fun hundredthsOf(speed: Float): Long = (speed * DECIMALS_FACTOR).roundToInt().toLong().coerceAtLeast(1)

    private const val HUNDREDTHS = 100L
}
