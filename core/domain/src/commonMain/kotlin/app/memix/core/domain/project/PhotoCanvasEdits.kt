package app.memix.core.domain.project

import app.memix.core.model.project.BoxSize
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.CanvasCatalog
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.Layer
import app.memix.core.model.project.PhotoCatalog
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.RatioTerms
import app.memix.core.model.project.fittedImageSize
import app.memix.core.model.project.turnedBounds
import kotlin.math.abs

/*
 * The photo canvas (spec docs/ux/specs/P2-01-photo-editor-shell.md → Start, Ratio change, Canvas panel): the shape a picked
 * photo starts with, what a ratio change does to the layers, and when the background can show. The fixed numbers are in
 * PhotoCatalog and CanvasCatalog.
 */

/**
 * The canvas of a photo meme started from a photo [photo] pixels big as shown (EXIF turn applied): the photo's own shape, so
 * the picture stays whole. A preset ratio when the shape is within [PhotoCatalog.PRESET_TOLERANCE] of one; else the custom
 * ratio with the fewest terms within [PhotoCatalog.CUSTOM_TOLERANCE]; a photo more extreme than a custom canvas may be gets
 * the widest allowed shape and sits whole inside it. The size follows CanvasCatalog's size table. White background.
 */
fun photoCanvasFor(photo: PixelSize): Canvas {
    val aspect = photo.widthPx.toFloat() / photo.heightPx
    val preset = CanvasCatalog.presets.firstOrNull { (_, size) -> isWithin(aspect, size.widthPx.toFloat() / size.heightPx, PhotoCatalog.PRESET_TOLERANCE) }
    if (preset != null) return canvasOf(preset.first, preset.second)
    val terms = customTermsNearest(aspect) ?: if (aspect > 1f) PhotoCatalog.WIDEST_CANVAS else PhotoCatalog.TALLEST_CANVAS
    return canvasOf(CanvasRatio.CUSTOM, CanvasCatalog.customSize(terms))
}

/**
 * The canvas in preset [ratio], or the custom ratio [customTerms] when [ratio] is [CanvasRatio.CUSTOM], in one step with
 * the layers: an image that was fully inside the old frame is kept fully inside the new one, moved straight in just far
 * enough to touch the edge (never a gap, [PhotoCatalog.IMAGE_MARGIN_OF_SHORT_SIDE]), shrinking first if it's too big;
 * every layer keeps its center, scale and turn otherwise, and an image's size at scale 1 follows the new shape. One the user
 * hung off the edge keeps its place. Unchanged when the canvas already has that size and ratio, or the custom terms aren't
 * allowed. Text and sticker layers join this rule with P2-03.
 */
fun Project.setPhotoCanvasRatio(ratio: CanvasRatio, customTerms: RatioTerms? = null): CanvasRatioChange {
    val size = canvasSizeFor(ratio, customTerms) ?: return CanvasRatioChange(this, movedLayers = false)
    val old = PixelSize(canvas.widthPx, canvas.heightPx)
    if (canvas.ratio == ratio && old == size) return CanvasRatioChange(this, movedLayers = false)
    var moved = false
    val kept = layers().map { layer ->
        val keptLayer = layer.keptInside(old, size)
        if (keptLayer != layer) moved = true
        keptLayer
    }
    val resized = copy(canvas = canvas.copy(ratio = ratio, widthPx = size.widthPx, heightPx = size.heightPx))
    return CanvasRatioChange(resized.copy(photo = photo?.copy(layers = kept)), moved)
}

/**
 * Whether the canvas background can show: no visible, fully opaque, unturned image covers the whole frame. Conservative
 * (a turned layer never counts as covering, even when it does), because it only decides whether a hint line explains
 * why a color change shows nothing.
 */
fun Project.photoBackgroundShows(): Boolean {
    val canvasSize = PixelSize(canvas.widthPx, canvas.heightPx)
    return layers().none { layer -> layer is ImageLayer && layer.coversCanvas(canvasSize) }
}

private fun Layer.keptInside(old: PixelSize, new: PixelSize): Layer = when (this) {
    is ImageLayer -> {
        val kept = transform.keptInside(old, new, PhotoCatalog.IMAGE_SCALE_RANGE.start, PhotoCatalog.ratioChangeMarginOf(this)) { canvas, scale ->
            boxOn(canvas, scale)
        }
        if (kept == transform) this else copy(transform = kept)
    }
    // TODO(P2-03): text and stickers join the rule (their boxes come from the caption renderer and the sticker's size).
    else -> this
}

/** The image's box at [scale] on [canvas]: its fitted size turned by its rotation, in canvas pixels. A picture of unknown size has none. */
internal fun ImageLayer.boxOn(canvas: PixelSize, scale: Float): BoxSize {
    val picture = source.pixelSize ?: return BoxSize(0f, 0f)
    val fitted = fittedImageSize(picture, canvas)
    return turnedBounds(BoxSize(fitted.width * scale, fitted.height * scale), transform.rotationDegrees)
}

private fun ImageLayer.coversCanvas(canvasSize: PixelSize): Boolean {
    if (hidden || opacity < 1f || transform.rotationDegrees != 0f) return false
    val box = boxOn(canvasSize, transform.scale)
    val centerX = transform.centerX * canvasSize.widthPx
    val centerY = transform.centerY * canvasSize.heightPx
    return centerX - box.width / 2 <= EDGE_TOLERANCE_PX && centerX + box.width / 2 >= canvasSize.widthPx - EDGE_TOLERANCE_PX &&
        centerY - box.height / 2 <= EDGE_TOLERANCE_PX && centerY + box.height / 2 >= canvasSize.heightPx - EDGE_TOLERANCE_PX
}

private fun canvasOf(ratio: CanvasRatio, size: PixelSize) =
    Canvas(ratio = ratio, widthPx = size.widthPx, heightPx = size.heightPx, background = CanvasBackground.Solid(PhotoCatalog.BACKGROUND))

private fun isWithin(aspect: Float, target: Float, tolerance: Float): Boolean = abs(aspect / target - 1f) <= tolerance

// The allowed custom ratio nearest in simplicity: fewest terms (smallest width + height), the closer one on a tie.
private fun customTermsNearest(aspect: Float): RatioTerms? {
    var best: RatioTerms? = null
    var bestError = Float.MAX_VALUE
    for (width in CanvasCatalog.CUSTOM_TERMS) {
        for (height in CanvasCatalog.CUSTOM_TERMS) {
            val terms = RatioTerms(width, height)
            val error = abs(width.toFloat() / height / aspect - 1f)
            if (error > PhotoCatalog.CUSTOM_TOLERANCE || !CanvasCatalog.allowsCustom(terms)) continue
            val current = best
            val simpler = current == null || width + height < current.width + current.height
            val sameSimplicityCloser = current != null && width + height == current.width + current.height && error < bestError
            if (simpler || sameSimplicityCloser) {
                best = terms
                bestError = error
            }
        }
    }
    return best
}
