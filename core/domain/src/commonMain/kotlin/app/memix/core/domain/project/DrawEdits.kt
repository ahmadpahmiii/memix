package app.memix.core.domain.project

import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.DrawCatalog
import app.memix.core.model.project.DrawingLayer
import app.memix.core.model.project.Project
import app.memix.core.model.project.StrokeMode
import app.memix.core.model.project.StrokePoints
import app.memix.core.model.project.Transform
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Drawing (spec docs/ux/specs/P2-05-draw-and-censor.md → Draw). A Draw session makes one drawing layer of strokes; drawing again
 * with that layer selected adds to it. One stroke is one edit (one undo step); the layer's other edits are LayerEdits'.
 */

/** What adding a stroke did: the new project, or why nothing changed (the editor explains each one). */
sealed interface StrokeResult {
    /** [layerId] is the layer the stroke went to (new or not); [truncated] when the cap cut the stroke short. */
    data class Added(val project: Project, val layerId: String, val truncated: Boolean) : StrokeResult

    /** The drawing layer is locked. */
    data object Locked : StrokeResult

    /** An eraser stroke with nothing drawn yet. */
    data object NothingToErase : StrokeResult

    /** The layer already holds [DrawCatalog.MAX_POINTS] points. */
    data object Full : StrokeResult
}

/**
 * Adds a stroke to drawing layer [targetId], or, when [targetId] is null or not a drawing, to a new layer [newLayerId] at the front
 * of the stack. [xs] and [ys] are the finger's [count] points in S (the canvas's shorter side) from the canvas center. They are
 * simplified in canvas terms, moved into the layer's own axes (it may be moved, scaled and turned), quantized and capped; the
 * layer is then re-centered on its strokes' box so it stays a plain centered layer for the gestures.
 */
fun Project.addStroke(
    targetId: String?,
    newLayerId: String,
    mode: StrokeMode,
    color: ArgbColor?,
    widthS: Float,
    xs: FloatArray,
    ys: FloatArray,
    count: Int,
): StrokeResult {
    val existing = targetId?.let { layer(it) as? DrawingLayer }
    if (existing?.locked == true) return StrokeResult.Locked
    if (mode == StrokeMode.ERASE && !DrawCatalog.hasPaint(existing)) return StrokeResult.NothingToErase
    val room = DrawCatalog.MAX_POINTS - (existing?.pointCount ?: 0)
    if (room <= 0) return StrokeResult.Full
    val base = existing ?: DrawingLayer(newLayerId, emptyList())
    val simplified = DrawCatalog.simplify(xs, ys, count)
    val local = simplified.toLayerAxes(base.transform, canvas.widthPx, canvas.heightPx)
    val stroke = DrawCatalog.strokeOf(mode, color, widthS / base.transform.scale, local, room)
    val truncated = simplified.xs.size > room
    val grown = DrawCatalog.recentered(base.copy(strokes = base.strokes + stroke), canvas.widthPx, canvas.heightPx)
    val layers = if (existing != null) layers().map { if (it.id == existing.id) grown else it } else layers() + grown
    return StrokeResult.Added(withLayers(layers), grown.id, truncated)
}

// Canvas-center-relative S points in the axes of a layer placed by [transform]: undo its center, turn and scale.
private fun StrokePoints.toLayerAxes(transform: Transform, canvasWidthPx: Int, canvasHeightPx: Int): StrokePoints {
    val shortSide = min(canvasWidthPx, canvasHeightPx).toFloat()
    val radians = -transform.rotationDegrees * (kotlin.math.PI.toFloat() / 180f)
    val cosine = cos(radians)
    val sine = sin(radians)
    // The layer's center from the canvas center, in S.
    val centerX = (transform.centerX - 0.5f) * canvasWidthPx / shortSide
    val centerY = (transform.centerY - 0.5f) * canvasHeightPx / shortSide
    val localX = FloatArray(xs.size)
    val localY = FloatArray(xs.size)
    for (i in xs.indices) {
        val dx = xs[i] - centerX
        val dy = ys[i] - centerY
        localX[i] = (dx * cosine - dy * sine) / transform.scale
        localY[i] = (dx * sine + dy * cosine) / transform.scale
    }
    return StrokePoints(localX, localY)
}
