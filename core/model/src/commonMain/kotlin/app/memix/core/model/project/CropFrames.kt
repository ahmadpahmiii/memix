package app.memix.core.model.project

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The Crop screen's frame rules as pure functions on [CropRect] fractions (spec docs/ux/specs/P2-04-image-tools.md → The crop
 * frame). [turned] is the size of the turned picture's frame in pixels, so a locked aspect (width ÷ height in pixels) can be kept
 * across fractions. The frame stays inside the picture and at least [ImageToolCatalog.MIN_CROP] of its width and of its height.
 */
object CropFrames {
    /** Which part of the frame a finger holds. */
    enum class Handle { MOVE, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /** The largest centered frame whose pixel shape is [aspect] (width ÷ height) inside a picture [turned] pixels big. */
    fun centeredWithAspect(aspect: Float, turned: PixelSize): CropRect {
        if (aspect <= 0f || turned.widthPx <= 0 || turned.heightPx <= 0) return CropRect.Full
        val pictureAspect = turned.widthPx.toFloat() / turned.heightPx
        val widthFraction = if (aspect >= pictureAspect) 1f else aspect / pictureAspect
        val heightFraction = if (aspect >= pictureAspect) pictureAspect / aspect else 1f
        val left = (1f - widthFraction) / 2f
        val top = (1f - heightFraction) / 2f
        return CropRect(left, top, left + widthFraction, top + heightFraction)
    }

    /** The pixel shape (width ÷ height) of [crop] on a picture [turned] pixels big. */
    fun aspectOf(crop: CropRect, turned: PixelSize): Float =
        (crop.width * turned.widthPx) / (crop.height * turned.heightPx).coerceAtLeast(MIN_DIVISOR)

    /** [crop] moved by ([dx], [dy]) fractions, stopped at the picture's edges. */
    fun moved(crop: CropRect, dx: Float, dy: Float): CropRect {
        val x = dx.coerceIn(-crop.left, 1f - crop.right)
        val y = dy.coerceIn(-crop.top, 1f - crop.bottom)
        return CropRect(crop.left + x, crop.top + y, crop.right + x, crop.bottom + y)
    }

    /**
     * [crop] with [handle] dragged to the point ([x], [y]) in fractions of the picture. A free frame follows the finger on the
     * sides the handle owns; with [lockedAspect] set, a corner keeps the shape (an edge does nothing). Stops at the minimum
     * size and the picture's edges; the drag simply stops there.
     */
    fun dragged(crop: CropRect, handle: Handle, x: Float, y: Float, lockedAspect: Float?, turned: PixelSize): CropRect {
        if (handle == Handle.MOVE) return crop
        if (lockedAspect != null) return draggedKeepingAspect(crop, handle, x, y, lockedAspect, turned)
        val minimum = ImageToolCatalog.MIN_CROP
        var left = crop.left
        var right = crop.right
        var top = crop.top
        var bottom = crop.bottom
        if (handle.touchesLeft) left = x.coerceIn(0f, right - minimum)
        if (handle.touchesRight) right = x.coerceIn(left + minimum, 1f)
        if (handle.touchesTop) top = y.coerceIn(0f, bottom - minimum)
        if (handle.touchesBottom) bottom = y.coerceIn(top + minimum, 1f)
        return CropRect(left, top, right, bottom)
    }

    private fun draggedKeepingAspect(crop: CropRect, handle: Handle, x: Float, y: Float, aspect: Float, turned: PixelSize): CropRect {
        val corner = handle.isCorner
        if (!corner) return crop
        // The opposite corner stays; the dragged corner goes toward the finger, along the locked shape.
        val anchorX = if (handle.touchesLeft) crop.right else crop.left
        val anchorY = if (handle.touchesTop) crop.bottom else crop.top
        val towardLeft = handle.touchesLeft
        val towardTop = handle.touchesTop
        val availableWidth = if (towardLeft) anchorX else 1f - anchorX
        val availableHeight = if (towardTop) anchorY else 1f - anchorY
        // Fraction of height per fraction of width at this shape.
        val heightPerWidth = turned.widthPx / (aspect * turned.heightPx)
        val minimum = ImageToolCatalog.MIN_CROP
        val smallestWidth = max(minimum, minimum / heightPerWidth)
        val largestWidth = min(availableWidth, availableHeight / heightPerWidth)
        if (largestWidth < smallestWidth) return crop
        val width = abs(x - anchorX).coerceIn(smallestWidth, largestWidth)
        val height = width * heightPerWidth
        val left = if (towardLeft) anchorX - width else anchorX
        val top = if (towardTop) anchorY - height else anchorY
        return CropRect(left, top, left + width, top + height)
    }

    /**
     * One Edges-panel step: side [handle] (LEFT, RIGHT, TOP or BOTTOM) trimmed by [delta] more of the picture (negative trims
     * less). With [lockedAspect] the other dimension follows about its center to keep the shape. Null when it can't move
     * (the minimum, or the picture's edge).
     */
    fun edgeStepped(crop: CropRect, handle: Handle, delta: Float, lockedAspect: Float?, turned: PixelSize): CropRect? {
        val minimum = ImageToolCatalog.MIN_CROP
        var left = crop.left
        var right = crop.right
        var top = crop.top
        var bottom = crop.bottom
        when (handle) {
            Handle.LEFT -> left += delta
            Handle.RIGHT -> right -= delta
            Handle.TOP -> top += delta
            Handle.BOTTOM -> bottom -= delta
            else -> return null
        }
        if (left < 0f || top < 0f || right > 1f || bottom > 1f || right - left < minimum || bottom - top < minimum) return null
        var stepped = CropRect(left, top, right, bottom)
        if (lockedAspect != null) stepped = keepingAspect(stepped, handle, lockedAspect, turned) ?: return null
        return stepped.takeIf { it != crop }
    }

    // The side not being stepped follows: height follows a width change and the other way round, about its own center.
    private fun keepingAspect(crop: CropRect, handle: Handle, aspect: Float, turned: PixelSize): CropRect? {
        val heightPerWidth = turned.widthPx / (aspect * turned.heightPx)
        val minimum = ImageToolCatalog.MIN_CROP
        val horizontal = handle == Handle.LEFT || handle == Handle.RIGHT
        val centerX = (crop.left + crop.right) / 2f
        val centerY = (crop.top + crop.bottom) / 2f
        val result = if (horizontal) {
            val height = crop.width * heightPerWidth
            CropRect(crop.left, centerY - height / 2f, crop.right, centerY + height / 2f)
        } else {
            val width = crop.height / heightPerWidth
            CropRect(centerX - width / 2f, crop.top, centerX + width / 2f, crop.bottom)
        }
        val inside = result.left >= -EPSILON && result.top >= -EPSILON && result.right <= 1f + EPSILON && result.bottom <= 1f + EPSILON
        return result.takeIf { inside && it.width >= minimum - EPSILON && it.height >= minimum - EPSILON }
    }

    private val Handle.touchesLeft get() = this == Handle.LEFT || this == Handle.TOP_LEFT || this == Handle.BOTTOM_LEFT
    private val Handle.touchesRight get() = this == Handle.RIGHT || this == Handle.TOP_RIGHT || this == Handle.BOTTOM_RIGHT
    private val Handle.touchesTop get() = this == Handle.TOP || this == Handle.TOP_LEFT || this == Handle.TOP_RIGHT
    private val Handle.touchesBottom get() = this == Handle.BOTTOM || this == Handle.BOTTOM_LEFT || this == Handle.BOTTOM_RIGHT
    private val Handle.isCorner get() = (touchesLeft || touchesRight) && (touchesTop || touchesBottom)

    private const val MIN_DIVISOR = 1e-6f
    private const val EPSILON = 1e-4f
}
