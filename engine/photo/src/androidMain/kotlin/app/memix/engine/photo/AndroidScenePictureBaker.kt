package app.memix.engine.photo

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import app.memix.core.domain.Logger
import app.memix.core.domain.photo.BakedPicture
import app.memix.core.domain.photo.ScenePictureBaker
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * "Make it a video meme" bakes the photo scene through [PhotoSceneRenderer], the renderer the photo export and the debug render hook
 * use, so the picture is the export's picture without its mark (spec docs/ux/specs/P2-10-make-it-a-video-meme.md → Rules 1).
 * The picture is opaque (the canvas background is drawn first), so it is encoded as a JPEG; encoding runs on [ioDispatcher].
 */
internal class AndroidScenePictureBaker(
    private val sceneRenderer: PhotoSceneRenderer,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : ScenePictureBaker {
    override suspend fun bake(project: Project, output: PixelSize): BakedPicture? = withContext(ioDispatcher) {
        try {
            val bitmap = sceneRenderer.render(project, output, watermark = false).asAndroidBitmap()
            try {
                currentCoroutineContext().ensureActive()
                // About a tenth of the raw size is a good guess for a flat meme; the stream grows if it is wrong.
                val stream = ByteArrayOutputStream(output.widthPx * output.heightPx / JPEG_GUESS_DIVISOR)
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) { "The picture couldn't be encoded" }
                BakedPicture(stream.toByteArray(), output)
            } finally {
                // Nothing else holds this bitmap: free its pixels now rather than at the next GC.
                bitmap.recycle()
            }
        } catch (e: UnreadablePictureException) {
            logger.error(TAG, "A picture of the scene can't be read", e)
            null
        } catch (e: IllegalStateException) {
            logger.error(TAG, "The scene couldn't be encoded", e)
            null
        } catch (e: OutOfMemoryError) {
            // A canvas this phone can't hold in memory is a calm failure, not a crash.
            logger.error(TAG, "Not enough memory to draw the scene", e)
            null
        }
    }

    private companion object {
        const val TAG = "MemixMakeVideo"

        /** High enough that baked text and edges show no blocks; the file stays a few hundred KB at 1080. */
        const val JPEG_QUALITY = 95
        const val JPEG_GUESS_DIVISOR = 10
    }
}
