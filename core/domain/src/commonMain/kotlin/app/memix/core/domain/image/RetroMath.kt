package app.memix.core.domain.image

import app.memix.core.domain.image.EffectPixels.bilinear
import app.memix.core.domain.image.EffectPixels.pack
import app.memix.core.domain.image.EffectPixels.smoothstep
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The Retro tab of P4-06b (VHS, Old screen, Film grain, Fisheye, Mirror; spec docs/ux/specs/P4-06-video-effects.md → The list) as numbers and CPU twins.
 * Each is one pass; the shaders (`RetroShaders` in `:engine:video`) take every number from here. Line patterns scale with height / 1080 (never below 1
 * pixel) as the spec writes for VHS; everything else is a share of the width, height or shorter side.
 */
object RetroMath {
    // VHS.
    const val VHS_CHROMA = 0.006f
    const val VHS_SCAN_ROW_PX = 2f
    const val VHS_SCAN_DARKEN = 0.12f
    const val VHS_BAND = 0.06f
    const val VHS_BAND_CYCLES_PER_SECOND = 0.25f
    const val VHS_JITTER = 0.015f
    const val VHS_NOISE = 0.04f
    const val VHS_SOFT_PX = 0.5f

    // Old screen.
    const val SCREEN_PERIOD_PX = 3f
    const val SCREEN_MASK = 0.15f
    const val SCREEN_BEND = 0.04f
    const val SCREEN_VIGNETTE = 0.45f
    const val SCREEN_VIGNETTE_NEAR = 0.6f
    const val SCREEN_VIGNETTE_FAR = 1.4142135f

    // Film grain: the spec's sigma; the table's noise is flat in -1..1 (sigma 1/sqrt(3)), so the amplitude is sigma x sqrt(3).
    const val GRAIN_SIGMA = 0.1f
    private const val SQRT_THREE = 1.7320508f

    // Fisheye.
    const val FISHEYE_K = 0.45f

    // Mirror: the fold goes from the right edge to the middle.
    const val MIRROR_FOLD_TRAVEL = 0.5f

    /** The unit of the line patterns in pixels: height / 1080, never under one pixel so a pattern stays on whole rows. */
    fun patternUnitPx(height: Int): Float = max(1f, height / 1080f)

    /** Film grain's flat noise amplitude at [strength]. */
    fun grainAmplitude(strength: Float): Float = GRAIN_SIGMA * SQRT_THREE * strength

    /** Fills [out] with VHS's tracking band: the middle row, half its height in pixels, and this frame's sideways jitter in pixels. It drifts up [VHS_BAND_CYCLES_PER_SECOND] a second. */
    fun vhsBand(tauSeconds: Float, strength: Float, frame: Int, width: Int, height: Int, out: FloatArray) {
        val cycle = VHS_BAND_CYCLES_PER_SECOND * tauSeconds
        out[0] = height * (1f - (cycle - floor(cycle)))
        out[1] = VHS_BAND * height / 2f
        out[2] = (2f * EffectNoise.unit(frame, 0, EffectNoise.VHS_SEED) - 1f) * VHS_JITTER * width * strength
    }

    // ---- The CPU twins -----------------------------------------------------------------------------------------------

    fun vhs(pixels: IntArray, width: Int, height: Int, strength: Float, tauSeconds: Float, frame: Int) {
        val source = pixels.copyOf()
        val band = FloatArray(3).also { vhsBand(tauSeconds, strength, frame, width, height, it) }
        val chroma = VHS_CHROMA * width * strength
        val soft = VHS_SOFT_PX * strength
        val lineHeight = VHS_SCAN_ROW_PX * patternUnitPx(height)
        val rgb = FloatArray(3)
        val other = FloatArray(3)
        for (y in 0 until height) {
            val row = y + 0.5f
            val inBand = abs(row - band[0]) < band[1]
            val dark = if (floor(y / lineHeight).toInt() % 2 == 1) 1f - VHS_SCAN_DARKEN * strength else 1f
            for (x in 0 until width) {
                val at = x + 0.5f + if (inBand) band[2] else 0f
                val r = softRead(source, width, height, at + chroma, row, soft, rgb, other, 0)
                val g = softRead(source, width, height, at, row, soft, rgb, other, 1)
                val b = softRead(source, width, height, at - chroma, row, soft, rgb, other, 2)
                pixels[y * width + x] = pack(source[y * width + x], r * dark, g * dark, b * dark)
            }
        }
        EffectNoise.addLuma(pixels, width, height, VHS_NOISE * strength, EffectNoise.VHS_SEED, frame)
    }

    // One channel read as the average of two reads [soft] pixels either side.
    private fun softRead(source: IntArray, width: Int, height: Int, x: Float, y: Float, soft: Float, a: FloatArray, b: FloatArray, channel: Int): Float {
        bilinear(source, width, height, x - soft, y, a)
        bilinear(source, width, height, x + soft, y, b)
        return 0.5f * (a[channel] + b[channel])
    }

    fun oldScreen(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val source = pixels.copyOf()
        val unit = patternUnitPx(height)
        val rgb = FloatArray(3)
        val mask = 1f - SCREEN_MASK * strength
        for (y in 0 until height) for (x in 0 until width) {
            val nx = (x + 0.5f - width / 2f) / (width / 2f)
            val ny = (y + 0.5f - height / 2f) / (height / 2f)
            val radiusSquared = nx * nx + ny * ny
            val bend = 1f + SCREEN_BEND * strength * radiusSquared
            val qx = nx * bend
            val qy = ny * bend
            // One pixel of soft edge where the bent picture ends.
            val inside = ((1f - abs(qx)) * width / 2f + 0.5f).coerceIn(0f, 1f) * ((1f - abs(qy)) * height / 2f + 0.5f).coerceIn(0f, 1f)
            bilinear(source, width, height, (qx + 1f) * width / 2f, (qy + 1f) * height / 2f, rgb)
            val rowDark = if (floor(y / unit).toInt() % 3 == 2) mask else 1f
            val column = floor(x / unit).toInt() % 3
            val vignette = 1f - SCREEN_VIGNETTE * strength * smoothstep(SCREEN_VIGNETTE_NEAR, SCREEN_VIGNETTE_FAR, sqrt(radiusSquared))
            val scale = rowDark * vignette * inside
            pixels[y * width + x] = pack(source[y * width + x], rgb[0] * scale * (if (column == 0) 1f else mask), rgb[1] * scale * (if (column == 1) 1f else mask), rgb[2] * scale * (if (column == 2) 1f else mask))
        }
    }

    /** Film grain: flat luma noise of sigma [GRAIN_SIGMA] x strength, a new field 24 times a second. */
    fun filmGrain(pixels: IntArray, width: Int, height: Int, strength: Float, grainFrame: Int) {
        EffectNoise.addLuma(pixels, width, height, grainAmplitude(strength), EffectNoise.GRAIN_SEED, grainFrame)
    }

    /** Fisheye: each pixel reads the picture at its distance from the center (in half-diagonals) x (1 + k r^2) / (1 + k): the middle grows and the corners stay put, so no edge shows. */
    fun fisheye(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val source = pixels.copyOf()
        val half = 0.5f * sqrt(width.toFloat() * width + height.toFloat() * height)
        val k = FISHEYE_K * strength
        val rgb = FloatArray(3)
        for (y in 0 until height) for (x in 0 until width) {
            val dx = (x + 0.5f - width / 2f) / half
            val dy = (y + 0.5f - height / 2f) / half
            val scale = (1f + k * (dx * dx + dy * dy)) / (1f + k)
            bilinear(source, width, height, width / 2f + dx * scale * half, height / 2f + dy * scale * half, rgb)
            pixels[y * width + x] = pack(source[y * width + x], rgb[0], rgb[1], rgb[2])
        }
    }

    /** Where Mirror's fold is, in pixels from the left, at [strength]. */
    fun mirrorFoldPx(strength: Float, width: Int): Float = width * (1f - MIRROR_FOLD_TRAVEL * strength)

    fun mirror(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val source = pixels.copyOf()
        val fold = mirrorFoldPx(strength, width)
        val rgb = FloatArray(3)
        for (y in 0 until height) for (x in 0 until width) {
            val at = x + 0.5f
            if (at <= fold) continue
            bilinear(source, width, height, 2f * fold - at, y + 0.5f, rgb)
            pixels[y * width + x] = pack(source[y * width + x], rgb[0], rgb[1], rgb[2])
        }
    }
}
