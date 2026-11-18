package app.memix.core.model.project

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * Speed curves (P4-09b; spec docs/ux/specs/P4-09-speed.md → Curve, How it plays). A curve is 2 to 8 [SpeedPoint]s: x is the place in the clip's own
 * picture in the order it plays (0 to 1 of the trimmed span, so a reversed clip's curve starts where its copy starts), y is the speed there. Between two
 * points the speed is interpolated in log speed with a cosine ease. The engines cannot play a smooth curve, so it is cut into [SpeedCatalog.CURVE_STEPS]
 * equal steps of the span, each played at one speed ([SpeedSegments]); preview and export use the same segments, so they match by construction.
 */

/** The six presets of the Curve tab (spec table); [id] is the analytics value and the key of the tile's name. */
enum class SpeedPreset(val id: String, val points: List<SpeedPoint>) {
    RAMP_UP("ramp_up", curveOf(0f to 0.5f, 1f to 4f)),
    RAMP_DOWN("ramp_down", curveOf(0f to 4f, 1f to 0.5f)),
    BULLET("bullet", curveOf(0f to 1f, 0.35f to 1f, 0.5f to 0.15f, 0.65f to 1f, 1f to 1f)),
    FLASH("flash", curveOf(0f to 1f, 0.4f to 1f, 0.5f to 6f, 0.6f to 1f, 1f to 1f)),
    PULSE("pulse", curveOf(0f to 1f, 0.2f to 3f, 0.4f to 0.5f, 0.6f to 3f, 0.8f to 0.5f, 1f to 1f)),
    SLAM("slam", curveOf(0f to 3f, 0.7f to 3f, 0.8f to 0.1f, 1f to 0.1f)),
}

private fun curveOf(vararg points: Pair<Float, Float>): List<SpeedPoint> = points.map { SpeedPoint(it.first, it.second) }

/** The preset whose points a curve still has exactly (an edited curve has none), for the selected tile. */
fun List<SpeedPoint>.matchingPreset(): SpeedPreset? = SpeedPreset.entries.firstOrNull { it.points == this }

/**
 * A speed curve as piecewise-constant pieces: piece `i` plays source offsets `sourceStartsUs[i]` until `sourceStartsUs[i + 1]` (µs from the start of the
 * clip's picture) at `speeds[i]`, which takes `timelineStartsUs[i + 1] - timelineStartsUs[i]` of timeline. The one mapping between a place in the picture
 * and a place on the timeline for a curved clip; the engine hands the same arrays to Media3's speed provider.
 */
class SpeedSegments internal constructor(
    val sourceStartsUs: LongArray,
    val timelineStartsUs: LongArray,
    val speeds: FloatArray,
) : Retime {
    val count: Int get() = speeds.size

    /** Where one piece gives way to the next, on the timeline: the places the sound is smoothed ([JoinSmootherProcessor] in the engine). */
    val joinTimesUs: LongArray get() = timelineStartsUs.copyOfRange(1, count)

    override val timelineLengthUs: Long get() = timelineStartsUs.last()
    override val pictureSpanUs: Long get() = sourceStartsUs.last()

    /** The part of the picture that plays, µs. */
    val sourceSpanUs: Long get() = sourceStartsUs.last()

    override fun timelineOf(sourceOffsetUs: Long): Long {
        val offset = sourceOffsetUs.coerceIn(0, sourceSpanUs)
        val index = pieceContaining(sourceStartsUs, offset)
        if (index >= count) return timelineLengthUs
        val sourceLength = sourceStartsUs[index + 1] - sourceStartsUs[index]
        val timelineLength = timelineStartsUs[index + 1] - timelineStartsUs[index]
        return timelineStartsUs[index] + ((offset - sourceStartsUs[index]) * timelineLength.toDouble() / sourceLength).roundToLong()
    }

    override fun sourceOf(timelineOffsetUs: Long): Long {
        val offset = timelineOffsetUs.coerceIn(0, timelineLengthUs)
        val index = pieceContaining(timelineStartsUs, offset)
        if (index >= count) return sourceSpanUs
        val sourceLength = sourceStartsUs[index + 1] - sourceStartsUs[index]
        val timelineLength = timelineStartsUs[index + 1] - timelineStartsUs[index]
        if (timelineLength <= 0) return sourceStartsUs[index]
        return sourceStartsUs[index] + ((offset - timelineStartsUs[index]) * sourceLength.toDouble() / timelineLength).toLong()
    }

    /** The speed at [sourceOffsetUs] of the picture, for the piece it falls in. */
    fun speedAtSource(sourceOffsetUs: Long): Float = speeds[pieceContaining(sourceStartsUs, sourceOffsetUs.coerceIn(0, sourceSpanUs)).coerceAtMost(count - 1)]

    // The piece [offset] falls in (its start at or before, its end after); [count] when [offset] is the very end.
    private fun pieceContaining(starts: LongArray, offset: Long): Int {
        var low = 0
        var high = count
        while (low < high) {
            val middle = (low + high) ushr 1
            if (starts[middle + 1] <= offset) low = middle + 1 else high = middle
        }
        return low
    }
}

/** What a clip's retiming does to time: the one place that turns a place in the picture into a place on the timeline and back ([SpeedCatalog.retimeOf]). */
interface Retime {
    /** How long the clip's whole picture lasts on the timeline, µs. */
    val timelineLengthUs: Long

    /** How much picture the clip plays (its trimmed span), µs. */
    val pictureSpanUs: Long

    /** The timeline time after the clip starts at which [sourceOffsetUs] of its picture (counted in playing order) is shown. */
    fun timelineOf(sourceOffsetUs: Long): Long

    /** How much of the picture, in playing order, has played [timelineOffsetUs] after the clip starts; never more than the picture. */
    fun sourceOf(timelineOffsetUs: Long): Long
}

/**
 * How much picture a handle dragged [timelineDeltaUs] at the start of the clip takes off (a negative drag puts picture back, at [edgeSpeed], the speed of the end it
 * grows from). The one rule the trim edit, the drag's preview and the frame shown while dragging share.
 */
fun Retime.pictureTakenFromStart(timelineDeltaUs: Long, edgeSpeed: Float): Long =
    if (timelineDeltaUs >= 0) sourceOf(timelineDeltaUs) else -SpeedCatalog.sourceSpanOf(-timelineDeltaUs, edgeSpeed)

/** How much the picture plays more (positive) or less (negative) when the end of the clip moves by [timelineDeltaUs]; see [pictureTakenFromStart]. */
fun Retime.pictureChangeAtEnd(timelineDeltaUs: Long, edgeSpeed: Float): Long =
    if (timelineDeltaUs <= 0) sourceOf((timelineLengthUs + timelineDeltaUs).coerceAtLeast(0)) - pictureSpanUs else SpeedCatalog.sourceSpanOf(timelineDeltaUs, edgeSpeed)

internal class ConstantRetime(private val speed: Float, private val spanUs: Long) : Retime {
    override val timelineLengthUs: Long get() = SpeedCatalog.timelineLengthOf(spanUs, speed)
    override val pictureSpanUs: Long get() = spanUs
    override fun timelineOf(sourceOffsetUs: Long): Long = SpeedCatalog.timelineLengthOf(sourceOffsetUs.coerceIn(0, spanUs), speed)
    override fun sourceOf(timelineOffsetUs: Long): Long = SpeedCatalog.sourceSpanOf(timelineOffsetUs.coerceAtLeast(0), speed).coerceAtMost(spanUs)
}

/**
 * The curve [points] as the pieces an engine plays over a picture [spanUs] long: [SpeedCatalog.CURVE_STEPS] equal steps, each at the curve's speed in its
 * middle; neighbors join into one piece while they stay within [SpeedCatalog.SPEED_GROUP_RATIO] of the piece's first step, at the mean of their log speeds
 * (so a flat part is one piece at its exact speed). Every join is a restart of the sound's time stretch (Media3 has no crossfade), so fewer is cleaner.
 */
internal fun segmentsOf(points: List<SpeedPoint>, spanUs: Long): SpeedSegments {
    val sourceStarts = ArrayList<Long>()
    val speeds = ArrayList<Float>()
    val steps = SpeedCatalog.CURVE_STEPS
    var previousEnd = 0L
    var groupFirstLog = 0.0
    var groupLogSum = 0.0
    var groupSteps = 0
    sourceStarts.add(0L)
    fun closeGroup() { if (groupSteps > 0) speeds.add(quantized(exp(groupLogSum / groupSteps).toFloat())) }
    for (step in 1..steps) {
        val end = spanUs * step / steps
        if (end <= previousEnd) continue
        val log = ln(curveSpeedAt(points, ((previousEnd + end) / 2.0 / spanUs).toFloat()).toDouble())
        if (groupSteps > 0 && abs(log - groupFirstLog) > LN_GROUP_RATIO) {
            closeGroup()
            groupLogSum = 0.0
            groupSteps = 0
        }
        if (groupSteps == 0) { groupFirstLog = log; sourceStarts.add(end) } else sourceStarts[sourceStarts.lastIndex] = end
        groupLogSum += log
        groupSteps++
        previousEnd = end
    }
    closeGroup()
    val timelineStarts = LongArray(sourceStarts.size)
    var elapsedUs = 0.0
    // The same sum Media3 makes for the item's length (SpeedProviderUtil): each piece's source length over its float speed, in doubles.
    for (index in speeds.indices) {
        elapsedUs += (sourceStarts[index + 1] - sourceStarts[index]) / speeds[index].toDouble()
        timelineStarts[index + 1] = elapsedUs.roundToLong()
    }
    return SpeedSegments(sourceStarts.toLongArray(), timelineStarts, speeds.toFloatArray())
}

private val LN_GROUP_RATIO = ln(SpeedCatalog.SPEED_GROUP_RATIO)

// The speed in whole thousandths, so the number the plan sums and the float the provider hands to Media3 are the same.
private fun quantized(speed: Float): Float {
    val thousandths = (speed * THOUSANDTHS).roundToInt().coerceIn(
        (SpeedCatalog.MIN_SPEED * THOUSANDTHS).roundToInt(),
        (SpeedCatalog.MAX_SPEED * THOUSANDTHS).roundToInt(),
    )
    return thousandths / THOUSANDTHS
}

private const val THOUSANDTHS = 1000f

/** The curve's speed at [fraction] (0 to 1) of the picture: log speed eased with a cosine between the two points around it. */
internal fun curveSpeedAt(points: List<SpeedPoint>, fraction: Float): Float {
    val x = fraction.coerceIn(0f, 1f)
    val after = points.indexOfFirst { it.positionFraction >= x }
    if (after < 0) return points.last().speed
    if (after == 0) return points.first().speed
    val from = points[after - 1]
    val to = points[after]
    val width = to.positionFraction - from.positionFraction
    if (width <= 0f) return to.speed
    val t = (x - from.positionFraction) / width
    val eased = (1.0 - cos(PI * t)) / 2.0
    return exp(ln(from.speed.toDouble()) + (ln(to.speed.toDouble()) - ln(from.speed.toDouble())) * eased).toFloat()
}

/** [points] if they make a usable curve (2 to 8, first at 0, last at 1, rising, in range), else null: a damaged draft plays at its constant speed instead. */
internal fun usableCurve(points: List<SpeedPoint>?): List<SpeedPoint>? {
    if (points == null || points.size !in SpeedCatalog.MIN_CURVE_POINTS..SpeedCatalog.MAX_CURVE_POINTS) return null
    if (points.first().positionFraction != 0f || points.last().positionFraction != 1f) return null
    val rising = points.zipWithNext().all { (a, b) -> b.positionFraction > a.positionFraction }
    val inRange = points.all { it.speed in SpeedCatalog.MIN_SPEED..SpeedCatalog.MAX_SPEED }
    return points.takeIf { rising && inRange }
}

/**
 * [points], a curve over a picture [oldSpanUs] long, laid over the part of that picture from [startOffsetUs] for [newSpanUs] (the part may reach past either
 * end of the old picture: a trim dragged outward). A cut or a split keeps every kept place at its speed: the new end takes the curve's value there, and because
 * a cosine ease cut in the middle is a different shape, up to [CUT_SAMPLES] points sampled from the old curve go between that end and the next old point (as
 * many as the 8-point limit leaves room for; when both ends are cut inside one interval it is sampled once). An extension keeps the old end point where it is
 * and adds a new end point with the same speed, so the footage that was there keeps its speed; at 8 points it moves the old end point out instead.
 */
internal fun rebasedCurve(points: List<SpeedPoint>, oldSpanUs: Long, startOffsetUs: Long, newSpanUs: Long): List<SpeedPoint> {
    if (oldSpanUs <= 0 || newSpanUs <= 0) return points
    val from = startOffsetUs.toDouble() / oldSpanUs
    val until = (startOffsetUs + newSpanUs).toDouble() / oldSpanUs
    val newFraction = { oldFraction: Double -> ((oldFraction - from) / (until - from)).toFloat() }
    val placed = points.map { SpeedPoint(newFraction(it.positionFraction.toDouble()), it.speed) }
    val extendsStart = from < 0
    val extendsEnd = until > 1
    val addsStart = extendsStart && points.size + 1 <= SpeedCatalog.MAX_CURVE_POINTS
    val addsEnd = extendsEnd && points.size + (if (addsStart) 2 else 1) <= SpeedCatalog.MAX_CURVE_POINTS
    val first = if (extendsStart) SpeedPoint(0f, placed.first().speed) else SpeedPoint(0f, curveSpeedAt(points, from.toFloat()))
    val last = if (extendsEnd) SpeedPoint(1f, placed.last().speed) else SpeedPoint(1f, curveSpeedAt(points, until.toFloat()))
    // An old end point that was moved to become a new end is not an inside point any more.
    val inside = placed.filterIndexed { index, point ->
        point.positionFraction > EDGE_GAP && point.positionFraction < 1f - EDGE_GAP &&
            !(extendsStart && !addsStart && index == 0) && !(extendsEnd && !addsEnd && index == placed.lastIndex)
    }
    val cutAtStart = !extendsStart && from > EDGE_GAP
    val cutAtEnd = !extendsEnd && until < 1 - EDGE_GAP
    val room = (SpeedCatalog.MAX_CURVE_POINTS - 2 - inside.size).coerceAtLeast(0)
    // The old curve between two places, sampled, so a cut's first interval keeps its shape.
    fun sample(fromOld: Double, toOld: Double, count: Int) =
        (1..count).map { step -> (fromOld + (toOld - fromOld) * step / (count + 1)).let { SpeedPoint(newFraction(it), curveSpeedAt(points, it.toFloat())) } }
    if (cutAtStart && cutAtEnd && inside.isEmpty()) return listOf(first) + sample(from, until, minOf(2 * CUT_SAMPLES, room)) + listOf(last)
    val perCut = minOf(CUT_SAMPLES, room / maxOf(1, listOf(cutAtStart, cutAtEnd).count { it }))
    val nextAfterStart = points.firstOrNull { it.positionFraction > from + EDGE_GAP }?.positionFraction?.toDouble()?.coerceAtMost(until) ?: until
    val lastBeforeEnd = points.lastOrNull { it.positionFraction < until - EDGE_GAP }?.positionFraction?.toDouble()?.coerceAtLeast(from) ?: from
    val startSamples = if (cutAtStart) sample(from, nextAfterStart, perCut) else emptyList()
    val endSamples = if (cutAtEnd) sample(until, lastBeforeEnd, perCut).reversed() else emptyList()
    return listOf(first) + startSamples + inside + endSamples + listOf(last)
}

private const val EDGE_GAP = 0.001f
private const val CUT_SAMPLES = 3
