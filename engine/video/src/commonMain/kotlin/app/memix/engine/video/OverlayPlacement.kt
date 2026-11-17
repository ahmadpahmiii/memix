package app.memix.engine.video

import app.memix.core.model.project.ItemLook
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.fittedImageSize
import kotlin.math.roundToInt

/**
 * How one overlay clip is drawn into a frame, in the terms of a compositor that stretches nothing: the platform engines only
 * translate it (Media3's `OverlaySettings` on Android, a Metal quad in P7), and the reference render draws it the same way.
 *
 * The clip's picture is first scaled to [textureSize], which is the picture fitted whole inside the frame (so the overlay
 * keeps its shape and a big video isn't composited at full size when it is drawn small). The compositor then draws that
 * texture at its own pixel size times [scaleX] and [scaleY], centered on the point ([anchorX], [anchorY]) and turned by
 * [rotationDegrees].
 */
internal data class OverlayPlacement(
    /** The size, in frame pixels, to scale the picture to before compositing: at overlay scale 1 it is also the size drawn. */
    val textureSize: PixelSize,
    /** The overlay's center in the frame, −1 (left) to 1 (right). */
    val anchorX: Float,
    /** The overlay's center in the frame, −1 (bottom) to 1 (top): up is positive, as in GL. */
    val anchorY: Float,
    /** Times the texture's own size: the clip's scale, corrected for the rounding of [textureSize] to whole pixels. */
    val scaleX: Float,
    val scaleY: Float,
    /** Counterclockwise, as GL turns; the project's [app.memix.core.model.project.Transform.rotationDegrees] is clockwise. */
    val rotationDegrees: Float,
    /** 0 is invisible, 1 fully drawn. */
    val alpha: Float,
    // The picture's fitted size over the texture's rounded one: what a scale is multiplied by, kept so a moving overlay needn't fit the picture again each frame.
    private val fitX: Float = 1f,
    private val fitY: Float = 1f,
) {
    /** This placement for another [look] of the same overlay in the same frame: only the look's part changes, nothing is measured again. */
    fun withLook(look: ItemLook): OverlayPlacement = copy(
        anchorX = look.centerX * 2f - 1f,
        anchorY = 1f - look.centerY * 2f,
        scaleX = look.scale * fitX,
        scaleY = look.scale * fitY,
        rotationDegrees = -look.rotationDegrees,
        alpha = look.opacity,
    )

    companion object {
        /**
         * The drawing of [clip] in a frame of [frame] pixels (the canvas's shape, at the preview's or the export's resolution), looking
         * as [look]: its keyframed look at some time ([OverlayClip.lookAt]), or its fixed one, which the default asks for. The same
         * function serves every frame of a moving overlay, so the preview and the export draw alike.
         */
        fun of(clip: OverlayClip, frame: PixelSize, look: ItemLook = clip.lookAt(0)): OverlayPlacement {
            // The same size the editor draws the overlay's frame and hit area by (a picture within 1% of the frame's shape covers it).
            val fitted = fittedImageSize(clip.pixelSize, frame)
            val textureWidth = fitted.width.roundToInt().coerceAtLeast(1)
            val textureHeight = fitted.height.roundToInt().coerceAtLeast(1)
            return OverlayPlacement(
                textureSize = PixelSize(textureWidth, textureHeight),
                anchorX = 0f, anchorY = 0f, scaleX = 1f, scaleY = 1f, rotationDegrees = 0f, alpha = 1f,
                fitX = fitted.width / textureWidth,
                fitY = fitted.height / textureHeight,
            ).withLook(look)
        }
    }
}
