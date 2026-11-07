package app.memix.debug

import app.memix.core.domain.project.StrokeResult
import app.memix.core.domain.project.addStroke
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.CensorLayer
import app.memix.core.model.project.CensorMode
import app.memix.core.model.project.CensorShape
import app.memix.core.model.project.TextLayer
import app.memix.core.model.project.Transform
import app.memix.core.model.project.DrawCatalog
import app.memix.core.model.project.Project
import app.memix.core.model.project.StrokeMode
import kotlin.math.PI
import kotlin.math.sin

/**
 * The strokes of the `photo-draw-censor` scene (P2-05), made through the real [addStroke] like a finger's: five sizes in five colors,
 * a thick stroke with an eraser stroke across it, a dot, and a second drawing layer at 40% whose strokes overlap (the group shows
 * no darkening). Points are in S from the canvas center, 2 mm apart so they look like touch samples.
 */
internal object DrawCheckScene {
    private const val SAMPLE_STEP = 0.002f

    /** A caption's font is 8% of S at scale 1: 0.55 makes digits with a cap height of about 0.03 S. */
    const val DIGITS_SCALE = 0.55f
    private val colors = listOf(4, 2, 8, 9, 5)

    /** [project] with the sizes layer (id [SIZES_ID]), the erased stroke and dot on it, and the faded layer (id [FADED_ID]). */
    fun withDrawings(project: Project): Project {
        var result = project
        DrawCatalog.BRUSH_SIZES.forEachIndexed { index, size ->
            val y = -0.34f + index * 0.07f
            result = result.stroke(SIZES_ID, StrokeMode.DRAW, colors[index], size, wave(-0.5f, y, 0.02f, 9f))
        }
        // A thick diagonal, then an eraser across it: the erased gap shows the photo, not white.
        result = result.stroke(SIZES_ID, StrokeMode.DRAW, 4, DrawCatalog.BRUSH_SIZES[4], line(-0.45f, 0.3f, 0.0f, 0.45f))
        result = result.stroke(SIZES_ID, StrokeMode.ERASE, null, DrawCatalog.BRUSH_SIZES[3], line(-0.3f, 0.45f, -0.3f, 0.25f))
        result = result.stroke(SIZES_ID, StrokeMode.DRAW, 1, DrawCatalog.BRUSH_SIZES[4], listOf(0.1f to 0.38f))
        result = result.stroke(FADED_ID, StrokeMode.DRAW, 6, DrawCatalog.BRUSH_SIZES[4], line(0.25f, 0.2f, 0.55f, 0.45f))
        result = result.stroke(FADED_ID, StrokeMode.DRAW, 6, DrawCatalog.BRUSH_SIZES[4], line(0.25f, 0.45f, 0.55f, 0.2f))
        return result.fade(FADED_ID, 0.4f)
    }

    /**
     * [project] with the censors of the P2-05 check on top: a line of ten digits under each of a Pixelate rectangle (strength 0), a
     * Blur ellipse (strength 50%) and a Black bar, and a sharp caption over them (a censor hides only what is under it). The digits'
     * cap height is about 0.03 S, the size of the legibility test.
     */
    fun withCensors(project: Project): Project {
        val digits = CaptionStyle("anton", ArgbColor(0xFFFFFFFF), ArgbColor(0xFF000000))
        fun line(id: String, x: Float) = TextLayer(id, "0123456789", digits, Transform(x, 0.27f, DIGITS_SCALE))
        fun censor(id: String, mode: CensorMode, shape: CensorShape, strength: Float, x: Float) =
            CensorLayer(id, mode, shape, strength, widthS = 0.32f, heightS = 0.16f, transform = Transform(x, 0.27f))
        val layers = listOf(
            line("c-digits-1", 0.2f), line("c-digits-2", 0.5f), line("c-digits-3", 0.8f),
            censor("c-pixelate", CensorMode.PIXELATE, CensorShape.RECT, 0f, 0.2f),
            censor("c-blur", CensorMode.BLUR, CensorShape.ELLIPSE, 0.5f, 0.5f),
            censor("c-bar", CensorMode.BAR, CensorShape.RECT, 0f, 0.8f),
            TextLayer("c-sharp", "SHARP ABOVE", digits, Transform(0.5f, 0.2f, 0.8f)),
        )
        return project.copy(photo = project.photo?.copy(layers = project.photo!!.layers + layers))
    }

    /** Draws a long scribble of [pointCount] raw touch points on one new layer, for the draft-size number (a 3-minute scribble). */
    fun scribble(project: Project, strokes: Int, pointsPerStroke: Int): Project {
        var result = project
        repeat(strokes) { n ->
            val phase = n * 0.37f
            val points = (0 until pointsPerStroke).map { i ->
                val t = i.toFloat() / pointsPerStroke
                (-0.5f + t) to (0.3f * sin(2f * PI.toFloat() * (6f * t + phase)) + 0.02f * sin(97f * t))
            }
            result = result.stroke(SCRIBBLE_ID, StrokeMode.DRAW, 4, DrawCatalog.BRUSH_SIZES[2], points)
        }
        return result
    }

    private fun Project.stroke(layerId: String, mode: StrokeMode, colorIndex: Int?, widthS: Float, points: List<Pair<Float, Float>>): Project {
        val result = addStroke(
            layerId, layerId, mode, colorIndex?.let { CaptionCatalog.palette[it] }, widthS,
            FloatArray(points.size) { points[it].first }, FloatArray(points.size) { points[it].second }, points.size,
        )
        return (result as? StrokeResult.Added)?.project ?: this
    }

    private fun Project.fade(layerId: String, opacity: Float): Project =
        copy(photo = photo?.copy(layers = photo!!.layers.map { if (it.id == layerId) it.withState(opacity = opacity) else it }))

    private fun wave(fromX: Float, centerY: Float, amplitude: Float, cycles: Float): List<Pair<Float, Float>> {
        val width = -2f * fromX
        val count = (width / SAMPLE_STEP).toInt()
        return (0..count).map { i ->
            val t = i.toFloat() / count
            (fromX + t * width) to (centerY + amplitude * sin(2f * PI.toFloat() * cycles * t))
        }
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float): List<Pair<Float, Float>> {
        val count = (kotlin.math.hypot(x1 - x0, y1 - y0) / SAMPLE_STEP).toInt().coerceAtLeast(2)
        return (0..count).map { i -> (x0 + (x1 - x0) * i / count) to (y0 + (y1 - y0) * i / count) }
    }

    const val SIZES_ID = "d-sizes"
    const val FADED_ID = "d-faded"
    const val SCRIBBLE_ID = "d-scribble"
}
