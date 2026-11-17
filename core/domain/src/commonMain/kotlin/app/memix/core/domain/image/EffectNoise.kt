package app.memix.core.domain.image

import kotlin.math.floor

/**
 * The one source of randomness of the video effects (spec docs/ux/specs/P4-06-video-effects.md → Deterministic): a hash of the frame's
 * presentation time rounded to 1/30 s and a per-effect seed, never a live random number, so the preview, a seek and the export show the same
 * frame at the same time. Integer math only, so the CPU twins and the shaders agree: the shaders can't hash (GLSL ES 1.00 has no integer
 * bit operations), so they read the [cell] table as a 256 x 256 texture and are handed the per-frame offsets computed here.
 */
object EffectNoise {
    const val TABLE_SIZE = 256
    const val FRAMES_PER_SECOND = 30
    private const val GRAIN_STEPS_PER_SECOND = 24
    private const val MICROS_PER_SECOND = 1_000_000L

    // Seeds, one per effect that draws random numbers.
    const val GLITCH_SEED = 0x51A3
    const val STATIC_SEED = 0x7C15
    const val GRAIN_SEED = 0x2E9D
    const val VHS_SEED = 0x4B07
    private const val TABLE_SEED = 0x1D2B

    /**
     * The 1/30 s frame [timeUs] belongs to, rounded to the nearest: every random effect hashes this, never the raw time. A frame time of a 24 or 60 fps
     * video that falls exactly halfway between two 30 fps frames rounds up (half-way points are rare, but a video's own timestamps are rounded to whole
     * microseconds), so the preview and an export may land on neighbouring frames there and draw one frame of different noise: not a defect.
     */
    fun frameIndex(timeUs: Long): Int = ((timeUs * FRAMES_PER_SECOND + MICROS_PER_SECOND / 2).floorDiv(MICROS_PER_SECOND)).toInt()

    /** Film grain's frame: new 24 times a second, so the 1/24 s step [timeUs] is in, expressed as the nearest 1/30 s frame. */
    fun grainFrameIndex(timeUs: Long): Int {
        val step = (timeUs * GRAIN_STEPS_PER_SECOND).floorDiv(MICROS_PER_SECOND)
        return ((step * FRAMES_PER_SECOND + GRAIN_STEPS_PER_SECOND / 2).floorDiv(GRAIN_STEPS_PER_SECOND.toLong())).toInt()
    }

    /** A 32-bit hash of [a], [b] and [seed] (the finalizer of lowbias32 over a weighted sum). */
    fun hash(a: Int, b: Int, seed: Int): Int = scramble(a * MUL_A + scramble(b * MUL_B + seed))

    /** [hash] as a number from 0 up to but not including 1. */
    fun unit(a: Int, b: Int, seed: Int): Float = (hash(a, b, seed) ushr 8) / UNIT_DIVISOR

    /** The noise table's byte at cell ([x], [y]) (both wrap at [TABLE_SIZE]): the 256 x 256 texture the shaders read holds exactly these. */
    fun cell(x: Int, y: Int): Int = hash(x and (TABLE_SIZE - 1), y and (TABLE_SIZE - 1), TABLE_SEED) and 0xFF

    /** The table's value at cell ([x], [y]) as -1 to 1, flat. */
    fun signedAt(x: Int, y: Int): Float = cell(x, y) / 127.5f - 1f

    /** The frame's shift of the table along one axis (0 = x, 1 = y), 0 up to [TABLE_SIZE]: a new field per frame without a new table. */
    fun shift(seed: Int, frame: Int, axis: Int): Int = hash(frame, axis, seed) and (TABLE_SIZE - 1)

    /** The noise cell, in pixels, for a picture whose shorter side is [shortSidePx]: 1.5 px at 1080p, the same share at any size. */
    fun cellPx(shortSidePx: Int): Float = GRAIN_CELL_PX * shortSidePx / GRAIN_REFERENCE_SIDE_PX

    /** Adds the table's field, [amplitude] x -1..1, to every channel of [pixels] (luma noise): cells of [cellPx], shifted by frame [frame]'s offsets of [seed]. The shaders do the same read from the table's texture. */
    internal fun addLuma(pixels: IntArray, width: Int, height: Int, amplitude: Float, seed: Int, frame: Int) {
        val cell = cellPx(minOf(width, height))
        val shiftX = shift(seed, frame, 0)
        val shiftY = shift(seed, frame, 1)
        for (y in 0 until height) for (x in 0 until width) {
            val n = signedAt(floor(x / cell).toInt() + shiftX, floor(y / cell).toInt() + shiftY) * amplitude
            val p = pixels[y * width + x]
            pixels[y * width + x] = EffectPixels.pack(p, EffectPixels.red(p) + n, EffectPixels.green(p) + n, EffectPixels.blue(p) + n)
        }
    }

    private fun scramble(value: Int): Int {
        var x = value
        x = x xor (x ushr 16)
        x *= 0x7FEB352D
        x = x xor (x ushr 15)
        x *= 0x846CA68B.toInt()
        x = x xor (x ushr 16)
        return x
    }

    private const val GRAIN_CELL_PX = 1.5f
    private const val GRAIN_REFERENCE_SIDE_PX = 1080f
    private const val MUL_A = 0x9E3779B1.toInt()
    private const val MUL_B = 0x85EBCA6B.toInt()
    private const val UNIT_DIVISOR = 16_777_216f
}
