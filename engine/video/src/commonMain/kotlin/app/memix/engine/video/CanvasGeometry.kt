package app.memix.engine.video

import app.memix.core.model.project.PixelSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The canvas rules' arithmetic (spec docs/ux/specs/P1-11-canvas.md → Rendering rules), shared by the platform engines
 * so they place clips and backgrounds alike.
 */

/**
 * The size frames are drawn at for a canvas [canvas] whose shorter side is drawn [shortSidePx] long: the canvas's shape,
 * both sides even (encoders need that). The canvas itself when [shortSidePx] is null or not smaller than its shorter side.
 */
internal fun frameSizeOf(canvas: PixelSize, shortSidePx: Int?): PixelSize {
    val canvasShortSide = min(canvas.widthPx, canvas.heightPx)
    if (shortSidePx == null || shortSidePx >= canvasShortSide || shortSidePx <= 0) return canvas
    val scale = shortSidePx.toFloat() / canvasShortSide
    return PixelSize(evenAtLeastTwo(canvas.widthPx * scale), evenAtLeastTwo(canvas.heightPx * scale))
}

private fun evenAtLeastTwo(length: Float): Int = max(2, (length / 2).roundToInt() * 2)

/** How a picture [picture] sits in a frame [frame]: its Fit and Fill scales, and its size when fitted. */
internal class FitGeometry(picture: PixelSize, private val frame: PixelSize) {
    /** The scale that shows the whole picture inside the frame. */
    val fitScale: Float = min(frame.widthPx.toFloat() / picture.widthPx, frame.heightPx.toFloat() / picture.heightPx)

    /** The scale that covers the whole frame. */
    val fillScale: Float = max(frame.widthPx.toFloat() / picture.widthPx, frame.heightPx.toFloat() / picture.heightPx)

    val fittedWidth: Float = picture.widthPx * fitScale
    val fittedHeight: Float = picture.heightPx * fitScale

    /** True when the fitted picture leaves bars at the left and right, false when above and below (or none). */
    val barsAtSides: Boolean get() = fittedWidth < frame.widthPx

    /** One bar's thickness, frame pixels: half of what the fitted picture leaves free. */
    val barThickness: Float get() = if (barsAtSides) (frame.widthPx - fittedWidth) / 2 else (frame.heightPx - fittedHeight) / 2
}
