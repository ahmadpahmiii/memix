package app.memix.core.model.project

import kotlinx.serialization.Serializable

/**
 * A background removed from an image layer (spec docs/ux/specs/P2-07-background-removal.md → Model). Non-destructive: the
 * picture file is never changed. The final alpha is computed from [maskPath] (the segmenter's mask, P2-06, in the picture's own
 * upright coordinates), then the [strokes] in saved order, then [softness], so crop, turn, move and the color tools keep working.
 *
 * Refine strokes reuse [Stroke]: mode [StrokeMode.ERASE] hides more of the photo, [StrokeMode.DRAW] restores it (no color).
 * Their width is a share of the picture's longer side and their points are thousandths of that side from the picture's center.
 */
@Serializable
data class CutOut(
    /**
     * The mask PNG in the project's media folder, relative to the files directory; listed in the project's media copies. Null when the
     * segmenter found nothing and the user erased by hand: everything counts as kept (an all-white mask) and only the strokes cut.
     */
    val maskPath: String?,
    /** The mask's own size in pixels (long side at most 1024); the picture's proportions. Null with no mask. */
    val maskSize: PixelSize?,
    val strokes: List<Stroke> = emptyList(),
    /** The feather of the cut edge, 0 to 1. */
    val softness: Float = CutOutCatalog.DEFAULT_SOFTNESS,
) {
    /** Points over all strokes, against [CutOutCatalog.MAX_POINTS]. */
    val pointCount: Int get() = strokes.sumOf { it.pointCount }
}

object CutOutCatalog {
    const val DEFAULT_SOFTNESS = 0.3f

    /** Brush sizes as shares of the picture's longer side: Thin, Fine, Medium, Thick, Heavy. */
    val BRUSH_SIZES = floatArrayOf(0.015f, 0.03f, 0.06f, 0.10f, 0.16f)
    const val DEFAULT_BRUSH_INDEX = 2

    /** The cap on refine points over a whole cut-out, as for a drawing layer (P2-05). */
    const val MAX_POINTS = DrawCatalog.MAX_POINTS

    /** The longest side, in pixels, a saved sticker is written at (P2-09 converts it to WhatsApp's size). */
    const val STICKER_LONG_SIDE_PX = 1024
}

/** The same layer with [cutOut] (null removes it). */
fun ImageLayer.withCutOut(cutOut: CutOut?): ImageLayer = copy(cutOut = cutOut)
