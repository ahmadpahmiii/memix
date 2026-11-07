package app.memix.engine.segmentation

import app.memix.core.domain.Logger
import app.memix.core.domain.segmentation.CutoutMask
import app.memix.core.domain.segmentation.ModelPreparation
import app.memix.core.domain.segmentation.ModelState
import app.memix.core.domain.segmentation.SegmentationFailure
import app.memix.core.domain.segmentation.SegmentationLimits
import app.memix.core.domain.segmentation.SegmentationOutcome
import app.memix.core.domain.segmentation.SegmentationTimings
import app.memix.core.domain.segmentation.Segmenter
import app.memix.core.model.project.PixelSize
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [Segmenter] on ML Kit Subject Segmentation (beta). One photo at a time: two at once would hold two decoded pictures
 * and two masks for no gain. The picture is decoded at [SegmentationLimits.INPUT_LONG_SIDE_PX], the mask is the model's
 * confidence at that size, written as a grayscale PNG.
 */
internal class MlKitSegmenter(
    private val clients: SubjectSegmenterClients,
    private val installer: SubjectModelInstaller,
    private val filesDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val cpuDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : Segmenter {
    private val onePhotoAtATime = Mutex()

    override suspend fun modelState(): ModelState = clients.use { installer.state(it) }

    override suspend fun prepareModel(onProgress: (Float?) -> Unit): ModelPreparation =
        clients.use { installer.install(it, onProgress) }

    override suspend fun segmentImage(sourcePath: String): SegmentationOutcome = onePhotoAtATime.withLock {
        withTimeoutOrNull(SegmentationLimits.SLOW_AFTER_MILLIS) { segment(sourcePath) }
            ?: SegmentationOutcome.Failed(SegmentationFailure.SLOW)
    }

    private suspend fun segment(sourcePath: String): SegmentationOutcome {
        val file = File(filesDir, sourcePath)
        if (!file.isFile) return SegmentationOutcome.Failed(SegmentationFailure.SOURCE_UNREADABLE)
        return try {
            val started = TimeSource.Monotonic.markNow()
            // Not recycled: when a timeout abandons the model call, native code may still be reading it.
            val picture = withContext(ioDispatcher) { MaskImages.decode(file, SegmentationLimits.INPUT_LONG_SIDE_PX) }
            val decoded = started.elapsedNow()
            val result = clients.use { it.process(InputImage.fromBitmap(picture, 0)).await() }
            val modelled = started.elapsedNow()
            val confidence = result.foregroundConfidenceMask ?: return SegmentationOutcome.Failed(SegmentationFailure.UNEXPECTED)
            val context = currentCoroutineContext()
            val mask = withContext(cpuDispatcher) { MaskImages.encodeMask(confidence, picture.width, picture.height) { context.ensureActive() } }
            val timings = SegmentationTimings(
                decodeMillis = decoded.inWholeMilliseconds,
                modelMillis = (modelled - decoded).inWholeMilliseconds,
                maskMillis = (started.elapsedNow() - modelled).inWholeMilliseconds,
            )
            logger.debug(TAG, "Segmented ${picture.width}x${picture.height}: decode ${timings.decodeMillis} ms, model ${timings.modelMillis} ms, mask ${timings.maskMillis} ms")
            if (mask.foregroundShare < SegmentationLimits.NOTHING_FOUND_BELOW_COVERAGE) {
                SegmentationOutcome.NothingFound(timings)
            } else {
                SegmentationOutcome.Found(CutoutMask(mask.png, PixelSize(picture.width, picture.height)), timings)
            }
        } catch (e: CancellationException) {
            // `await()` also throws this when ML Kit itself cancels the task (for example its client was closed). That is
            // a failure, not our caller leaving: rethrowing it would end the caller's coroutine without a result.
            currentCoroutineContext().ensureActive()
            logger.error(TAG, "ML Kit cancelled the task", e)
            SegmentationOutcome.Failed(SegmentationFailure.UNEXPECTED)
        } catch (e: IOException) {
            logger.error(TAG, "Couldn't decode the picture", e)
            SegmentationOutcome.Failed(SegmentationFailure.SOURCE_UNREADABLE)
        } catch (e: OutOfMemoryError) {
            logger.error(TAG, "Out of memory segmenting a picture", e)
            SegmentationOutcome.Failed(SegmentationFailure.OUT_OF_MEMORY)
        } catch (e: MlKitException) {
            logger.error(TAG, "ML Kit failed with code ${e.errorCode}", e)
            SegmentationOutcome.Failed(if (e.errorCode == MlKitException.UNAVAILABLE) SegmentationFailure.MODEL_UNAVAILABLE else SegmentationFailure.UNEXPECTED)
        } catch (e: Exception) {
            logger.error(TAG, "Segmentation failed", e)
            SegmentationOutcome.Failed(SegmentationFailure.UNEXPECTED)
        }
    }

    private companion object {
        const val TAG = "MemixSegmenter"
    }
}
