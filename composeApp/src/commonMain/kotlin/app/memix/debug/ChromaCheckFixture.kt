package app.memix.debug

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The synthetic green screen picture of P4-04's hand checks (`chroma-check`, spec docs/ux/specs/P4-04-green-screen.md → Reference
 * renders), original and drawn here, so no footage is involved: a flat `#00B140` backdrop lit unevenly (a brightness gradient of +-20%
 * across it) with per-pixel noise (sigma 4/255), and a subject of a skin-tone ellipse (`#E0A98C`), a white rectangle (a shirt), 30
 * one-pixel anti-aliased dark strands (hair) and a green rim on its edge (G + 12/255: the spill the key must take out).
 * Pure arithmetic over a pixel array, so the debug build writes it as a PNG and the desktop harness draws the reference renders from
 * the same code. [backdrop] swaps the backdrop's color (the blue and off-green versions of the references).
 */
internal object ChromaCheckFixture {
    const val WIDTH_PX = 1920
    const val HEIGHT_PX = 1080

    const val GREEN = 0xFF00B140.toInt()
    const val BLUE = 0xFF0047BB.toInt()
    const val OFF_GREEN = 0xFF6BB536.toInt()
    private const val SKIN = 0xFFE0A98C.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val STRAND = 0xFF1E1410.toInt()

    /** What a pixel of the picture is, for the checks that measure a key against the truth. */
    enum class Part { BACKDROP, SUBJECT_INSIDE, STRAND_CENTER, OUTSIDE_BAND, EDGE }

    class Picture(val width: Int, val height: Int, val pixels: IntArray, val parts: Array<Part>)

    /** The picture [width] x [height] (the shapes scale with the height), noise from [seed], with the backdrop of color [backdrop]. */
    fun render(width: Int = WIDTH_PX, height: Int = HEIGHT_PX, seed: Int = 1, backdrop: Int = GREEN): Picture {
        val shapes = Shapes(width, height)
        val pixels = IntArray(width * height)
        val parts = Array(width * height) { Part.BACKDROP }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val body = shapes.bodyDistance(x + HALF, y + HALF)
                val strand = shapes.strandDistance(x + HALF, y + HALF)
                val back = backdropAt(x, y, width, seed, backdrop)
                val coverage = (HALF - body).coerceIn(0f, 1f)
                val strandCoverage = if (body > 0f) (1f - strand).coerceIn(0f, 1f) else 0f
                val subject = shapes.subjectColorAt(x + HALF, y + HALF, body)
                pixels[index] = blend(blend(back, subject, coverage), STRAND, strandCoverage)
                parts[index] = when {
                    body <= -INSIDE_PX -> Part.SUBJECT_INSIDE
                    strand <= STRAND_CENTER_PX && body > OUTSIDE_PX -> Part.STRAND_CENTER
                    body in -INSIDE_PX..0f -> Part.EDGE
                    // Near a strand the picture is neither backdrop nor band: it is hair.
                    body > 0f && strand <= OUTSIDE_PX -> Part.EDGE
                    body > 0f && body <= OUTSIDE_PX -> Part.OUTSIDE_BAND
                    else -> Part.BACKDROP
                }
            }
        }
        return Picture(width, height, pixels, parts)
    }

    // The backdrop is lit +-20% across its width, with sigma-4/255 noise on every channel.
    private fun backdropAt(x: Int, y: Int, width: Int, seed: Int, color: Int): Int {
        val light = 1f + GRADIENT * (2f * x / (width - 1) - 1f)
        return packOpaque(
            channelOf(color, RED_SHIFT) * light + noise(x, y, seed, 0),
            channelOf(color, GREEN_SHIFT) * light + noise(x, y, seed, 1),
            channelOf(color, BLUE_SHIFT) * light + noise(x, y, seed, 2),
        )
    }

    // Four uniforms summed give a bell: (sum - 2) x sqrt(3) has sigma 1.
    private fun noise(x: Int, y: Int, seed: Int, channel: Int): Float {
        var sum = 0f
        for (round in 0 until 4) sum += uniform(x * 73856093 xor y * 19349663 xor seed * 83492791 xor channel * 2654435 xor round * 40503)
        return (sum - 2f) * SQRT3 * NOISE_SIGMA
    }

    private fun uniform(input: Int): Float {
        var h = input * -1640531535
        h = h xor (h ushr 15)
        h *= 0x2c1b3c6d
        h = h xor (h ushr 12)
        h *= 0x297a2d39
        h = h xor (h ushr 15)
        return (h ushr 8) / 16_777_216f
    }

    /** The subject's shapes in pixels; every distance is signed (negative inside) and in pixels. */
    private class Shapes(width: Int, private val height: Int) {
        private val centerX = width / 2f
        private val faceY = height * 0.38f
        private val faceRx = height * 0.17f
        private val faceRy = height * 0.22f
        private val shirtLeft = centerX - height * 0.22f
        private val shirtRight = centerX + height * 0.22f
        private val shirtTop = height * 0.62f
        private val shirtBottom = height * 0.97f
        private val strands: List<FloatArray> = (0 until STRAND_COUNT).map { index ->
            // From the top of the head, fanned out, starting just inside the face so each joins it.
            val angle = (-160.0 + 140.0 * index / (STRAND_COUNT - 1)) * PI / 180.0
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            val startX = centerX + dx * (faceRx - 2f)
            val startY = faceY + dy * (faceRy - 2f)
            floatArrayOf(startX, startY, startX + dx * height * STRAND_LENGTH, startY + dy * height * STRAND_LENGTH)
        }

        fun bodyDistance(x: Float, y: Float): Float = min(faceDistance(x, y), shirtDistance(x, y))

        private fun faceDistance(x: Float, y: Float): Float {
            val nx = (x - centerX) / faceRx
            val ny = (y - faceY) / faceRy
            return (sqrt(nx * nx + ny * ny) - 1f) * min(faceRx, faceRy)
        }

        private fun shirtDistance(x: Float, y: Float): Float {
            val dx = max(shirtLeft - x, x - shirtRight)
            val dy = max(shirtTop - y, y - shirtBottom)
            return if (dx > 0f || dy > 0f) hypot(max(dx, 0f), max(dy, 0f)) else max(dx, dy)
        }

        fun strandDistance(x: Float, y: Float): Float {
            var nearest = Float.MAX_VALUE
            for (s in strands) nearest = min(nearest, segmentDistance(x, y, s[0], s[1], s[2], s[3]))
            return nearest
        }

        private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val abx = bx - ax
            val aby = by - ay
            val t = (((px - ax) * abx + (py - ay) * aby) / (abx * abx + aby * aby)).coerceIn(0f, 1f)
            return hypot(px - (ax + t * abx), py - (ay + t * aby))
        }

        // The face is skin and the shirt white; within RIM_PX of the edge the green channel is raised by 12/255 (the spill).
        fun subjectColorAt(x: Float, y: Float, body: Float): Int {
            val base = if (faceDistance(x, y) <= shirtDistance(x, y)) SKIN else WHITE
            if (body < -RIM_PX) return base
            return packOpaque(channelOf(base, RED_SHIFT), channelOf(base, GREEN_SHIFT) + RIM_GREEN, channelOf(base, BLUE_SHIFT))
        }
    }

    private fun blend(below: Int, above: Int, amount: Float): Int {
        if (amount <= 0f) return below
        val keep = 1f - amount
        return packOpaque(
            channelOf(below, RED_SHIFT) * keep + channelOf(above, RED_SHIFT) * amount,
            channelOf(below, GREEN_SHIFT) * keep + channelOf(above, GREEN_SHIFT) * amount,
            channelOf(below, BLUE_SHIFT) * keep + channelOf(above, BLUE_SHIFT) * amount,
        )
    }

    private fun channelOf(argb: Int, shift: Int): Float = ((argb shr shift) and BYTE).toFloat()

    private fun packOpaque(r: Float, g: Float, b: Float): Int =
        (0xFF shl ALPHA_SHIFT) or (toByte(r) shl RED_SHIFT) or (toByte(g) shl GREEN_SHIFT) or toByte(b)

    private fun toByte(value: Float): Int = min(255, max(0, (value + HALF).toInt()))

    private const val HALF = 0.5f
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BLUE_SHIFT = 0
    private const val BYTE = 0xFF
    private const val GRADIENT = 0.2f
    private const val NOISE_SIGMA = 4f
    private const val SQRT3 = 1.7320508f
    private const val STRAND_COUNT = 30
    private const val STRAND_LENGTH = 0.09f
    private const val RIM_PX = 3f
    private const val RIM_GREEN = 12f
    private const val INSIDE_PX = 3f
    private const val OUTSIDE_PX = 3f

    // A pixel whose center is this close to a strand is covered by it for 80% or more: the strand's own center line.
    private const val STRAND_CENTER_PX = 0.2f
}
