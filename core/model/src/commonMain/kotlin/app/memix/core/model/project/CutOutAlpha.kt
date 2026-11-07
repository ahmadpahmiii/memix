package app.memix.core.model.project

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The last steps of a cut-out's alpha (spec docs/ux/specs/P2-07-background-removal.md): the feather of the cut edge and the
 * multiply into a picture. Pure Kotlin on ARGB int arrays, so the editor, the render hook and the export make the same pixels.
 * The mask is upscaled and the refine strokes are drawn before this (in `:core:ui`); the feather comes after both, at the
 * size the picture is drawn at, so 1080 and 2160 renders agree.
 */
object CutOutAlpha {
    /** At softness 1 the feather's box radius is this share of the picture's longer side. */
    const val MAX_FEATHER_SHARE = 0.02f

    private const val BLUR_PASSES = 2
    private const val MAX_LEVEL = 255

    /** The feather's box radius in pixels for a picture [longSidePx] long; 0 means none. */
    fun featherRadius(softness: Float, longSidePx: Int): Int = (softness.coerceIn(0f, 1f) * MAX_FEATHER_SHARE * longSidePx).roundToInt()

    /**
     * Turns [levels] (the red channel of each pixel is the cut-out's opacity, 0 to 255) into [picture] pixels' alpha, feathering
     * the edge by [radius] first. [picture] is opaque ARGB, changed in place; [levels] is used as scratch (a big picture makes
     * these arrays the screen's biggest allocation, so none is made here). [checkActive] runs once per row.
     */
    fun applyTo(picture: IntArray, levels: IntArray, width: Int, height: Int, radius: Int, checkActive: () -> Unit = {}) {
        val alpha = levels
        for (index in 0 until width * height) alpha[index] = (alpha[index] shr 16) and MAX_LEVEL
        if (radius > 0) repeat(BLUR_PASSES) { boxBlur(alpha, width, height, radius, checkActive) }
        for (row in 0 until height) {
            checkActive()
            for (index in row * width until (row + 1) * width) {
                val a = alpha[index]
                picture[index] = (a shl 24) or (picture[index] and 0x00FFFFFF)
            }
        }
    }

    /** Pixels with alpha above this count as part of the cut-out when a sticker is trimmed. */
    private const val TRIM_ALPHA = 8

    /**
     * The smallest box [left, top, right, bottom] (right and bottom exclusive) holding every pixel of [pixels] whose alpha is above a
     * faint threshold, or null when nothing is left: the trim of a saved sticker (spec P2-07 → Save as sticker).
     */
    fun opaqueBounds(pixels: IntArray, width: Int, height: Int): IntArray? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((pixels[y * width + x] ushr 24) <= TRIM_ALPHA) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right < 0) null else intArrayOf(left, top, right + 1, bottom + 1)
    }

    /** A box blur of [values] (one channel, 0 to 255) of [radius], run along rows then columns, edges clamped. */
    fun boxBlur(values: IntArray, width: Int, height: Int, radius: Int, checkActive: () -> Unit = {}) {
        val line = IntArray(max(width, height))
        for (row in 0 until height) {
            checkActive()
            blurLine(values, row * width, 1, width, radius, line)
        }
        for (column in 0 until width) {
            checkActive()
            blurLine(values, column, width, height, radius, line)
        }
    }

    // One line of [count] values starting at [start] with [step] between them, averaged over a window of 2 * radius + 1.
    private fun blurLine(values: IntArray, start: Int, step: Int, count: Int, radius: Int, scratch: IntArray) {
        val window = 2 * radius + 1
        var sum = 0
        for (offset in -radius..radius) sum += values[start + offset.coerceIn(0, count - 1) * step]
        for (i in 0 until count) {
            scratch[i] = (sum + window / 2) / window
            sum += values[start + (i + radius + 1).coerceIn(0, count - 1) * step] - values[start + (i - radius).coerceIn(0, count - 1) * step]
        }
        for (i in 0 until count) values[start + i * step] = scratch[i]
    }

}
