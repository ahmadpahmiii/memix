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

    /** A curve has 2 to 8 points (spec P4-09 → Curve) and is played in this many equal steps of the clip's picture. */
    const val MIN_CURVE_POINTS = 2
    const val MAX_CURVE_POINTS = 8
    const val CURVE_STEPS = 60

    /** How far apart (as a ratio) the steps of one played piece may be: 15%, so a piece is within 7% of the curve. */
    const val SPEED_GROUP_RATIO = 1.15

    /** A dragged point stops this far (a fraction of the picture) short of its neighbor, and the speed snaps to 1x within [SNAP_FRACTION] of the graph's height. */
    const val POINT_GAP = 0.01f
    const val SNAP_FRACTION = 0.03f

    /** The Time stepper's step, a fraction of the picture (2%). */
    const val TIME_STEP = 0.02f

    private const val DECIMALS_FACTOR = 100f

    /** The speed a clip really plays at: a photo and a freeze frame have none; a video's is kept to the slider's two decimals, so every place that uses it agrees. */
    fun speedOf(clip: MediaClip): Float = if (clip.source.kind == MediaKind.IMAGE || clip.freeze != null) NORMAL else cleanSpeed(clip.speed)

    /**
     * How [clip]'s trimmed picture maps to the timeline: the one place source time and timeline time are turned into each other. A curve wins over the
     * constant speed; a curve that is not usable (a damaged draft) is ignored.
     */
    fun retimeOf(clip: MediaClip): Retime = retimeOf(speedOf(clip), curveOf(clip), clip.sourceSpanUs)

    /** As [retimeOf] for a [speed] and an optional [curve] over a picture [spanUs] long (the engine's plan clips carry these instead of a [MediaClip]). */
    fun retimeOf(speed: Float, curve: List<SpeedPoint>?, spanUs: Long): Retime {
        val usable = usableCurve(curve)
        return if (usable == null || spanUs <= 0) ConstantRetime(speed, spanUs) else segmentsOf(usable, spanUs)
    }

    /** The curve [clip] really plays: null for a photo, a freeze frame and a clip with no usable curve. */
    fun curveOf(clip: MediaClip): List<SpeedPoint>? =
        if (clip.source.kind == MediaKind.IMAGE || clip.freeze != null) null else usableCurve(clip.speedCurve)

    /** The speed [curve] has at [fraction] (0 to 1) of the picture, for the graph and the point steppers. */
    fun curveSpeedAt(curve: List<SpeedPoint>, fraction: Float): Float = app.memix.core.model.project.curveSpeedAt(curve, fraction)

    /** [points] as the pieces the engines play over a picture [spanUs] long (the graph's length caption and the provider read the same pieces). */
    fun segmentsOf(points: List<SpeedPoint>, spanUs: Long): SpeedSegments = app.memix.core.model.project.segmentsOf(points, spanUs)

    /** The curve a cut or a split leaves for the part of the picture from [startOffsetUs] for [newSpanUs]; see [rebasedCurve]. */
    fun rebasedCurve(curve: List<SpeedPoint>, oldSpanUs: Long, startOffsetUs: Long, newSpanUs: Long): List<SpeedPoint> =
        app.memix.core.model.project.rebasedCurve(curve, oldSpanUs, startOffsetUs, newSpanUs)

    /** The speed at the clip's first (or last, [atEnd]) frame in playing order: what a trim dragged outward holds, and what a transition's spare footage plays at. */
    fun edgeSpeedOf(speed: Float, curve: List<SpeedPoint>?, atEnd: Boolean): Float {
        val usable = usableCurve(curve) ?: return speed
        return if (atEnd) usable.last().speed else usable.first().speed
    }

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
