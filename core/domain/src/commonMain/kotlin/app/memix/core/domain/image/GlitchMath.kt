package app.memix.core.domain.image

import app.memix.core.domain.image.EffectPixels.bilinear
import app.memix.core.domain.image.EffectPixels.mix
import app.memix.core.domain.image.EffectPixels.pack
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Glitch tab of P4-06b (Glitch, RGB split, Tear, Pixelate, Static; spec docs/ux/specs/P4-06-video-effects.md → The list) as numbers and CPU twins.
 * The shaders (`GlitchShaders` in `:engine:video`) take every number from here, as `EffectMath` does for the first 15. Glitch, RGB split and Tear
 * are one pass of the same sideways-shift shader (rows moved by [rowShifts], then red and blue slid apart); Pixelate and Static are one pass each.
 * Random numbers come from `EffectNoise` only.
 */
object GlitchMath {
    // Glitch: bursts of 3 frames (90 ms) in 10-frame slots (1/3 s), so there are at most 3 a second and they never overlap.
    const val BURST_SLOT_FRAMES = 10
    const val BURST_FRAMES = 3
    const val MAX_SLICES = 10
    private const val MIN_SLICES = 6
    private const val SLICE_HEIGHT_MIN = 0.02f
    private const val SLICE_HEIGHT_MAX = 0.09f
    private const val SLICE_SHIFT_MAX = 0.08f
    private const val GLITCH_SPLIT = 0.01f

    /** A slot has a burst unless the low two bits of its hash are 0: three of four slots do. */
    private const val BURST_MASK = 3
    private const val BURST_START_MASK = 7

    // RGB split.
    private const val RGB_SPLIT = 0.015f

    // Tear: the band's height and sweep, the sideways shift as a share of the width.
    private const val TEAR_BAND = 0.12f
    const val TEAR_PERIOD_SECONDS = 1.2f
    private const val TEAR_SHIFT = 0.05f

    // Pixelate: the block is (BASE + SPAN x s) of the shorter side; the mosaic fades in over the first 1/FADE_STRENGTH of the strength so the ramp has no pop.
    private const val PIXELATE_BASE = 0.01f
    private const val PIXELATE_SPAN = 0.09f
    private const val PIXELATE_FADE_STRENGTH = 0.125f

    // Static.
    const val STATIC_AMOUNT = 0.6f

    /** Floats per slice in [rowShifts]'s output: the top row, the row after the bottom, the shift in pixels. */
    const val SLICE_FLOATS = 3

    /**
     * True when frame [frame] (a 1/30 s index, `EffectNoise.frameIndex`) is inside a glitch burst. Each slot may hold one burst; a slot's burst is dropped
     * when it would start sooner after its neighbour's than a slot is long, so any two bursts start at least 10 frames apart and no second holds more than 3.
     */
    fun isBurstFrame(frame: Int): Boolean {
        val slot = frame.floorDiv(BURST_SLOT_FRAMES)
        val start = burstStart(slot)
        if (start < 0) return false
        val before = burstStart(slot - 1)
        if (before >= 0 && start < before) return false
        return frame - slot * BURST_SLOT_FRAMES in start until start + BURST_FRAMES
    }

    // The frame within [slot] a burst would start at, or -1 for none (a slot has one unless the low two bits of its hash are 0).
    private fun burstStart(slot: Int): Int {
        if (EffectNoise.hash(slot, 0, EffectNoise.GLITCH_SEED) and BURST_MASK == 0) return -1
        return EffectNoise.hash(slot, 1, EffectNoise.GLITCH_SEED) and BURST_START_MASK
    }

    /** The first frame, from 0, in the middle of a burst: where the reference render and the tile are taken. */
    fun firstBurstMiddleFrame(): Int = (0..BURST_SLOT_FRAMES * 20).first { isBurstFrame(it - 1) && isBurstFrame(it) && isBurstFrame(it + 1) }

    /**
     * Fills [out] ([MAX_SLICES] x [SLICE_FLOATS] floats, unused slices all zero) with frame [frame]'s slices of a [width] x [height] picture at
     * strength [strength], and returns true; false (and zeros) when the frame is not in a burst. A slice is 6 to 10 bands 2 to 9% of the height tall,
     * moved sideways by up to 8% of the width x strength. Later slices cover earlier ones.
     */
    fun slices(frame: Int, strength: Float, width: Int, height: Int, out: FloatArray): Boolean {
        out.fill(0f)
        if (!isBurstFrame(frame)) return false
        val count = MIN_SLICES + (EffectNoise.unit(frame, 2, EffectNoise.GLITCH_SEED) * (MAX_SLICES - MIN_SLICES + 1)).toInt().coerceAtMost(MAX_SLICES - MIN_SLICES)
        for (i in 0 until count) {
            val bandHeight = (SLICE_HEIGHT_MIN + (SLICE_HEIGHT_MAX - SLICE_HEIGHT_MIN) * EffectNoise.unit(frame, 10 + i, EffectNoise.GLITCH_SEED)) * height
            val top = EffectNoise.unit(frame, 30 + i, EffectNoise.GLITCH_SEED) * (height - bandHeight)
            out[i * SLICE_FLOATS] = top
            out[i * SLICE_FLOATS + 1] = top + bandHeight
            out[i * SLICE_FLOATS + 2] = (2f * EffectNoise.unit(frame, 50 + i, EffectNoise.GLITCH_SEED) - 1f) * SLICE_SHIFT_MAX * width * strength
        }
        return true
    }

    /** The red/blue slide in pixels of Glitch during a burst. */
    fun glitchSplitPx(strength: Float, width: Int): Float = GLITCH_SPLIT * width * strength

    /** The red/blue slide in pixels of RGB split. */
    fun rgbSplitPx(strength: Float, width: Int): Float = RGB_SPLIT * width * strength

    /** Fills [out] with Tear's band [tauSeconds] into the item: the top row, its height, and the shift's amplitude in pixels; the band's middle sweeps from the top to the bottom each [TEAR_PERIOD_SECONDS]. */
    fun tearBand(tauSeconds: Float, strength: Float, width: Int, height: Int, out: FloatArray) {
        val cycle = tauSeconds / TEAR_PERIOD_SECONDS
        val middle = height * (cycle - floor(cycle))
        out[0] = middle - TEAR_BAND * height / 2f
        out[1] = TEAR_BAND * height
        out[2] = TEAR_SHIFT * width * strength
    }

    /** A tear band of zero amplitude: what the shift shader gets when the effect is not Tear. */
    fun noTear(height: Int, out: FloatArray) {
        out[0] = 0f
        out[1] = TEAR_BAND * height
        out[2] = 0f
    }

    /** Pixelate's block edge in pixels for a picture whose shorter side is [shortSidePx], at strength [strength]. */
    fun pixelBlockPx(strength: Float, shortSidePx: Int): Float = max(1f, ((PIXELATE_BASE + PIXELATE_SPAN * strength) * shortSidePx).roundToInt().toFloat())

    /** How much of the mosaic shows: 0 at strength 0, all of it from [PIXELATE_FADE_STRENGTH] up. */
    fun pixelMix(strength: Float): Float = min(1f, strength / PIXELATE_FADE_STRENGTH)

    // ---- The CPU twins -----------------------------------------------------------------------------------------------

    /** Glitch at [frame]: nothing outside a burst. */
    fun glitch(pixels: IntArray, width: Int, height: Int, strength: Float, frame: Int) {
        val slices = FloatArray(MAX_SLICES * SLICE_FLOATS)
        if (!slices(frame, strength, width, height, slices)) return
        val tear = FloatArray(3).also { noTear(height, it) }
        shift(pixels, width, height, slices, tear, glitchSplitPx(strength, width))
    }

    fun rgbSplit(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val tear = FloatArray(3).also { noTear(height, it) }
        shift(pixels, width, height, FloatArray(MAX_SLICES * SLICE_FLOATS), tear, rgbSplitPx(strength, width))
    }

    fun tear(pixels: IntArray, width: Int, height: Int, strength: Float, tauSeconds: Float) {
        val tear = FloatArray(3).also { tearBand(tauSeconds, strength, width, height, it) }
        shift(pixels, width, height, FloatArray(MAX_SLICES * SLICE_FLOATS), tear, 0f)
    }

    // The shift shader: each row is moved sideways (slice, then tear), then red is read [split] pixels to the right and blue to the left.
    private fun shift(pixels: IntArray, width: Int, height: Int, slices: FloatArray, tear: FloatArray, split: Float) {
        val source = pixels.copyOf()
        val rgb = FloatArray(3)
        val other = FloatArray(3)
        for (y in 0 until height) {
            val row = y + 0.5f
            var dx = 0f
            for (i in 0 until MAX_SLICES) if (row >= slices[i * SLICE_FLOATS] && row < slices[i * SLICE_FLOATS + 1]) dx = slices[i * SLICE_FLOATS + 2]
            val along = (row - tear[0]) / tear[1]
            if (along >= 0f && along < 1f) dx += tear[2] * sin(TAU * along)
            for (x in 0 until width) {
                val at = x + 0.5f - dx
                bilinear(source, width, height, at + split, row, rgb)
                bilinear(source, width, height, at - split, row, other)
                val red = rgb[0]
                val blue = other[2]
                bilinear(source, width, height, at, row, rgb)
                pixels[y * width + x] = pack(source[y * width + x], red, rgb[1], blue)
            }
        }
    }

    /** Pixelate: each block shows the picture at its middle, faded in by [pixelMix]. */
    fun pixelate(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val source = pixels.copyOf()
        val block = pixelBlockPx(strength, min(width, height))
        val amount = pixelMix(strength)
        val rgb = FloatArray(3)
        for (y in 0 until height) for (x in 0 until width) {
            bilinear(source, width, height, (floor((x + 0.5f) / block) + 0.5f) * block, (floor((y + 0.5f) / block) + 0.5f) * block, rgb)
            val p = source[y * width + x]
            pixels[y * width + x] = pack(p, mix(EffectPixels.red(p), rgb[0], amount), mix(EffectPixels.green(p), rgb[1], amount), mix(EffectPixels.blue(p), rgb[2], amount))
        }
    }

    /** Static: luma noise of up to [STATIC_AMOUNT] x strength added to every channel, a new field each frame. */
    fun staticNoise(pixels: IntArray, width: Int, height: Int, strength: Float, frame: Int) {
        EffectNoise.addLuma(pixels, width, height, STATIC_AMOUNT * strength, EffectNoise.STATIC_SEED, frame)
    }

    private const val TAU = (2.0 * PI).toFloat()
}
