package app.memix.core.model.project

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The drawing tool's fixed rules (spec docs/ux/specs/P2-05-draw-and-censor.md; design/tokens.json → photo.draw). They are
 * part of every saved draft's look, so changing one after the closed test needs a schema bump (CLAUDE.md rule 6). S is the
 * canvas's shorter side.
 */
object DrawCatalog {
    /** Thin, Fine, Medium, Thick, Heavy: brush widths as shares of S (4 to 69 px at 1080), so a stroke scales with the canvas and the export. */
    val BRUSH_SIZES: List<Float> = listOf(0.004f, 0.008f, 0.016f, 0.032f, 0.064f)
    const val DEFAULT_SIZE_INDEX = 2

    /** Red, the usual "circle it" color: an index into [CaptionCatalog.palette]. */
    const val DEFAULT_COLOR_INDEX = 4

    /** A layer's scale runs 10% to 500%, as a sticker's. */
    val SCALE_RANGE: ClosedFloatingPointRange<Float> = 0.1f..5f

    /** Points are stored in thousandths of S. */
    const val UNITS_PER_S = 1000

    /** A finished stroke is simplified within this, in S, before it is stored. */
    const val SIMPLIFY_TOLERANCE_S = 0.002f

    /** A stroke that travels less than this, in S, is a dot. */
    const val DOT_TRAVEL_S = 0.004f

    /** One drawing layer holds at most this many points, so autosave (which writes the whole draft) stays small. */
    const val MAX_POINTS = 5000

    private const val CURVE_STEP_S = 0.004f
    private const val MAX_CURVE_STEPS = 32

    /** Brush widths are stored to four decimals, so a stroke redrawn on a scaled layer doesn't write long numbers. */
    private const val WIDTH_DECIMALS = 10_000f

    /**
     * The points of a finished stroke from the ones a finger made ([xs], [ys], [count] of them, in S): the midpoint curve the
     * live stroke was drawn with (so what is stored matches what was seen), simplified within [SIMPLIFY_TOLERANCE_S]; or just
     * the first point when the path travelled less than [DOT_TRAVEL_S] (a dot). Empty for no points.
     */
    fun simplify(xs: FloatArray, ys: FloatArray, count: Int): StrokePoints {
        if (count == 0) return StrokePoints(FloatArray(0), FloatArray(0))
        if (travelOf(xs, ys, count) < DOT_TRAVEL_S) return StrokePoints(floatArrayOf(xs[0]), floatArrayOf(ys[0]))
        val curve = flattenedMidpointCurve(xs, ys, count)
        val total = curve.xs.size
        val kept = simplified(curve.xs, curve.ys, total, SIMPLIFY_TOLERANCE_S)
        return StrokePoints(FloatArray(kept.size) { curve.xs[kept[it]] }, FloatArray(kept.size) { curve.ys[kept[it]] })
    }

    // The quadratic curve through the touch points' midpoints (each touch point is a control point), as a polyline whose
    // segments are about CURVE_STEP_S long: the shape the stage draws live with Path.quadTo.
    private fun flattenedMidpointCurve(xs: FloatArray, ys: FloatArray, count: Int): StrokePoints {
        val outX = ArrayList<Float>()
        val outY = ArrayList<Float>()
        outX.add(xs[0])
        outY.add(ys[0])
        var startX = xs[0]
        var startY = ys[0]
        for (i in 1 until count - 1) {
            val endX = (xs[i] + xs[i + 1]) / 2f
            val endY = (ys[i] + ys[i + 1]) / 2f
            val length = hypot(xs[i] - startX, ys[i] - startY) + hypot(endX - xs[i], endY - ys[i])
            val steps = (length / CURVE_STEP_S).toInt().coerceIn(1, MAX_CURVE_STEPS)
            for (step in 1..steps) {
                val t = step.toFloat() / steps
                val u = 1f - t
                outX.add(u * u * startX + 2f * u * t * xs[i] + t * t * endX)
                outY.add(u * u * startY + 2f * u * t * ys[i] + t * t * endY)
            }
            startX = endX
            startY = endY
        }
        outX.add(xs[count - 1])
        outY.add(ys[count - 1])
        return StrokePoints(outX.toFloatArray(), outY.toFloatArray())
    }

    /** [points] (S from the layer's center) as a stored stroke: quantized to a thousandth of S, a repeated point dropped, at most [maxPoints]. */
    fun strokeOf(mode: StrokeMode, color: ArgbColor?, widthS: Float, points: StrokePoints, maxPoints: Int = MAX_POINTS): Stroke {
        val stored = ArrayList<Int>(points.xs.size * 2)
        for (i in points.xs.indices) {
            if (stored.size / 2 >= maxPoints) break
            val x = quantum(points.xs[i])
            val y = quantum(points.ys[i])
            val n = stored.size
            if (n >= 2 && stored[n - 2] == x && stored[n - 1] == y) continue
            stored.add(x)
            stored.add(y)
        }
        return Stroke(mode, color, (widthS * WIDTH_DECIMALS).roundToInt() / WIDTH_DECIMALS, stored)
    }

    private fun quantum(value: Float): Int = (value * UNITS_PER_S).roundToInt()

    private fun travelOf(xs: FloatArray, ys: FloatArray, count: Int): Float {
        var travel = 0f
        for (i in 1 until count) travel += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        return travel
    }

    // Douglas-Peucker with an explicit stack (a long scribble must not recurse deep): the indexes to keep, in order.
    private fun simplified(xs: FloatArray, ys: FloatArray, count: Int, tolerance: Float): List<Int> {
        val keep = BooleanArray(count)
        keep[0] = true
        keep[count - 1] = true
        val stack = ArrayDeque<IntArray>()
        stack.add(intArrayOf(0, count - 1))
        while (stack.isNotEmpty()) {
            val (from, to) = stack.removeLast()
            var farthest = -1
            var farthestDistance = tolerance
            for (i in from + 1 until to) {
                val distance = distanceToSegment(xs[i], ys[i], xs[from], ys[from], xs[to], ys[to])
                if (distance > farthestDistance) {
                    farthest = i
                    farthestDistance = distance
                }
            }
            if (farthest < 0) continue
            keep[farthest] = true
            stack.add(intArrayOf(from, farthest))
            stack.add(intArrayOf(farthest, to))
        }
        return (0 until count).filter { keep[it] }
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /**
     * The box around [layer]'s painted strokes in S from the layer's center, grown by half of each stroke's width, or null when
     * nothing is painted. Erasers don't count: they only clear.
     */
    fun boundsOf(layer: DrawingLayer): StrokeBounds? {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var any = false
        for (stroke in layer.strokes) {
            if (stroke.mode != StrokeMode.DRAW) continue
            val half = stroke.widthS / 2f
            for (i in 0 until stroke.pointCount) {
                val x = stroke.points[i * 2].toFloat() / UNITS_PER_S
                val y = stroke.points[i * 2 + 1].toFloat() / UNITS_PER_S
                minX = min(minX, x - half)
                maxX = max(maxX, x + half)
                minY = min(minY, y - half)
                maxY = max(maxY, y + half)
                any = true
            }
        }
        return if (any) StrokeBounds(minX, minY, maxX, maxY) else null
    }

    /** True when [layer] has something painted: an eraser needs it. */
    fun hasPaint(layer: DrawingLayer?): Boolean = layer != null && layer.strokes.any { it.mode == StrokeMode.DRAW }

    /**
     * [layer] with its points moved so the layer's center is the middle of its painted box, and [Transform] changed to keep every
     * stroke where it was on the canvas. [canvasWidthPx] and [canvasHeightPx] are the canvas's size.
     */
    fun recentered(layer: DrawingLayer, canvasWidthPx: Int, canvasHeightPx: Int): DrawingLayer {
        val bounds = boundsOf(layer) ?: return layer
        val shiftX = quantum((bounds.minX + bounds.maxX) / 2f)
        val shiftY = quantum((bounds.minY + bounds.maxY) / 2f)
        if (shiftX == 0 && shiftY == 0) return layer
        val shortSide = min(canvasWidthPx, canvasHeightPx).toFloat()
        val transform = layer.transform
        val radians = transform.rotationDegrees * (kotlin.math.PI.toFloat() / 180f)
        val dx = shiftX.toFloat() / UNITS_PER_S * shortSide * transform.scale
        val dy = shiftY.toFloat() / UNITS_PER_S * shortSide * transform.scale
        val moved = transform.copy(
            centerX = transform.centerX + (dx * kotlin.math.cos(radians) - dy * kotlin.math.sin(radians)) / canvasWidthPx,
            centerY = transform.centerY + (dx * kotlin.math.sin(radians) + dy * kotlin.math.cos(radians)) / canvasHeightPx,
        )
        val shifted = layer.strokes.map { stroke ->
            stroke.copy(points = stroke.points.mapIndexed { i, value -> if (i % 2 == 0) value - shiftX else value - shiftY })
        }
        return layer.copy(strokes = shifted, transform = moved)
    }
}

/** A box in S from a drawing layer's center. */
data class StrokeBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

/** A stroke's points as two arrays, in S. */
class StrokePoints(val xs: FloatArray, val ys: FloatArray)
