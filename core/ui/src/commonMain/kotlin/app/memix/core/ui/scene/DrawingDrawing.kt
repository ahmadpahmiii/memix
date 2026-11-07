package app.memix.core.ui.scene

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Stroke as StrokeStyle
import androidx.compose.ui.graphics.drawscope.withTransform
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.DrawCatalog
import app.memix.core.model.project.DrawingLayer
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Stroke
import app.memix.core.model.project.StrokeMode
import app.memix.core.model.project.Transform
import kotlin.math.max
import kotlin.math.min

/*
 * How drawing layers draw (spec docs/ux/specs/P2-05-draw-and-censor.md → Draw). Called only from drawPhotoScene, so the editor,
 * the render hook and export draw strokes the same way.
 */

/**
 * The stroke a finger is drawing now, in S (the canvas's shorter side) from the canvas center: [drawPhotoScene] draws it into
 * the group of drawing layer [layerId] (or of a new one when no such layer exists yet), so an eraser clears inside the layer and
 * never the picture. Points are added in the pointer handler; the scene reads [version] while drawing, so a point redraws the scene
 * without recomposing anything. Main thread only.
 */
@Stable
class LiveStroke(val layerId: String, val mode: StrokeMode, val color: ArgbColor?, val widthS: Float) {
    private var xs = FloatArray(INITIAL_CAPACITY)
    private var ys = FloatArray(INITIAL_CAPACITY)

    private var size = 0

    /** How many points there are. Reading it in a draw lambda subscribes that lambda to new points. */
    val count: Int get() {
        version
        return size
    }

    /** Goes up with every point, so a draw lambda that reads it is redrawn. */
    var version by mutableIntStateOf(0)
        private set

    /** Adds a point; false when it is within a pixel's worth of the last one and was skipped. */
    fun add(x: Float, y: Float): Boolean {
        if (size > 0 && kotlin.math.abs(x - xs[size - 1]) + kotlin.math.abs(y - ys[size - 1]) < MIN_STEP_S) return false
        if (size == xs.size) {
            xs = xs.copyOf(size * 2)
            ys = ys.copyOf(size * 2)
        }
        xs[size] = x
        ys[size] = y
        size++
        version++
        return true
    }

    /** A copy of the points so far, for the step that records the stroke. */
    fun xsCopy(): FloatArray = xs.copyOf(size)

    fun ysCopy(): FloatArray = ys.copyOf(size)

    internal fun xAt(index: Int) = xs[index]

    internal fun yAt(index: Int) = ys[index]

    private companion object {
        const val INITIAL_CAPACITY = 256
        const val MIN_STEP_S = 0.0008f
    }
}

/**
 * The paths of finished strokes, kept so a frame of a drag or a fade doesn't build a 5,000-point path again (the editor keeps one
 * for as long as it is open). Keyed by the stroke's value and the pixels per S it is built for. Main thread only.
 */
@Stable
class StrokePathCache {
    private val paths = HashMap<PathKey, Path>()

    internal fun pathOf(stroke: Stroke, shortSide: Float): Path {
        val key = PathKey(stroke, shortSide)
        paths[key]?.let { return it }
        // Strokes that left the drawing (undone, deleted) must not pile up: start again when many are kept.
        if (paths.size >= MAX_PATHS) paths.clear()
        return strokePath(stroke, shortSide).also { paths[key] = it }
    }

    private data class PathKey(val stroke: Stroke, val shortSide: Float)

    private companion object {
        const val MAX_PATHS = 400
    }
}

/**
 * [layer] drawn as one group at [opacity]: its strokes in saved order under its transform, then [live] (in canvas terms, not the
 * layer's) when it is going to this layer. A stroke that erases clears inside the group.
 */
internal fun DrawScope.drawDrawingLayer(layer: DrawingLayer, transform: Transform, opacity: Float, canvasSize: PixelSize, live: LiveStroke?, paths: StrokePathCache? = null) {
    val shortSide = min(canvasSize.widthPx, canvasSize.heightPx).toFloat()
    drawStrokeGroup(
        layer.strokes,
        opacity,
        Rect(0f, 0f, canvasSize.widthPx.toFloat(), canvasSize.heightPx.toFloat()),
        shortSide,
        place = {
            translate(transform.centerX * canvasSize.widthPx, transform.centerY * canvasSize.heightPx)
            rotate(transform.rotationDegrees, Offset.Zero)
            scale(transform.scale, transform.scale, Offset.Zero)
        },
        live = live,
        liveOrigin = Offset(canvasSize.widthPx / 2f, canvasSize.heightPx / 2f),
        paths = paths,
    )
}

/** A stroke the front of the stack doesn't have yet: the first one of a new drawing layer. */
internal fun DrawScope.drawNewDrawing(live: LiveStroke, canvasSize: PixelSize) =
    drawDrawingLayer(DrawingLayer(live.layerId, emptyList()), Transform(), 1f, canvasSize, live)

/**
 * A small render of [strokes] fitted inside this scope's square, for the Layers panel's row (spec P2-05 → The drawing layer →
 * Row). Strokes are centered on their box, as the layer keeps them.
 */
fun DrawScope.drawDrawingThumbnail(strokes: List<Stroke>) {
    val bounds = DrawCatalog.boundsOf(DrawingLayer("thumbnail", strokes)) ?: return
    val longest = max(bounds.width, bounds.height)
    if (longest <= 0f) return
    val shortSide = THUMBNAIL_FILL * size.minDimension / longest
    drawStrokeGroup(strokes, 1f, Rect(Offset.Zero, size), shortSide, place = { translate(size.width / 2f, size.height / 2f) }, live = null, liveOrigin = Offset.Zero, paths = null)
}

private const val THUMBNAIL_FILL = 0.8f

// The strokes in one offscreen group, so an eraser clears only them and the group's opacity applies once.
private fun DrawScope.drawStrokeGroup(
    strokes: List<Stroke>,
    opacity: Float,
    bounds: Rect,
    shortSide: Float,
    place: DrawTransform.() -> Unit,
    live: LiveStroke?,
    liveOrigin: Offset,
    paths: StrokePathCache?,
) {
    val canvas = drawContext.canvas
    canvas.saveLayer(bounds, Paint().apply { alpha = opacity })
    withTransform(place) { for (stroke in strokes) drawStroke(stroke, shortSide, paths) }
    if (live != null && live.count > 0) withTransform({ translate(liveOrigin.x, liveOrigin.y) }) { drawLiveStroke(live, shortSide) }
    canvas.restore()
}

private fun DrawScope.drawStroke(stroke: Stroke, shortSide: Float, paths: StrokePathCache?) {
    if (stroke.pointCount == 0) return // not written by the editor, but a hand-edited or damaged draft mustn't crash the scene
    val width = stroke.widthS * shortSide
    val color = stroke.color?.let { Color(it.argb.toInt()) } ?: Color.Black
    val blend = if (stroke.mode == StrokeMode.ERASE) BlendMode.Clear else BlendMode.SrcOver
    if (stroke.pointCount == 1) {
        val unit = shortSide / DrawCatalog.UNITS_PER_S
        drawCircle(color, width / 2f, Offset(stroke.points[0] * unit, stroke.points[1] * unit), blendMode = blend)
        return
    }
    val path = paths?.pathOf(stroke, shortSide) ?: strokePath(stroke, shortSide)
    drawPath(path, color, style = StrokeStyle(width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
}

// Stored points already follow the curve the finger drew (DrawCatalog.simplify), so they are joined by straight segments.
private fun strokePath(stroke: Stroke, shortSide: Float): Path {
    val unit = shortSide / DrawCatalog.UNITS_PER_S
    return Path().apply {
        moveTo(stroke.points[0] * unit, stroke.points[1] * unit)
        for (i in 1 until stroke.pointCount) lineTo(stroke.points[i * 2] * unit, stroke.points[i * 2 + 1] * unit)
    }
}

// The finger's points as a smooth curve: each touch point is a control point between the midpoints of its neighbours.
private fun DrawScope.drawLiveStroke(live: LiveStroke, shortSide: Float) {
    val width = live.widthS * shortSide
    val color = live.color?.let { Color(it.argb.toInt()) } ?: Color.Black
    val blend = if (live.mode == StrokeMode.ERASE) BlendMode.Clear else BlendMode.SrcOver
    if (live.count == 1) {
        drawCircle(color, width / 2f, Offset(live.xAt(0) * shortSide, live.yAt(0) * shortSide), blendMode = blend)
        return
    }
    val path = Path()
    path.moveTo(live.xAt(0) * shortSide, live.yAt(0) * shortSide)
    for (i in 1 until live.count - 1) {
        val midX = (live.xAt(i) + live.xAt(i + 1)) / 2f
        val midY = (live.yAt(i) + live.yAt(i + 1)) / 2f
        path.quadraticTo(live.xAt(i) * shortSide, live.yAt(i) * shortSide, midX * shortSide, midY * shortSide)
    }
    path.lineTo(live.xAt(live.count - 1) * shortSide, live.yAt(live.count - 1) * shortSide)
    drawPath(path, color, style = StrokeStyle(width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
}
