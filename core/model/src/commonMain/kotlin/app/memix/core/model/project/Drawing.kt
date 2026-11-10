package app.memix.core.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Whether a stroke paints its color or clears what the same drawing layer painted before it. */
@Serializable
enum class StrokeMode {
    @SerialName("draw") DRAW,
    @SerialName("erase") ERASE,
}

/**
 * One hand-drawn stroke (spec docs/ux/specs/P2-05-draw-and-censor.md → Draw). [points] is a flat list of x, y pairs in
 * thousandths of S (the canvas's shorter side) from the layer's own center, so a draft stays small (a point is about nine
 * characters of JSON) and a ratio change never stretches a drawing. A stroke of one point is a dot. [widthS] is the brush
 * width in S at scale 1 of the layer; [color] is null for an eraser.
 */
@Serializable
data class Stroke(
    val mode: StrokeMode = StrokeMode.DRAW,
    val color: ArgbColor? = null,
    val widthS: Float,
    val points: List<Int>,
) {
    val pointCount: Int get() = points.size / 2
}

/**
 * Hand-drawn strokes as one layer (spec P2-05 → The drawing layer). Strokes draw in saved order into the layer's own group,
 * so an eraser stroke clears only what this layer drew before it and a faded drawing never shows overlap darkening. The
 * layer's center is kept at the middle of its strokes' box ([DrawCatalog.recentered]), so it moves, scales and turns like any
 * sticker.
 */
@Serializable
@SerialName("drawing_layer")
data class DrawingLayer(
    override val id: String,
    val strokes: List<Stroke>,
    override val transform: Transform = Transform(),
    override val locked: Boolean = false,
    override val hidden: Boolean = false,
    override val opacity: Float = 1f,
) : Layer {
    /** All the points of all strokes, which the cap [DrawCatalog.MAX_POINTS] counts. */
    val pointCount: Int get() = strokes.sumOf { it.pointCount }

    override fun withId(id: String): DrawingLayer = copy(id = id)

    override fun withState(transform: Transform, locked: Boolean, hidden: Boolean, opacity: Float): DrawingLayer =
        copy(transform = transform, locked = locked, hidden = hidden, opacity = opacity)
}
