package app.memix.core.domain.project

import app.memix.core.model.project.ClipMask
import app.memix.core.model.project.MaskCatalog
import app.memix.core.model.project.MaskShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The mask's arithmetic on the CPU (P4-03b; spec → Mask: how it draws): the exact mirror of the Android fragment shader
 * (`MaskEffect`), so the reference renders and the stage's numbers come from what the phone draws with. Points are in pixels of the
 * clip's own box, from its top-left, y down; the box is [boxWidth] by [boxHeight] pixels.
 *
 * The result is a **straight** alpha to multiply into the clip's own alpha; the color is never touched.
 */
object MaskMath {
    /** The alpha (0 outside, 1 inside, or the other way round when inverted) of [mask] at pixel ([x], [y]) of a [boxWidth] x [boxHeight] box. */
    fun alphaAt(mask: ClipMask, x: Float, y: Float, boxWidth: Float, boxHeight: Float): Float {
        val edge = edgeWidthPx(mask, boxWidth, boxHeight)
        val inside = 1f - smoothstep(-edge / 2f, edge / 2f, signedDistance(mask, x, y, boxWidth, boxHeight))
        return if (mask.invert) 1f - inside else inside
    }

    /** The width of the fading edge in pixels: the softness's share of the box's shorter side, never less than [MaskCatalog.MIN_EDGE_PX]. */
    fun edgeWidthPx(mask: ClipMask, boxWidth: Float, boxHeight: Float): Float =
        max(mask.softness * MaskCatalog.FEATHER_SHARE * min(boxWidth, boxHeight), MaskCatalog.MIN_EDGE_PX)

    /** Distance in pixels from ([x], [y]) to the mask's edge: negative inside the shape, positive outside (Linear: the side the arrow points to is inside). */
    fun signedDistance(mask: ClipMask, x: Float, y: Float, boxWidth: Float, boxHeight: Float): Float {
        val dx = x - mask.centerX * boxWidth
        val dy = y - mask.centerY * boxHeight
        val radians = mask.rotationDegrees * DEGREES_TO_RADIANS
        val cosine = cos(radians)
        val sine = sin(radians)
        val shorter = min(boxWidth, boxHeight)
        return when (mask.shape) {
            MaskShape.RECTANGLE -> rectangleDistance(mask, dx * cosine + dy * sine, -dx * sine + dy * cosine, boxWidth, boxHeight)
            MaskShape.CIRCLE -> {
                val radiusX = mask.width * shorter / 2f
                val radiusY = mask.height * shorter / 2f
                val lx = (dx * cosine + dy * sine) / radiusX
                val ly = (-dx * sine + dy * cosine) / radiusY
                // The ellipse's distance is approximated by its unit-circle distance scaled by the smaller radius: exact for a circle.
                (sqrt(lx * lx + ly * ly) - 1f) * min(radiusX, radiusY)
            }
            // Along the line's normal (the arrow's direction at the turn): the arrow's side is the inside.
            MaskShape.LINEAR -> -(sine * dx - cosine * dy)
            MaskShape.MIRROR -> abs(sine * dx - cosine * dy) - mask.width * shorter / 2f
        }
    }

    private fun rectangleDistance(mask: ClipMask, lx: Float, ly: Float, boxWidth: Float, boxHeight: Float): Float {
        val halfWidth = mask.width * boxWidth / 2f
        val halfHeight = mask.height * boxHeight / 2f
        val radius = mask.cornerRoundness * MaskCatalog.CORNER_RADIUS_SHARE * min(halfWidth, halfHeight)
        val qx = abs(lx) - (halfWidth - radius)
        val qy = abs(ly) - (halfHeight - radius)
        val outside = sqrt(max(qx, 0f) * max(qx, 0f) + max(qy, 0f) * max(qy, 0f))
        return outside + min(max(qx, qy), 0f) - radius
    }

    /** GLSL smoothstep. */
    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private const val DEGREES_TO_RADIANS = (kotlin.math.PI / 180.0).toFloat()
}
