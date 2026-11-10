package app.memix.engine.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.os.Build
import app.memix.core.domain.Logger
import app.memix.core.domain.stickerpack.StickerEncoding
import app.memix.core.domain.stickerpack.StickerPackEncoder
import app.memix.core.model.stickerpack.StickerPackLimits
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * WhatsApp's sticker files (spec docs/ux/specs/P2-09-whatsapp-sticker-pack.md -> Rules): the picture is decoded at the size it will
 * have, drawn centered on a transparent square, and encoded as lossy WebP with alpha. The quality is searched for the highest value
 * whose file is within the limit; a picture that is over it even at [MIN_QUALITY] is reported as too detailed.
 *
 * Work runs on [workDispatcher] (CPU), one sticker at a time, so peak memory is one decoded picture and one 512 x 512 bitmap.
 */
internal class AndroidStickerPackEncoder(
    private val filesDir: File,
    private val workDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : StickerPackEncoder {
    override suspend fun encodeSticker(sourcePath: String): StickerEncoding = withContext(workDispatcher) {
        val square = fittedSquare(sourcePath, StickerPackLimits.STICKER_SIZE_PX) ?: return@withContext StickerEncoding.Unreadable
        try {
            val webp = bestWebp(square)
            if (webp == null) StickerEncoding.TooDetailed else StickerEncoding.Encoded(webp)
        } catch (e: IOException) {
            logger.error(TAG, "A sticker can't be encoded", e)
            StickerEncoding.Unreadable
        } finally {
            square.recycle()
        }
    }

    override suspend fun encodeTray(sourcePath: String): ByteArray? = withContext(workDispatcher) {
        val square = fittedSquare(sourcePath, StickerPackLimits.TRAY_SIZE_PX) ?: return@withContext null
        try {
            val out = ByteArrayOutputStream()
            if (!square.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) throw IOException("PNG encoding failed")
            // A 96 x 96 picture is 37 KB raw, so this only guards the limit.
            out.toByteArray().takeIf { it.size <= StickerPackLimits.TRAY_MAX_BYTES }
        } catch (e: IOException) {
            logger.error(TAG, "The tray icon can't be encoded", e)
            null
        } finally {
            square.recycle()
        }
    }

    // The file's picture scaled so its long side is [sizePx] (smaller pictures grow), centered on a transparent [sizePx] square.
    private fun fittedSquare(path: String, sizePx: Int): Bitmap? {
        val file = File(filesDir, path)
        if (!file.isFile) return null
        return try {
            val picture = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val scale = sizePx.toFloat() / max(info.size.width, info.size.height)
                decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
                // Drawn into a software canvas below, which a GPU-backed bitmap can't be.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val square = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            Canvas(square).drawBitmap(picture, (sizePx - picture.width) / 2f, (sizePx - picture.height) / 2f, null)
            picture.recycle()
            square
        } catch (e: IOException) {
            logger.error(TAG, "A sticker can't be decoded", e)
            null
        } catch (e: ImageDecoder.DecodeException) {
            logger.error(TAG, "A sticker can't be decoded", e)
            null
        } catch (e: OutOfMemoryError) {
            logger.error(TAG, "Out of memory decoding a sticker", e)
            null
        }
    }

    // The highest quality whose file fits, by bisection (file size grows with quality): about 6 encodes of a 512 square, none when the best quality fits.
    private suspend fun bestWebp(square: Bitmap): ByteArray? {
        val limit = StickerPackLimits.STICKER_MAX_BYTES
        webp(square, MAX_QUALITY).takeIf { it.size <= limit }?.let { return it }
        var best = webp(square, MIN_QUALITY).takeIf { it.size <= limit } ?: return null
        var low = MIN_QUALITY
        var high = MAX_QUALITY - 1
        while (low < high) {
            currentCoroutineContext().ensureActive()
            val middle = (low + high + 1) / 2
            val candidate = webp(square, middle)
            if (candidate.size <= limit) {
                best = candidate
                low = middle
            } else {
                high = middle - 1
            }
        }
        return best
    }

    private fun webp(square: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        // WEBP_LOSSY is API 30+. Before it, WEBP is lossy below 100 and lossless at 100, and MAX_QUALITY stays below 100.
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        if (!square.compress(format, quality, out)) throw IOException("WebP encoding failed")
        return out.toByteArray()
    }

    private companion object {
        const val TAG = "MemixStickerPacks"

        // About 40 is where a cut-out starts to show blocks round its edge; under it a sticker is reported instead of spoiled.
        const val MIN_QUALITY = 40
        const val MAX_QUALITY = 95
        const val PNG_QUALITY = 100
    }
}
