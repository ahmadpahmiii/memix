package app.memix.core.domain.image

/**
 * A 33 x 33 x 33 color lookup table in sRGB gamma space, the shared artifact of the filters (spec docs/ux/specs/P2-04-image-tools.md;
 * format in core/ui/lut-tools/README.md): [bytes] holds R, G, B per lattice point, red varying fastest, then green, then blue.
 * Sampled trilinearly, so a color between lattice points mixes its 8 neighbours. Pure and thread-safe.
 */
class Lut(private val bytes: ByteArray) {
    init {
        require(bytes.size == SIZE * SIZE * SIZE * CHANNELS) { "A filter LUT is ${SIZE * SIZE * SIZE * CHANNELS} bytes, not ${bytes.size}" }
    }

    /** The color of lattice point ([r], [g], [b]) in 0 to 255, channel [channel] (0 red, 1 green, 2 blue). */
    fun at(r: Int, g: Int, b: Int, channel: Int): Int = bytes[((b * SIZE + g) * SIZE + r) * CHANNELS + channel].toInt() and BYTE_MASK

    /** Writes the filtered color of ([r], [g], [b]), each 0 to 1, into [out] (0 to 1 each). */
    fun sample(r: Float, g: Float, b: Float, out: FloatArray) {
        val x = r.coerceIn(0f, 1f) * LAST
        val y = g.coerceIn(0f, 1f) * LAST
        val z = b.coerceIn(0f, 1f) * LAST
        val x0 = minOf(x.toInt(), LAST - 1)
        val y0 = minOf(y.toInt(), LAST - 1)
        val z0 = minOf(z.toInt(), LAST - 1)
        val fx = x - x0
        val fy = y - y0
        val fz = z - z0
        for (channel in 0 until CHANNELS) {
            val c00 = lerp(at(x0, y0, z0, channel), at(x0 + 1, y0, z0, channel), fx)
            val c10 = lerp(at(x0, y0 + 1, z0, channel), at(x0 + 1, y0 + 1, z0, channel), fx)
            val c01 = lerp(at(x0, y0, z0 + 1, channel), at(x0 + 1, y0, z0 + 1, channel), fx)
            val c11 = lerp(at(x0, y0 + 1, z0 + 1, channel), at(x0 + 1, y0 + 1, z0 + 1, channel), fx)
            out[channel] = lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz) / BYTE_MAX
        }
    }

    private fun lerp(a: Int, b: Int, t: Float): Float = a + (b - a) * t

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    companion object {
        const val SIZE = 33
        private const val LAST = SIZE - 1
        private const val CHANNELS = 3
        private const val BYTE_MASK = 0xFF
        private const val BYTE_MAX = 255f
    }
}
