package app.memix.core.domain.image

import kotlin.math.floor

/** The pixel helpers the CPU twins of the video effects share (`EffectMath`, `GlitchMath`, `BlurMath`, `RetroMath`): ARGB `Int`s, channels as 0 to 1 floats. */
internal object EffectPixels {
    const val OPAQUE = 0xFF000000.toInt()

    // The picture at the continuous point (x, y) (a pixel's center at +0.5) by bilinear filtering, the edge repeating: the sampler's linear filter and clamp-to-edge.
    fun bilinear(source: IntArray, width: Int, height: Int, x: Float, y: Float, out: FloatArray) {
        val fx = x - 0.5f
        val fy = y - 0.5f
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val tx = fx - x0
        val ty = fy - y0
        val a = source[y0.coerceIn(0, height - 1) * width + x0.coerceIn(0, width - 1)]
        val b = source[y0.coerceIn(0, height - 1) * width + (x0 + 1).coerceIn(0, width - 1)]
        val c = source[(y0 + 1).coerceIn(0, height - 1) * width + x0.coerceIn(0, width - 1)]
        val d = source[(y0 + 1).coerceIn(0, height - 1) * width + (x0 + 1).coerceIn(0, width - 1)]
        out[0] = mix(mix(red(a), red(b), tx), mix(red(c), red(d), tx), ty)
        out[1] = mix(mix(green(a), green(b), tx), mix(green(c), green(d), tx), ty)
        out[2] = mix(mix(blue(a), blue(b), tx), mix(blue(c), blue(d), tx), ty)
    }

    fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun red(p: Int): Float = ((p shr 16) and 0xFF) / 255f
    fun green(p: Int): Float = ((p shr 8) and 0xFF) / 255f
    fun blue(p: Int): Float = (p and 0xFF) / 255f

    fun pack(alphaFrom: Int, r: Float, g: Float, b: Float): Int = (alphaFrom and OPAQUE) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)

    fun to8(v: Float): Int = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

    fun rgbOf(rgb: Int): FloatArray = floatArrayOf(((rgb shr 16) and 0xFF) / 255f, ((rgb shr 8) and 0xFF) / 255f, (rgb and 0xFF) / 255f)
}
