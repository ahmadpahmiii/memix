package app.memix.engine.photo

import android.graphics.ImageDecoder
import androidx.compose.ui.graphics.asImageBitmap
import app.memix.core.domain.Logger
import app.memix.core.model.project.PixelSize
import app.memix.core.ui.scene.SceneBitmap
import app.memix.core.ui.scene.SceneImageSource
import app.memix.core.ui.scene.SceneImageUse
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Decodes the app's copy of a picture with the platform's `ImageDecoder`: EXIF orientation applied, scaled while decoding
 * (so a 12 MP photo never exists in memory at full size for a layer drawn at screen size), the first frame of a GIF or
 * WebP. Screen bitmaps may live on the GPU; offscreen ones are software bitmaps, which can be drawn into a software canvas.
 */
internal class AndroidSceneImageSource(
    private val filesDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : SceneImageSource {
    override suspend fun decode(path: String, maxLongSidePx: Int, use: SceneImageUse): SceneBitmap? = withContext(ioDispatcher) {
        val file = File(filesDir, path)
        if (!file.isFile) return@withContext null
        try {
            var sourceSize = PixelSize(0, 0)
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                // info.size is the picture as shown (orientation already applied), the size layers are fitted by.
                sourceSize = PixelSize(info.size.width, info.size.height)
                val longSide = max(info.size.width, info.size.height)
                val scale = if (longSide > maxLongSidePx) maxLongSidePx.toFloat() / longSide else 1f
                decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
                if (use == SceneImageUse.OFFSCREEN) decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            SceneBitmap(bitmap.asImageBitmap(), sourceSize)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            logger.error(TAG, "Couldn't decode a layer's picture", e)
            null
        } catch (e: ImageDecoder.DecodeException) {
            logger.error(TAG, "Couldn't decode a layer's picture", e)
            null
        } catch (e: OutOfMemoryError) {
            // Too big for this phone at this size: the layer isn't drawn, which the row says (File missing).
            logger.error(TAG, "Out of memory decoding a layer's picture", e)
            null
        }
    }

    private companion object {
        const val TAG = "MemixPhotoEngine"
    }
}
