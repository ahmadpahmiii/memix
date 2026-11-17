package app.memix.core.domain.project

import app.memix.core.model.project.ChromaKey
import app.memix.core.model.project.ChromaKeyCatalog
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The key's arithmetic on the CPU (P4-04; spec → How it draws): the exact mirror of the Android fragment shader
 * (`ChromaKeyEffect`), so the reference renders, the Pick's 9 x 9 average and the "nothing found" share are read from the same
 * numbers the phone draws with. Colors are 0xAARRGGBB ints; chroma (Cb, Cr) is BT.601 full range, each in -0.5..0.5; luma is
 * ignored when comparing, so the shadowed part of a screen keys like the lit part.
 *
 * The result is **straight** alpha (the color is not multiplied by alpha): Media3 scales and composites with straight alpha
 * (`GL_SRC_ALPHA`), and multiplying here would darken edges twice.
 */
object ChromaKeyMath {
    /** Cb of an RGB color, each channel 0..1. */
    fun cb(r: Float, g: Float, b: Float): Float = -0.168736f * r - 0.331264f * g + 0.5f * b

    /** Cr of an RGB color, each channel 0..1. */
    fun cr(r: Float, g: Float, b: Float): Float = 0.5f * r - 0.418688f * g - 0.081312f * b

    /** GLSL smoothstep: 0 at or below [edge0], 1 at or above [edge1], smooth between. */
    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** The key's alpha for a pixel whose chroma is ([pixelCb], [pixelCr]): 0 inside the tolerance, 1 beyond it plus the feather. */
    fun alphaOf(pixelCb: Float, pixelCr: Float, key: ChromaKey): Float {
        val (keyCb, keyCr) = chromaOf(key.colorArgb)
        val dCb = pixelCb - keyCb
        val dCr = pixelCr - keyCr
        val distance = sqrt(dCb * dCb + dCr * dCr)
        val tolerance = ChromaKeyCatalog.toleranceOf(key.strength)
        return smoothstep(tolerance, tolerance + ChromaKeyCatalog.featherOf(key.strength, key.softness), distance)
    }

    /**
     * Pixel [argb] after the key: its alpha multiplied by the key's, its color with the spill taken out (the chroma along the key's
     * direction reduced by [ChromaKeyCatalog.SPILL_REDUCTION] x (1 - alpha), fully where the pixel is nearly removed), luma kept.
     */
    fun keyPixel(argb: Int, key: ChromaKey): Int {
        val r = channel(argb, RED_SHIFT)
        val g = channel(argb, GREEN_SHIFT)
        val b = channel(argb, BLUE_SHIFT)
        val sourceAlpha = channel(argb, ALPHA_SHIFT)
        val pixelCb = cb(r, g, b)
        val pixelCr = cr(r, g, b)
        val keyAlpha = alphaOf(pixelCb, pixelCr, key)
        val (keyCb, keyCr) = chromaOf(key.colorArgb)
        val keyLength = sqrt(keyCb * keyCb + keyCr * keyCr)
        var outCb = pixelCb
        var outCr = pixelCr
        if (keyLength > MIN_KEY_CHROMA) {
            val along = (pixelCb * keyCb + pixelCr * keyCr) / keyLength
            if (along > 0f) {
                val reduction = spillReductionAt(keyAlpha) * along / keyLength
                outCb -= keyCb * reduction
                outCr -= keyCr * reduction
            }
        }
        val luma = 0.299f * r + 0.587f * g + 0.114f * b
        val outR = luma + 1.402f * outCr
        val outG = luma - 0.344136f * outCb - 0.714136f * outCr
        val outB = luma + 1.772f * outCb
        return pack(sourceAlpha * keyAlpha, outR, outG, outB)
    }

    /** The share of the reduction applied at [alpha]: [ChromaKeyCatalog.SPILL_REDUCTION] x (1 - alpha), up to 1 where alpha is near 0. */
    fun spillReductionAt(alpha: Float): Float {
        val guard = min(alpha / ChromaKeyCatalog.BLEED_GUARD_ALPHA, 1f)
        val share = 1f + (ChromaKeyCatalog.SPILL_REDUCTION - 1f) * guard
        return share * (1f - alpha)
    }

    /** Writes the (Cb, Cr) of an 0xAARRGGBB color into [out] (two floats) without allocating. */
    fun chromaInto(argb: Int, out: FloatArray) {
        val r = channel(argb, RED_SHIFT)
        val g = channel(argb, GREEN_SHIFT)
        val b = channel(argb, BLUE_SHIFT)
        out[0] = cb(r, g, b)
        out[1] = cr(r, g, b)
    }

    /** The (Cb, Cr) of an 0xAARRGGBB color. */
    fun chromaOf(argb: Int): Pair<Float, Float> {
        val r = channel(argb, RED_SHIFT)
        val g = channel(argb, GREEN_SHIFT)
        val b = channel(argb, BLUE_SHIFT)
        return cb(r, g, b) to cr(r, g, b)
    }

    /**
     * The average color of the [window] x [window] pixels around ([centerX], [centerY]) in an image [width] x [height] of 0xAARRGGBB
     * [pixels] (the Pick; a camera's noise makes one pixel a poor sample). The window is cut at the image's edge, so the
     * average is always of real pixels. Opaque. Null when the image is empty or the center is outside it.
     */
    fun averageColor(pixels: IntArray, width: Int, height: Int, centerX: Int, centerY: Int, window: Int = ChromaKeyCatalog.SAMPLE_WINDOW_PX): Int? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        if (centerX !in 0 until width || centerY !in 0 until height) return null
        val half = window / 2
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0
        for (y in max(0, centerY - half)..min(height - 1, centerY + half)) {
            for (x in max(0, centerX - half)..min(width - 1, centerX + half)) {
                val argb = pixels[y * width + x]
                sumR += (argb shr RED_SHIFT) and BYTE
                sumG += (argb shr GREEN_SHIFT) and BYTE
                sumB += (argb shr BLUE_SHIFT) and BYTE
                count++
            }
        }
        return OPAQUE or ((sumR / count).toInt() shl RED_SHIFT) or ((sumG / count).toInt() shl GREEN_SHIFT) or (sumB / count).toInt()
    }

    /**
     * The share (0..1) of [pixels] the [key] removes at least half (alpha under 0.5), over a grid of at most [maxSamples] pixels:
     * the "nothing found" test, run on a downsampled frame. Pixels the clip's own alpha already hides count as removed.
     */
    fun removedShare(pixels: IntArray, width: Int, height: Int, key: ChromaKey, maxSamples: Int = 40_000): Float {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return 0f
        val stride = max(1, sqrt((width.toLong() * height / maxSamples.toDouble())).toInt())
        var removed = 0
        var total = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val keyed = keyPixel(pixels[y * width + x], key)
                if (((keyed ushr ALPHA_SHIFT) and BYTE) < HALF_ALPHA) removed++
                total++
                x += stride
            }
            y += stride
        }
        return if (total == 0) 0f else removed.toFloat() / total
    }

    private fun channel(argb: Int, shift: Int): Float = ((argb shr shift) and BYTE) / BYTE_MAX

    private fun pack(alpha: Float, r: Float, g: Float, b: Float): Int =
        (toByte(alpha) shl ALPHA_SHIFT) or (toByte(r) shl RED_SHIFT) or (toByte(g) shl GREEN_SHIFT) or toByte(b)

    private fun toByte(value: Float): Int = (value.coerceIn(0f, 1f) * BYTE_MAX + 0.5f).toInt()

    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BLUE_SHIFT = 0
    private const val BYTE = 0xFF
    private const val BYTE_MAX = 255f
    private const val HALF_ALPHA = 128
    private const val OPAQUE = 0xFF shl ALPHA_SHIFT

    // A key with almost no chroma (a gray) has no direction to take spill along.
    private const val MIN_KEY_CHROMA = 1e-4f
}
