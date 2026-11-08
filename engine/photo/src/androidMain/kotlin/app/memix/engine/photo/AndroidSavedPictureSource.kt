package app.memix.engine.photo

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.memix.core.domain.Logger
import app.memix.core.ui.scene.SavedPictureSource
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Decodes the saved gallery item with [ImageDecoder] at the size the Exported screen shows it (never full size). */
internal class AndroidSavedPictureSource(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : SavedPictureSource {
    override suspend fun decode(location: String, maxLongSidePx: Int): ImageBitmap? = withContext(ioDispatcher) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(location))
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = (maxLongSidePx.toFloat() / max(info.size.width, info.size.height)).coerceAtMost(1f)
                decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
            }.asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            logger.error(TAG, "The saved picture can't be read", e)
            null
        } catch (e: SecurityException) {
            logger.error(TAG, "The saved picture isn't readable", e)
            null
        }
    }

    private companion object {
        const val TAG = "MemixPhotoExport"
    }
}
