package app.memix.core.model.project

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The photo scene's arithmetic, in canvas pixels, used by the editor, the render hook and (P2-08) export, so preview
 * and export can't disagree (spec docs/ux/specs/P2-01-photo-editor-shell.md → Layers on the canvas).
 */

/** A width and height in canvas pixels, fractions allowed (a scaled picture isn't whole pixels). */
data class BoxSize(val width: Float, val height: Float)

/** Whether [a] and [b] have the same width-to-height ratio within [CanvasCatalog.SAME_SHAPE_TOLERANCE]. */
fun hasSameShape(a: PixelSize, b: PixelSize): Boolean {
    if (a.widthPx <= 0 || a.heightPx <= 0 || b.widthPx <= 0 || b.heightPx <= 0) return false
    val aspectA = a.widthPx.toFloat() / a.heightPx
    val aspectB = b.widthPx.toFloat() / b.heightPx
    return abs(aspectA / aspectB - 1f) <= CanvasCatalog.SAME_SHAPE_TOLERANCE
}

/**
 * An image layer's size at scale 1 on [canvas]: the picture scaled uniformly to the largest size that lies wholly inside
 * the canvas, or, when its shape is within [CanvasCatalog.SAME_SHAPE_TOLERANCE] of the canvas's, to cover it (a 1% crop,
 * never a 1% bar). Recomputed from the canvas's current shape every time, so it follows a ratio change.
 */
fun fittedImageSize(image: PixelSize, canvas: PixelSize): BoxSize {
    if (image.widthPx <= 0 || image.heightPx <= 0) return BoxSize(0f, 0f)
    val acrossScale = canvas.widthPx.toFloat() / image.widthPx
    val downScale = canvas.heightPx.toFloat() / image.heightPx
    val scale = if (hasSameShape(image, canvas)) max(acrossScale, downScale) else min(acrossScale, downScale)
    return BoxSize(image.widthPx * scale, image.heightPx * scale)
}

/**
 * A background picture's size on [canvas] (spec P2-12 → How it draws): scaled uniformly to cover the whole canvas, the
 * overflow cropped about the center. [image] is the size as shown (EXIF turn applied). The video engine and the photo
 * scene both use this, so a picture lies the same in the preview and the export.
 */
fun coveredImageSize(image: PixelSize, canvas: PixelSize): BoxSize {
    if (image.widthPx <= 0 || image.heightPx <= 0) return BoxSize(0f, 0f)
    val scale = max(canvas.widthPx.toFloat() / image.widthPx, canvas.heightPx.toFloat() / image.heightPx)
    return BoxSize(image.widthPx * scale, image.heightPx * scale)
}

/** The bounds of a [box] turned clockwise by [rotationDegrees] around its center: its width and height on the canvas. */
fun turnedBounds(box: BoxSize, rotationDegrees: Float): BoxSize {
    val radians = rotationDegrees * PI.toFloat() / HALF_TURN_DEGREES
    val cosine = abs(cos(radians))
    val sine = abs(sin(radians))
    return BoxSize(box.width * cosine + box.height * sine, box.width * sine + box.height * cosine)
}

private const val HALF_TURN_DEGREES = 180f
