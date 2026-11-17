package app.memix.debug

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The 12 x 12 grid picture of P4-03b's hand checks (`mask-grid.png`, spec docs/ux/specs/P4-03b-shape-masks-and-blend.md → Reference
 * renders), drawn here so no file is involved: 144 cells whose hue runs along the columns and whose lightness alternates down the rows,
 * 2 px dark lines between them, the top-left cell white and the bottom-right cell black, so a window cut out of it can be placed by eye
 * (which cells show, and which way up). Pure arithmetic over a pixel array: the debug build writes it as a PNG and the desktop harness
 * draws the reference renders from the same code.
 */
internal object MaskCheckFixture {
    const val SIZE_PX = 1080
    const val CELLS = 12

    /** The picture, [size] x [size] pixels, as opaque 0xAARRGGBB. */
    fun render(size: Int = SIZE_PX): IntArray {
        val pixels = IntArray(size * size)
        val cell = size.toFloat() / CELLS
        val line = max(1f, size * LINE_SHARE)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val column = min(CELLS - 1, (x / cell).toInt())
                val row = min(CELLS - 1, (y / cell).toInt())
                val onLine = (x % cell) < line / 2 || (x % cell) > cell - line / 2 || (y % cell) < line / 2 || (y % cell) > cell - line / 2
                pixels[y * size + x] = if (onLine) LINE_COLOR else cellColor(row, column)
            }
        }
        return pixels
    }

    private fun cellColor(row: Int, column: Int): Int = when {
        row == 0 && column == 0 -> WHITE
        row == CELLS - 1 && column == CELLS - 1 -> BLACK
        else -> hsv(column * HUE_STEP, SATURATION, if (row % 2 == 0) LIGHT else DARK)
    }

    // The standard HSV to RGB, hue in degrees.
    private fun hsv(hue: Float, saturation: Float, value: Float): Int {
        val chroma = value * saturation
        val x = chroma * (1f - abs((hue / 60f) % 2f - 1f))
        val m = value - chroma
        val (r, g, b) = when ((hue / 60f).toInt()) {
            0 -> Triple(chroma, x, 0f)
            1 -> Triple(x, chroma, 0f)
            2 -> Triple(0f, chroma, x)
            3 -> Triple(0f, x, chroma)
            4 -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
        return pack(r + m, g + m, b + m)
    }

    private fun pack(r: Float, g: Float, b: Float): Int =
        (0xFF shl 24) or (toByte(r) shl 16) or (toByte(g) shl 8) or toByte(b)

    private fun toByte(value: Float): Int = min(255, max(0, (value * 255f + 0.5f).toInt()))

    private const val LINE_SHARE = 0.002f
    private const val HUE_STEP = 30f
    private const val SATURATION = 0.85f
    private const val LIGHT = 0.95f
    private const val DARK = 0.6f
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val LINE_COLOR = 0xFF111111.toInt()
}
