package app.memix.engine.video

import android.graphics.Bitmap
import app.memix.core.domain.Logger
import app.memix.core.domain.image.Lut
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import memix.core.ui.generated.resources.Res

/**
 * The filters' color tables as textures the look shader samples (P4-07). The data is the photo editor's: the same `files/filters/<id>.lut`
 * resources, parsed by the same [Lut], so a filter id gives the same colors in both editors. A table is laid out as Media3's own LUT
 * texture is (N wide by N * N tall: x is blue, y is red plane * N + green), which the GPU's linear filtering then samples as the
 * trilinear lookup [Lut.sample] does. Made once per filter and shared by every composition.
 *
 * [tableOf] reads a resource, so it runs only on the composition builder's thread (never main, never the GL thread).
 */
internal class LutTables(private val logger: Logger) {
    private val tables = ConcurrentHashMap<String, Bitmap>()

    /** The table of filter [filterId], or null when this build has no such filter (a newer build's draft): the filter is then left out. */
    fun tableOf(filterId: String): Bitmap? {
        tables[filterId]?.let { return it }
        return try {
            val bytes = runBlocking { Res.readBytes("files/filters/$filterId.lut") }
            tableFrom(Lut(bytes)).also { tables[filterId] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "No LUT for filter '$filterId'", e)
            null
        }
    }

    private fun tableFrom(lut: Lut): Bitmap {
        val size = Lut.SIZE
        val pixels = IntArray(size * size * size)
        for (r in 0 until size) for (g in 0 until size) for (b in 0 until size) {
            pixels[(r * size + g) * size + b] = OPAQUE or (lut.at(r, g, b, 0) shl 16) or (lut.at(r, g, b, 1) shl 8) or lut.at(r, g, b, 2)
        }
        return Bitmap.createBitmap(pixels, size, size * size, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val TAG = "MemixLutTables"
        const val OPAQUE = 0xFF000000.toInt()
    }
}
