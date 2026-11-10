package app.memix.engine.photo

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.memix.core.domain.Logger
import app.memix.core.domain.image.ImagePipeline
import app.memix.core.domain.image.Lut
import app.memix.core.model.project.ImageEdit
import app.memix.core.ui.scene.ImageToolRenderer
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import memix.core.ui.generated.resources.Res

/**
 * The image tools' color pass on Android (spec docs/ux/specs/P2-04-image-tools.md): [ImagePipeline] on the picture's pixels, a CPU pass
 * on a background dispatcher over the bitmap at its drawn size, the same code on every Android version (so API 29 and a current phone
 * look alike). The filters' LUT files come from the shared resources and are parsed once each.
 */
internal class AndroidImageToolRenderer(
    private val cpuDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : ImageToolRenderer {
    private val luts = ConcurrentHashMap<String, Lut>()

    override val isSupported = true

    override suspend fun process(picture: ImageBitmap, edit: ImageEdit): ImageBitmap? {
        val lut = edit.filter?.let { lutOf(it.id) }
        return withContext(cpuDispatcher) {
            val context = currentCoroutineContext()
            try {
                val source = picture.asAndroidBitmap()
                val width = source.width
                val height = source.height
                val pixels = IntArray(width * height)
                source.getPixels(pixels, 0, width, 0, 0, width, height)
                ImagePipeline.process(pixels, width, height, edit, lut) { context.ensureActive() }
                Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                // A bitmap that can't be read back (a hardware one): the caller decodes a software copy, so this is a bug to see.
                logger.error(TAG, "Couldn't read a picture's pixels", e)
                null
            } catch (e: OutOfMemoryError) {
                logger.error(TAG, "Out of memory processing a picture", e)
                null
            }
        }
    }

    override suspend fun bitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap? = withContext(cpuDispatcher) {
        try {
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
        } catch (e: OutOfMemoryError) {
            logger.error(TAG, "Out of memory making a cut-out picture", e)
            null
        }
    }

    override suspend fun encodePng(image: ImageBitmap): ByteArray? = withContext(cpuDispatcher) {
        try {
            java.io.ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }.toByteArray()
        } catch (e: OutOfMemoryError) {
            logger.error(TAG, "Out of memory encoding a sticker", e)
            null
        }
    }

    private suspend fun lutOf(filterId: String): Lut? {
        luts[filterId]?.let { return it }
        return try {
            Lut(Res.readBytes("files/filters/$filterId.lut")).also { luts[filterId] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An id this build doesn't have (a newer build's draft): the filter is left out.
            logger.error(TAG, "No LUT for filter '$filterId'", e)
            null
        }
    }

    private companion object {
        const val TAG = "MemixPhotoEngine"
        const val PNG_QUALITY = 100
    }
}
