package app.memix.core.domain.image

import app.memix.core.model.project.TransitionCatalog
import app.memix.core.domain.image.EffectPixels.bilinear
import app.memix.core.domain.image.EffectPixels.blue
import app.memix.core.domain.image.EffectPixels.green
import app.memix.core.domain.image.EffectPixels.mix
import app.memix.core.domain.image.EffectPixels.pack
import app.memix.core.domain.image.EffectPixels.red
import app.memix.core.domain.image.EffectPixels.smoothstep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The 20 transitions of P4-08 (spec docs/ux/specs/P4-08-transitions.md → The list) as numbers and as a CPU twin of the shaders (`TransitionShaders`
 * in `:engine:video`, which take every constant from here). Every transition is one formula, `mix(A'(x, y), B'(x, y), m(x, y))`: A' is made from
 * the outgoing picture alone, B' from the incoming picture alone, and m is how much of B' shows. That is what lets the engine draw it with two
 * layers that never see each other: the compositor blends the TOP layer over the BOTTOM one as `top * a + bottom * (1 - a)`, so the layer drawn on
 * top carries `a = 1 - m` when it is the outgoing picture (before the cut) and `a = m` when it is the incoming one (after the cut), and the bottom
 * layer is opaque. The roles swap at the cut, which is progress 0.5, so every hard swap in a recipe sits at 0.5.
 *
 * Progress [p] runs 0 to 1 across the window; [q] is [eased] of it (Bézier 0.42, 0, 0.58, 1). Pictures outside the frame are black. Sizes are
 * shares of the shorter side, so a 720p preview and a 1080p export look the same. Pure Kotlin on ARGB [IntArray]s; pixel (x, y) has its center
 * at +0.5, y down.
 */
object TransitionMath {
    /** The soft edge of the wipes, iris, clock and blinds, a share of the frame (the clock's: of a turn). */
    const val SOFT_EDGE = 0.03f

    /** Blur through blurs to this share of the shorter side at the cut. */
    const val BLUR_SIGMA = 0.03f

    /** Blur through's kernel: [BLUR_TAPS] x [BLUR_TAPS] taps [BLUR_TAP_SPACING] sigmas apart (a constant loop in the shader). */
    const val BLUR_TAPS = 7
    const val BLUR_TAP_SPACING = 0.6f

    const val ZOOM_OUT_SCALE = 1.8f
    const val ZOOM_IN_SCALE = 0.8f
    const val TWIRL_DEGREES = 90f
    const val TWIRL_SMALL_SCALE = 0.6f
    const val BLINDS = 8f

    /** Pixel swap's biggest cell, a share of the shorter side; its smallest is one pixel (the picture itself). */
    const val PIXEL_CELL = 0.12f

    /** Glitch cut: three bursts of at most [GLITCH_BURST_US] (or a fifth of the window), centred at these shares of the window. */
    const val GLITCH_BURST_US = 90_000L
    val GLITCH_BURST_CENTERS = TransitionCatalog.GLITCH_BURST_CENTERS
    const val GLITCH_SLICES = 6
    const val GLITCH_SLICE_FLOATS = 3
    const val GLITCH_RGB_SPLIT = 0.01f
    private const val GLITCH_SHIFT_MAX = 0.08f
    private const val GLITCH_SLICE_HEIGHT_MIN = 0.02f
    private const val GLITCH_SLICE_HEIGHT_SPAN = 0.07f

    private const val BEZIER_X1 = 0.42f
    private const val BEZIER_X2 = 0.58f
    private const val BEZIER_STEPS = 24
    private const val TAU = (2.0 * PI).toFloat()

    /** Progress [p] (0 to 1) eased by the cubic Bézier (0.42, 0, 0.58, 1). Symmetric: 0.5 stays 0.5, which is where the roles swap. */
    fun eased(p: Float): Float {
        val x = p.coerceIn(0f, 1f)
        // Solve x(t) = p by bisection (x is increasing in t), then read y(t).
        var low = 0f
        var high = 1f
        repeat(BEZIER_STEPS) {
            val mid = (low + high) * 0.5f
            if (bezier(mid, BEZIER_X1, BEZIER_X2) < x) low = mid else high = mid
        }
        return bezier((low + high) * 0.5f, 0f, 1f)
    }

    private fun bezier(t: Float, c1: Float, c2: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * c1 + 3f * u * t * t * c2 + t * t * t
    }

    /** The glitch burst (0 to 2) that window time [p] x [durationUs] falls in, or -1 between bursts. */
    fun glitchBurstAt(p: Float, durationUs: Long): Int {
        val lengthUs = min(GLITCH_BURST_US, durationUs / 5)
        val timeUs = p * durationUs
        for (burst in GLITCH_BURST_CENTERS.indices) {
            if (abs(timeUs - GLITCH_BURST_CENTERS[burst] * durationUs) <= lengthUs / 2f) return burst
        }
        return -1
    }

    /**
     * Fills [out] ([GLITCH_SLICES] x [GLITCH_SLICE_FLOATS] floats: top row, bottom row, shift in pixels) with burst [burst]'s slices of a
     * [width] x [height] picture. Fixed by the burst number, so the preview, the export and a replay all show the same glitch.
     */
    fun glitchSlices(burst: Int, width: Int, height: Int, out: FloatArray) {
        out.fill(0f)
        if (burst < 0) return
        for (i in 0 until GLITCH_SLICES) {
            val bandHeight = (GLITCH_SLICE_HEIGHT_MIN + GLITCH_SLICE_HEIGHT_SPAN * EffectNoise.unit(burst, 10 + i, EffectNoise.GLITCH_SEED)) * height
            val top = EffectNoise.unit(burst, 30 + i, EffectNoise.GLITCH_SEED) * (height - bandHeight)
            out[i * GLITCH_SLICE_FLOATS] = top
            out[i * GLITCH_SLICE_FLOATS + 1] = top + bandHeight
            out[i * GLITCH_SLICE_FLOATS + 2] = (2f * EffectNoise.unit(burst, 50 + i, EffectNoise.GLITCH_SEED) - 1f) * GLITCH_SHIFT_MAX * width
        }
    }

    /** A soft edge's position at eased progress [q]: -[soft] (nothing shown) to 1 + [soft] (everything), so the first and last frames are plain. */
    fun sweep(q: Float, soft: Float): Float = q * (1f + 2f * soft) - soft

    // ---- The CPU twin ------------------------------------------------------------------------------------------------

    /**
     * One layer of transition [id] at progress [p]: [source] is the layer's own picture (the outgoing one when [incoming] is false). The color is
     * what the formula makes of that picture; the alpha is 255 for the [top] = false layer, and for the top layer the share of the layer that
     * shows (`1 - m` for the outgoing picture, `m` for the incoming one).
     */
    fun layer(id: String, incoming: Boolean, top: Boolean, source: IntArray, width: Int, height: Int, p: Float, durationUs: Long): IntArray {
        val frame = Frame(source, width, height, p, eased(p), durationUs)
        val out = IntArray(width * height)
        val rgb = FloatArray(3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                frame.colorOf(id, incoming, x + 0.5f, y + 0.5f, rgb)
                val m = weightOfIncoming(id, x + 0.5f, y + 0.5f, width, height, p, frame.q)
                val alpha = if (!top) 1f else if (incoming) m else 1f - m
                out[y * width + x] = (EffectPixels.to8(alpha) shl 24) or (EffectPixels.to8(rgb[0]) shl 16) or (EffectPixels.to8(rgb[1]) shl 8) or EffectPixels.to8(rgb[2])
            }
        }
        return out
    }

    /** What the compositor makes of [top] over an opaque [bottom]: `top * a + bottom * (1 - a)`, rounded to 8 bits, opaque. */
    fun compose(top: IntArray, bottom: IntArray): IntArray = IntArray(top.size) { i ->
        val a = (top[i] ushr 24) / 255f
        pack(0xFF000000.toInt(), mix(red(bottom[i]), red(top[i]), a), mix(green(bottom[i]), green(top[i]), a), mix(blue(bottom[i]), blue(top[i]), a))
    }

    /** The frame of transition [id] at progress [p]: the top layer is the outgoing picture before the cut and the incoming one after it. */
    fun render(id: String, outgoing: IntArray, incoming: IntArray, width: Int, height: Int, p: Float, durationUs: Long): IntArray {
        val outgoingOnTop = p < 0.5f
        val top = if (outgoingOnTop) layer(id, false, true, outgoing, width, height, p, durationUs) else layer(id, true, true, incoming, width, height, p, durationUs)
        val bottom = if (outgoingOnTop) layer(id, true, false, incoming, width, height, p, durationUs) else layer(id, false, false, outgoing, width, height, p, durationUs)
        return compose(top, bottom)
    }

    /** m: how much of the incoming picture shows at pixel center ([x], [y]) (0 to 1), from progress [p] (the hard swaps) and its eased [q]. */
    fun weightOfIncoming(id: String, x: Float, y: Float, width: Int, height: Int, p: Float, q: Float): Float {
        val u = x / width
        val v = y / height
        val short = min(width, height).toFloat()
        return when (id) {
            "dissolve", "zoom_through", "twirl" -> q
            "dip_black", "dip_white", "blur_through", "flip", "glitch_cut", "pixel_swap" -> if (p >= 0.5f) 1f else 0f
            "push_left", "cover", "uncover" -> if (u >= 1f - q) 1f else 0f
            "push_right" -> if (u < q) 1f else 0f
            "push_up" -> if (v >= 1f - q) 1f else 0f
            "push_down" -> if (v < q) 1f else 0f
            "wipe_right" -> 1f - smoothstep(sweep(q, SOFT_EDGE) - SOFT_EDGE, sweep(q, SOFT_EDGE) + SOFT_EDGE, u)
            "wipe_down" -> 1f - smoothstep(sweep(q, SOFT_EDGE) - SOFT_EDGE, sweep(q, SOFT_EDGE) + SOFT_EDGE, v)
            "iris" -> {
                val soft = SOFT_EDGE * short
                val radius = q * (0.5f * sqrt((width * width + height * height).toFloat()) + 2f * soft) - soft
                val dx = x - width * 0.5f
                val dy = y - height * 0.5f
                1f - smoothstep(radius - soft, radius + soft, sqrt(dx * dx + dy * dy))
            }
            "clock" -> {
                val turn = (atan2(x - width * 0.5f, -(y - height * 0.5f)) / TAU).let { if (it < 0f) it + 1f else it }
                val edge = sweep(q, SOFT_EDGE)
                1f - smoothstep(edge - SOFT_EDGE, edge + SOFT_EDGE, turn)
            }
            "blinds" -> {
                val band = v * BLINDS
                val edge = sweep(q, SOFT_EDGE)
                1f - smoothstep(edge - SOFT_EDGE, edge + SOFT_EDGE, band - floor(band))
            }
            else -> 0f
        }
    }

    // One layer's picture and its progress, with the per-pixel color of every recipe.
    private class Frame(val source: IntArray, val width: Int, val height: Int, val p: Float, val q: Float, val durationUs: Long) {
        private val short = min(width, height).toFloat()
        private val tmp = FloatArray(3)
        private val slices = FloatArray(GLITCH_SLICES * GLITCH_SLICE_FLOATS).also { glitchSlices(glitchBurstAt(p, durationUs), width, height, it) }
        private val burst = glitchBurstAt(p, durationUs)

        fun colorOf(id: String, incoming: Boolean, x: Float, y: Float, out: FloatArray) {
            // How far the picture is from plain, 0 to 1: the outgoing one goes away over the first half, the incoming one comes back over the second (dips, blur, pixels).
            val away = if (incoming) 1f - max(0f, 2f * q - 1f) else min(1f, 2f * q)
            when (id) {
                "dissolve", "wipe_right", "wipe_down", "iris", "clock", "blinds" -> at(x, y, out)
                "dip_black" -> {
                    at(x, y, out)
                    for (c in 0..2) out[c] *= 1f - away
                }
                "dip_white" -> {
                    at(x, y, out)
                    for (c in 0..2) out[c] = mix(out[c], 1f, away)
                }
                "blur_through" -> blur(x, y, BLUR_SIGMA * short * away, out)
                "push_left" -> if (incoming) at(x - width * (1f - q), y, out) else at(x + width * q, y, out)
                "push_right" -> if (incoming) at(x + width * (1f - q), y, out) else at(x - width * q, y, out)
                "push_up" -> if (incoming) at(x, y - height * (1f - q), out) else at(x, y + height * q, out)
                "push_down" -> if (incoming) at(x, y + height * (1f - q), out) else at(x, y - height * q, out)
                "cover" -> if (incoming) at(x - width * (1f - q), y, out) else at(x, y, out)
                "uncover" -> if (incoming) at(x, y, out) else at(x + width * q, y, out)
                "zoom_through" -> zoomed(x, y, if (incoming) ZOOM_IN_SCALE + (1f - ZOOM_IN_SCALE) * q else 1f + (ZOOM_OUT_SCALE - 1f) * q, out)
                "twirl" -> twirled(x, y, out, incoming)
                "flip" -> flipped(x, y, out, incoming)
                "glitch_cut" -> glitched(x, y, out)
                "pixel_swap" -> pixelated(x, y, PIXEL_CELL * short * away, out)
                else -> at(x, y, out)
            }
        }

        // The picture at the continuous point (x, y); black outside the frame.
        private fun at(x: Float, y: Float, out: FloatArray) {
            if (x < 0f || y < 0f || x > width || y > height) {
                out.fill(0f)
                return
            }
            bilinear(source, width, height, x, y, out)
        }

        private fun blur(x: Float, y: Float, sigmaPx: Float, out: FloatArray) {
            if (sigmaPx < 0.5f) return at(x, y, out)
            val step = BLUR_TAP_SPACING * sigmaPx
            val half = BLUR_TAPS / 2
            var total = 0f
            out.fill(0f)
            for (j in 0 until BLUR_TAPS) {
                for (i in 0 until BLUR_TAPS) {
                    val dx = (i - half).toFloat()
                    val dy = (j - half).toFloat()
                    val weight = kotlin.math.exp(-0.5f * BLUR_TAP_SPACING * BLUR_TAP_SPACING * (dx * dx + dy * dy))
                    at(x + dx * step, y + dy * step, tmp)
                    for (c in 0..2) out[c] += tmp[c] * weight
                    total += weight
                }
            }
            for (c in 0..2) out[c] /= total
        }

        private fun zoomed(x: Float, y: Float, scale: Float, out: FloatArray) =
            at(width * 0.5f + (x - width * 0.5f) / scale, height * 0.5f + (y - height * 0.5f) / scale, out)

        // The picture turned clockwise by `degrees` and scaled, about the center: the pixel looks up where it came from.
        private fun twirled(x: Float, y: Float, out: FloatArray, incoming: Boolean) {
            val degrees = if (incoming) -TWIRL_DEGREES * (1f - q) else TWIRL_DEGREES * q
            val scale = if (incoming) TWIRL_SMALL_SCALE + (1f - TWIRL_SMALL_SCALE) * q else 1f - (1f - TWIRL_SMALL_SCALE) * q
            val radians = degrees * PI.toFloat() / 180f
            val dx = x - width * 0.5f
            val dy = y - height * 0.5f
            val cosine = cos(radians)
            val sine = sin(radians)
            at(width * 0.5f + (dx * cosine + dy * sine) / scale, height * 0.5f + (-dx * sine + dy * cosine) / scale, out)
        }

        // Turning about the vertical center line: the width is cos(q pi) going edge-on at q = 0.5 for the outgoing picture, -cos(q pi) coming out for the incoming one.
        private fun flipped(x: Float, y: Float, out: FloatArray, incoming: Boolean) {
            val squeeze = if (incoming) -cos(q * PI.toFloat()) else cos(q * PI.toFloat())
            if (squeeze < FLIP_MIN_WIDTH) return out.fill(0f)
            at(width * 0.5f + (x - width * 0.5f) / squeeze, y, out)
        }

        // A burst moves the rows of the slices sideways and reads red a little to the right, blue to the left; outside a burst the picture is plain.
        private fun glitched(x: Float, y: Float, out: FloatArray) {
            if (burst < 0) return at(x, y, out)
            var shift = 0f
            for (i in 0 until GLITCH_SLICES) if (y >= slices[i * GLITCH_SLICE_FLOATS] && y < slices[i * GLITCH_SLICE_FLOATS + 1]) shift = slices[i * GLITCH_SLICE_FLOATS + 2]
            val split = GLITCH_RGB_SPLIT * width
            val moved = x - shift
            at(moved + split, y, tmp)
            val red = tmp[0]
            at(moved - split, y, tmp)
            val blue = tmp[2]
            at(moved, y, out)
            out[0] = red
            out[2] = blue
        }

        // Each cell shows the picture at its middle; a cell under a pixel and a half is the picture itself.
        private fun pixelated(x: Float, y: Float, cell: Float, out: FloatArray) {
            if (cell < 1.5f) return at(x, y, out)
            at((floor(x / cell) + 0.5f) * cell, (floor(y / cell) + 0.5f) * cell, out)
        }
    }

    /** A flip narrower than this share of the width is edge-on: the pixel shows nothing (and the shader never divides by a tiny number). */
    const val FLIP_MIN_WIDTH = 0.002f
}
