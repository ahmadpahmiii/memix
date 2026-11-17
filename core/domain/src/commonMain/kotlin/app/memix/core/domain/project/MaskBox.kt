package app.memix.core.domain.project

import app.memix.core.model.project.FrameFit
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.fittedImageSize
import app.memix.core.model.project.hasSameShape
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where a clip's own box lies on the canvas (spec P4-03b → Mask: how it draws, "Space"): the picture after Fit or Fill and before
 * framing, in canvas pixels, turned by [rotationDegrees] (clockwise) about its center. The stage draws the mask frame in it, and the
 * engine's mask effect reads the same rule, so the frame and the picture can't disagree.
 */
data class MaskStageBox(val centerX: Float, val centerY: Float, val width: Float, val height: Float, val rotationDegrees: Float) {
    /** The canvas-pixel point of box point ([fx], [fy]), each 0..1 from the box's top-left. */
    fun canvasPoint(fx: Float, fy: Float): Pair<Float, Float> {
        val localX = (fx - HALF) * width
        val localY = (fy - HALF) * height
        val radians = rotationDegrees * DEGREES_TO_RADIANS
        return (centerX + localX * cos(radians) - localY * sin(radians)) to (centerY + localX * sin(radians) + localY * cos(radians))
    }

    /** The box point (fractions of the box, from its top-left) under canvas-pixel point ([x], [y]). Outside 0..1 is outside the box. */
    fun boxPoint(x: Float, y: Float): Pair<Float, Float> {
        val dx = x - centerX
        val dy = y - centerY
        val radians = rotationDegrees * DEGREES_TO_RADIANS
        val localX = dx * cos(radians) + dy * sin(radians)
        val localY = -dx * sin(radians) + dy * cos(radians)
        return (if (width > 0f) localX / width + HALF else HALF) to (if (height > 0f) localY / height + HALF else HALF)
    }

    private companion object {
        const val HALF = 0.5f
        const val DEGREES_TO_RADIANS = (kotlin.math.PI / 180.0).toFloat()
    }
}

/**
 * The box of maskable clip [itemId] at project time [atUs] on the canvas: an overlay's picture at its look then (keyframes included),
 * a main clip's fitted picture (Fit) or the whole canvas (Fill, or a picture of the canvas's shape). Null when [itemId] isn't a clip a mask applies to.
 */
fun Project.maskStageBoxOf(itemId: String, atUs: Long): MaskStageBox? {
    val clip = keyableClip(itemId) ?: return null
    val canvasSize = PixelSize(canvas.widthPx, canvas.heightPx)
    if (findItem(itemId)?.track?.kind == TrackKind.OVERLAY) {
        val look = lookOf(itemId, atUs)?.transform ?: clip.transform
        val box = overlayBoxAtScaleOne(clip, canvasSize)
        return MaskStageBox(look.centerX * canvasSize.widthPx, look.centerY * canvasSize.heightPx, box.width * look.scale, box.height * look.scale, look.rotationDegrees)
    }
    val fitted = mainClipBox(clip, canvasSize)
    return MaskStageBox(canvasSize.widthPx / 2f, canvasSize.heightPx / 2f, fitted.first, fitted.second, 0f)
}

/** The main clip's box in canvas pixels; the engine's mask uses the same rule on the frame ([fittedImageSize] for Fit, the frame for Fill). */
fun mainClipBox(clip: MediaClip, canvas: PixelSize): Pair<Float, Float> {
    val picture = clip.source.pixelSize
    val wholeCanvas = canvas.widthPx.toFloat() to canvas.heightPx.toFloat()
    if (clip.fit == FrameFit.FILL || picture == null || picture.widthPx <= 0 || picture.heightPx <= 0 || hasSameShape(picture, canvas)) return wholeCanvas
    val fitted = fittedImageSize(picture, canvas)
    return fitted.width to fitted.height
}

