package app.memix.core.domain.image

import app.memix.core.model.project.ClipLook
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The CPU twin of the video look shader (P4-07; spec docs/ux/specs/P4-07-video-filters-addendum.md → How it draws), and the one place
 * the shader's sharpen kernel is made. The GPU runs two passes over a clip's own picture: pass 1 is [ImagePipeline]'s steps 2 to 4
 * per pixel (brightness, contrast, saturation, warmth, then the filter's LUT at its intensity), stored as 8 bits; pass 2 is sharpen
 * (the unsharp mask of [ImagePipeline], its two box blurs folded into one separable kernel) and vignette. Same constants
 * ([ImagePipelineRules]), same order, so a photo and a video frame with the same look agree to within the 8-bit rounding and the
 * hardware's filtering. Pure Kotlin: the parity harness runs it against [ImagePipeline], and the engine reads [sharpenTaps] from it.
 */
object VideoLookMath {
    /** The widest blur the shader takes, in texels each side of the center: a clip taller than about 2600 px gets the radius of that size. */
    const val MAX_KERNEL_REACH = 8

    /** The shader's most taps along one axis: the center and four merged pairs each side (nine texels reach eight). */
    const val MAX_TAPS = MAX_KERNEL_REACH + 1

    /** The blur's box radius in texels for a picture whose shorter side is [shortSidePx] (not rounded, so the look scales with the size). */
    fun sharpenRadiusPx(shortSidePx: Float): Float = min(
        max(MIN_RADIUS_PX, ImagePipelineRules.SHARPEN_RADIUS_OF_SHORT_SIDE * shortSidePx),
        MAX_RADIUS_PX,
    )

    /**
     * The 1D blur weights for box [radius]: [ImagePipeline]'s box blur (whole pixels count fully, the pixel past a fractional radius by its
     * fraction) applied twice, as one kernel of `2 * reach + 1` weights summing to 1, the center at index `reach`.
     */
    fun sharpenKernel(radius: Float): FloatArray {
        val whole = radius.toInt()
        val edge = radius - whole
        val boxReach = whole + 1
        val box = FloatArray(2 * boxReach + 1) { i -> if (i == 0 || i == 2 * boxReach) edge else 1f }
        val total = 2f * radius + 1f
        for (i in box.indices) box[i] /= total
        val kernel = FloatArray(2 * box.size - 1)
        for (a in box.indices) for (b in box.indices) kernel[a + b] += box[a] * box[b]
        return kernel
    }

    /**
     * [kernel] as taps for a linear-filtering sampler: the center, then each pair of neighbouring texels on a side merged into one fetch
     * between them (offset = their weighted mean, weight = their sum), so nine texels cost five fetches. Offsets are in texels; the arrays
     * are symmetric about 0 and [SharpenTaps.count] long.
     */
    fun sharpenTaps(kernel: FloatArray): SharpenTaps {
        val reach = kernel.size / 2
        val offsets = ArrayList<Float>()
        val weights = ArrayList<Float>()
        offsets += 0f
        weights += kernel[reach]
        for (side in intArrayOf(-1, 1)) {
            var distance = 1
            while (distance <= reach) {
                val first = kernel[reach + side * distance]
                val second = if (distance + 1 <= reach) kernel[reach + side * (distance + 1)] else 0f
                val sum = first + second
                offsets += if (sum > 0f) side * (distance * first + (distance + 1) * second) / sum else (side * distance).toFloat()
                weights += sum
                distance += 2
            }
        }
        return SharpenTaps(offsets.toFloatArray(), weights.toFloatArray())
    }

    /**
     * Pass 1 for one pixel: [argb] with the look's adjustments and filter, 8 bits again. [lut] is the filter's table (null leaves a filter
     * out). The shader's fragment function, line by line.
     */
    fun pointwise(argb: Int, look: ClipLook, lut: Lut?, scratch: FloatArray = FloatArray(PIXEL_SCRATCH)): Int {
        val adjust = look.adjust
        var r = ((argb shr 16) and 0xFF) / 255f
        var g = ((argb shr 8) and 0xFF) / 255f
        var b = (argb and 0xFF) / 255f
        if (adjust.brightness != 0f) {
            val shift = adjust.brightness * ImagePipelineRules.BRIGHTNESS_SPAN
            r = clamp(r + shift)
            g = clamp(g + shift)
            b = clamp(b + shift)
        }
        if (adjust.contrast != 0f) {
            val k = 1f + adjust.contrast
            r = clamp((r - 0.5f) * k + 0.5f)
            g = clamp((g - 0.5f) * k + 0.5f)
            b = clamp((b - 0.5f) * k + 0.5f)
        }
        if (adjust.saturation != 0f) {
            val luma = LUMA_R * r + LUMA_G * g + LUMA_B * b
            val k = 1f + adjust.saturation
            r = clamp(luma + (r - luma) * k)
            g = clamp(luma + (g - luma) * k)
            b = clamp(luma + (b - luma) * k)
        }
        if (adjust.warmth != 0f) {
            r = clamp(r * (1f + ImagePipelineRules.WARMTH_SPAN * adjust.warmth))
            b = clamp(b * (1f - ImagePipelineRules.WARMTH_SPAN * adjust.warmth))
        }
        val filter = look.filter
        if (filter != null && lut != null && filter.intensity > 0f) {
            lut.sample(r, g, b, scratch)
            val t = filter.intensity.coerceIn(0f, 1f)
            r += (scratch[0] - r) * t
            g += (scratch[1] - g) * t
            b += (scratch[2] - b) * t
        }
        return (argb and ALPHA_MASK) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
    }

    /**
     * The whole look on [pixels] ([width] x [height] ARGB, the clip's own picture), in place: pass 1, then pass 2. What one frame of the
     * video looks like after the GPU; used to measure parity with the photo path and to make the reference renders.
     */
    fun process(pixels: IntArray, width: Int, height: Int, look: ClipLook, lut: Lut?) {
        if (look.isNeutral || width <= 0 || height <= 0) return
        val scratch = FloatArray(PIXEL_SCRATCH)
        for (i in pixels.indices) pixels[i] = pointwise(pixels[i], look, lut, scratch)
        val sharpenAmount = look.adjust.sharpen * ImagePipelineRules.SHARPEN_AMOUNT
        val vignette = look.adjust.vignette
        if (sharpenAmount <= 0f && vignette <= 0f) return
        val source = pixels.copyOf()
        val kernel = sharpenKernel(sharpenRadiusPx(min(width, height).toFloat()))
        val reach = kernel.size / 2
        val centerX = (width - 1) / 2f
        val centerY = (height - 1) / 2f
        val halfDiagonal = sqrt(centerX * centerX + centerY * centerY).coerceAtLeast(1f)
        val blur = FloatArray(3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val own = source[y * width + x]
                var r = ((own shr 16) and 0xFF) / 255f
                var g = ((own shr 8) and 0xFF) / 255f
                var b = (own and 0xFF) / 255f
                if (sharpenAmount > 0f) {
                    blurAt(source, width, height, x, y, kernel, reach, blur)
                    r = clamp(r + sharpenAmount * (r - blur[0]))
                    g = clamp(g + sharpenAmount * (g - blur[1]))
                    b = clamp(b + sharpenAmount * (b - blur[2]))
                }
                if (vignette > 0f) {
                    val factor = vignetteFactor(x - centerX, y - centerY, halfDiagonal, vignette)
                    r *= factor
                    g *= factor
                    b *= factor
                }
                pixels[y * width + x] = (own and ALPHA_MASK) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
            }
        }
    }

    /** The vignette's multiplier at the offset ([dx], [dy]) texels from the center: 1 - strength * smoothstep(0.35, 1, r). */
    fun vignetteFactor(dx: Float, dy: Float, halfDiagonal: Float, strength: Float): Float {
        val r = sqrt(dx * dx + dy * dy) / halfDiagonal
        val t = ((r - ImagePipelineRules.VIGNETTE_START) / (1f - ImagePipelineRules.VIGNETTE_START)).coerceIn(0f, 1f)
        return 1f - strength * (t * t * (3f - 2f * t))
    }

    // The separable blur at (x, y) of 8-bit pixels, the edge pixel repeating past the picture (the sampler's clamp-to-edge).
    internal fun blurAt(source: IntArray, width: Int, height: Int, x: Int, y: Int, kernel: FloatArray, reach: Int, out: FloatArray) {
        var r = 0f
        var g = 0f
        var b = 0f
        for (j in -reach..reach) {
            val wy = kernel[j + reach]
            val row = (y + j).coerceIn(0, height - 1) * width
            for (i in -reach..reach) {
                val w = wy * kernel[i + reach]
                val p = source[row + (x + i).coerceIn(0, width - 1)]
                r += w * ((p shr 16) and 0xFF)
                g += w * ((p shr 8) and 0xFF)
                b += w * (p and 0xFF)
            }
        }
        out[0] = r / 255f
        out[1] = g / 255f
        out[2] = b / 255f
    }

    private fun clamp(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

    private fun to8(v: Float): Int = (clamp(v) * 255f + 0.5f).toInt()

    private const val ALPHA_MASK = 0xFF000000.toInt()
    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f
    private const val MIN_RADIUS_PX = 0.5f

    // A box radius whose two blurs reach MAX_KERNEL_REACH texels: whole + 1 on each of the two passes.
    private const val MAX_RADIUS_PX = MAX_KERNEL_REACH / 2f - 0.01f
    private const val PIXEL_SCRATCH = 3
}

/** The sharpen blur as fetch offsets (texels along one axis, from the center) and their weights; both [count] long. */
class SharpenTaps(val offsets: FloatArray, val weights: FloatArray) {
    val count: Int get() = offsets.size
}
