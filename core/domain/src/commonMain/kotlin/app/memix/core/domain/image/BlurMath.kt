package app.memix.core.domain.image

import app.memix.core.domain.image.EffectPixels.OPAQUE
import app.memix.core.domain.image.EffectPixels.bilinear
import app.memix.core.domain.image.EffectPixels.blue
import app.memix.core.domain.image.EffectPixels.green
import app.memix.core.domain.image.EffectPixels.mix
import app.memix.core.domain.image.EffectPixels.pack
import app.memix.core.domain.image.EffectPixels.red
import app.memix.core.domain.image.EffectPixels.smoothstep
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The Blur tab of P4-06b (Blur, Radial blur, Smear, Tilt blur, Soft focus; spec docs/ux/specs/P4-06-video-effects.md → The list) as numbers and CPU twins.
 * The three Gaussian effects work on a quarter-size copy, as Glow does, so their taps stay bounded at any strength: one pass averages 4 x 4 blocks into
 * the copy (four linear reads at block corners), two passes blur it along each axis, and a last pass reads the copy scaled up by the sampler's linear
 * filter and mixes it over the frame: **four passes** each. Radial blur and Smear are one pass of 16 taps on the full frame. The shaders
 * (`BlurShaders` in `:engine:video`) take every number from here.
 */
object BlurMath {
    const val BLUR_SIGMA = 0.025f
    const val TILT_SIGMA = 0.02f
    const val SOFT_SIGMA = 0.012f
    const val SOFT_MIX = 0.7f
    const val SOFT_BRIGHTNESS = 0.1f
    const val TILT_BAND = 0.25f
    const val TILT_FEATHER = 0.15f
    const val RADIAL_LENGTH = 0.12f
    const val SMEAR_LENGTH = 0.08f

    /** Radial blur's and Smear's taps along their line, as the spec writes: 16. */
    const val LINE_TAPS = 16

    /** The quarter-size copy: its blocks are this many pixels each way. */
    const val DOWNSCALE = EffectMath.GLOW_DOWNSCALE

    /** A blur whose sigma is under this share of the shorter side fades in from the sharp picture (the copy itself blurs about this much), so strength 0 is continuous. */
    private const val FADE_SIGMA_SHARE = 0.003f

    /** The smallest sigma the shader divides by, in copy pixels. */
    private const val MIN_COPY_SIGMA = 0.05f

    /** Sigma of the full-size blur of [share] of the shorter side at [strength], in pixels. */
    fun sigmaPx(share: Float, strength: Float, shortSidePx: Int): Float = share * shortSidePx * strength

    /** The sigma the blur passes use, in pixels of the quarter-size copy, for a full-size sigma of [sigmaPx]. */
    fun copySigma(sigmaPx: Float): Float = max(sigmaPx / DOWNSCALE, MIN_COPY_SIGMA)

    /** How many copy pixels each side the blur passes read, for the widest sigma the effect reaches ([share] at strength 1): three sigma. */
    fun reach(share: Float, shortSidePx: Int): Int = ceil(3f * share * shortSidePx / DOWNSCALE).toInt().coerceAtLeast(1)

    /** How much of the blurred copy shows over a blur of full-size sigma [sigmaPx]: all of it from [FADE_SIGMA_SHARE] of the shorter side up. */
    fun fadeIn(sigmaPx: Float, shortSidePx: Int): Float = min(1f, sigmaPx / (FADE_SIGMA_SHARE * shortSidePx))

    /** Tilt blur's blur weight at the row of height share [rowShare] (0 top to 1 bottom): 0 in the sharp band, 1 beyond its feather. */
    fun tiltWeight(rowShare: Float): Float = smoothstep(TILT_BAND / 2f - TILT_FEATHER / 2f, TILT_BAND / 2f + TILT_FEATHER / 2f, abs(rowShare - 0.5f))

    // ---- The CPU twins -----------------------------------------------------------------------------------------------

    fun blur(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val short = min(width, height)
        val sigma = sigmaPx(BLUR_SIGMA, strength, short)
        val copy = blurredCopy(pixels, width, height, reach(BLUR_SIGMA, short), copySigma(sigma))
        mixCopy(pixels, width, height, copy, { fadeIn(sigma, short) }, 1f)
    }

    fun tiltBlur(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val short = min(width, height)
        val sigma = sigmaPx(TILT_SIGMA, strength, short)
        val copy = blurredCopy(pixels, width, height, reach(TILT_SIGMA, short), copySigma(sigma))
        val fade = fadeIn(sigma, short)
        mixCopy(pixels, width, height, copy, { row -> fade * tiltWeight((row + 0.5f) / height) }, 1f)
    }

    fun softFocus(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val short = min(width, height)
        val copy = blurredCopy(pixels, width, height, reach(SOFT_SIGMA, short), copySigma(sigmaPx(SOFT_SIGMA, 1f, short)))
        mixCopy(pixels, width, height, copy, { SOFT_MIX * strength }, 1f + SOFT_BRIGHTNESS * strength)
    }

    /** Radial blur: [LINE_TAPS] taps from each pixel toward the center, as far as 12% of the distance x strength. */
    fun radialBlur(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val source = pixels.copyOf()
        val rgb = FloatArray(3)
        for (y in 0 until height) for (x in 0 until width) {
            val px = x + 0.5f
            val py = y + 0.5f
            var r = 0f
            var g = 0f
            var b = 0f
            for (i in 0 until LINE_TAPS) {
                val along = RADIAL_LENGTH * strength * i / (LINE_TAPS - 1)
                bilinear(source, width, height, px + (width / 2f - px) * along, py + (height / 2f - py) * along, rgb)
                r += rgb[0]
                g += rgb[1]
                b += rgb[2]
            }
            pixels[y * width + x] = pack(source[y * width + x], r / LINE_TAPS, g / LINE_TAPS, b / LINE_TAPS)
        }
    }

    /** Smear: [LINE_TAPS] taps along the row over 8% of the width x strength, centered on the pixel. */
    fun smear(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val source = pixels.copyOf()
        val rgb = FloatArray(3)
        val length = SMEAR_LENGTH * width * strength
        for (y in 0 until height) for (x in 0 until width) {
            var r = 0f
            var g = 0f
            var b = 0f
            for (i in 0 until LINE_TAPS) {
                bilinear(source, width, height, x + 0.5f + (i.toFloat() / (LINE_TAPS - 1) - 0.5f) * length, y + 0.5f, rgb)
                r += rgb[0]
                g += rgb[1]
                b += rgb[2]
            }
            pixels[y * width + x] = pack(source[y * width + x], r / LINE_TAPS, g / LINE_TAPS, b / LINE_TAPS)
        }
    }

    // The quarter-size copy, blurred along rows then columns; kept as the 8-bit pixels the shader's targets hold.
    private fun blurredCopy(pixels: IntArray, width: Int, height: Int, reach: Int, sigma: Float): Copy {
        val (lowWidth, lowHeight) = EffectMath.glowSize(width, height)
        val low = IntArray(lowWidth * lowHeight)
        val rgb = FloatArray(3)
        for (j in 0 until lowHeight) for (i in 0 until lowWidth) {
            var r = 0f
            var g = 0f
            var b = 0f
            for ((dx, dy) in CORNERS) {
                bilinear(pixels, width, height, (i * DOWNSCALE + dx).toFloat(), (j * DOWNSCALE + dy).toFloat(), rgb)
                r += rgb[0]
                g += rgb[1]
                b += rgb[2]
            }
            low[j * lowWidth + i] = pack(OPAQUE, r / CORNERS.size, g / CORNERS.size, b / CORNERS.size)
        }
        val across = EffectMath.blur(low, lowWidth, lowHeight, reach, sigma, horizontal = true)
        return Copy(EffectMath.blur(across, lowWidth, lowHeight, reach, sigma, horizontal = false), lowWidth, lowHeight)
    }

    // out = mix(frame, copy scaled up, weight(row)) x brightness.
    private inline fun mixCopy(pixels: IntArray, width: Int, height: Int, copy: Copy, weight: (Int) -> Float, brightness: Float) {
        val rgb = FloatArray(3)
        for (y in 0 until height) {
            val amount = weight(y)
            for (x in 0 until width) {
                bilinear(copy.pixels, copy.width, copy.height, (x + 0.5f) / width * copy.width, (y + 0.5f) / height * copy.height, rgb)
                val p = pixels[y * width + x]
                pixels[y * width + x] = pack(p, mix(red(p), rgb[0], amount) * brightness, mix(green(p), rgb[1], amount) * brightness, mix(blue(p), rgb[2], amount) * brightness)
            }
        }
    }

    private class Copy(val pixels: IntArray, val width: Int, val height: Int)

    // A 4 x 4 block's average as four reads at the block's inner corners, each of which the linear filter averages over 2 x 2 pixels.
    private val CORNERS = listOf(1 to 1, 3 to 1, 1 to 3, 3 to 3)
}
