package app.memix.core.domain.segmentation

import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaPaths
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.PixelSize
import kotlin.time.TimeSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Makes the cut-out mask of a photo: checks the model, downloads it when it is missing, segments, and saves the mask as
 * a new file in the project's media folder (never over an existing file). P2-07 stores the path in the layer; a mask
 * that is never stored is removed with the other unused copies at the next app start.
 */
class RemoveBackgroundUseCase(
    private val segmenter: Segmenter,
    private val mediaFiles: MediaFiles,
) {
    /**
     * Runs the whole job for the picture at [sourcePath]. [onProgress] says what the screen should show, on any thread:
     * [CutoutProgress.Preparing] (and once [CutoutProgress.TakingLonger]) while the model downloads, then
     * [CutoutProgress.Working]. Cancelling stops it; nothing is saved then.
     */
    suspend operator fun invoke(projectId: String, sourcePath: String, onProgress: (CutoutProgress) -> Unit = {}): CutoutResult {
        val started = TimeSource.Monotonic.markNow()
        fun elapsedMillis() = started.elapsedNow().inWholeMilliseconds

        modelProblem(onProgress)?.let { return it.toResult(elapsedMillis()) }
        onProgress(CutoutProgress.Working)
        return when (val outcome = segmenter.segmentImage(sourcePath)) {
            is SegmentationOutcome.Failed -> CutoutResult.Failed(outcome.failure, elapsedMillis())
            is SegmentationOutcome.NothingFound -> CutoutResult.NothingFound(elapsedMillis())
            is SegmentationOutcome.Found -> saveMask(projectId, outcome, ::elapsedMillis)
        }
    }

    /** Null when the model is ready (after a download if it was missing); otherwise why not. */
    private suspend fun modelProblem(onProgress: (CutoutProgress) -> Unit): ModelProblem? =
        when (val state = segmenter.modelState()) {
            ModelState.Ready -> null
            is ModelState.Unavailable -> ModelProblem.Failure(state.failure)
            ModelState.NotInstalled -> when (val preparation = downloadModel(onProgress)) {
                ModelPreparation.Ready -> null
                ModelPreparation.Cancelled -> ModelProblem.Cancelled
                is ModelPreparation.Failed -> ModelProblem.Failure(preparation.failure)
            }
        }

    private suspend fun downloadModel(onProgress: (CutoutProgress) -> Unit): ModelPreparation = coroutineScope {
        onProgress(CutoutProgress.Preparing(fraction = null))
        val slowTimer = launch {
            delay(SegmentationLimits.SLOW_AFTER_MILLIS)
            onProgress(CutoutProgress.TakingLonger)
        }
        try {
            segmenter.prepareModel { fraction -> onProgress(CutoutProgress.Preparing(fraction)) }
        } finally {
            slowTimer.cancel()
        }
    }

    private suspend fun saveMask(projectId: String, found: SegmentationOutcome.Found, elapsedMillis: () -> Long): CutoutResult {
        val path = MediaPaths.newCopy(projectId, MediaKind.IMAGE, "png")
        return when (mediaFiles.write(found.mask.pngBytes, path)) {
            CopyResult.COPIED -> CutoutResult.Done(path, found.mask.size, found.timings, elapsedMillis())
            CopyResult.STORAGE_FULL -> CutoutResult.Failed(SegmentationFailure.STORAGE_FULL, elapsedMillis())
            CopyResult.UNREADABLE -> CutoutResult.Failed(SegmentationFailure.UNEXPECTED, elapsedMillis())
        }
    }

    private sealed interface ModelProblem {
        data class Failure(val failure: SegmentationFailure) : ModelProblem
        data object Cancelled : ModelProblem

        fun toResult(durationMillis: Long): CutoutResult = when (this) {
            is Failure -> CutoutResult.Failed(failure, durationMillis)
            Cancelled -> CutoutResult.Cancelled
        }
    }
}

/** What the cut-out screen shows while [RemoveBackgroundUseCase] runs. */
sealed interface CutoutProgress {
    /** The model is downloading (first use). [fraction] is 0 to 1, or null when the download gives no size. */
    data class Preparing(val fraction: Float?) : CutoutProgress

    /** Sent once, 30 seconds into the download: offer a retry. [Preparing] updates may follow. */
    data object TakingLonger : CutoutProgress

    /** The model is ready and the photo is being segmented (usually under 1.5 s). */
    data object Working : CutoutProgress
}

sealed interface CutoutResult {
    /** The mask is saved at [maskPath] (relative to the files directory), [maskSize] pixels. [durationMillis] counts everything, a model download included. */
    data class Done(
        val maskPath: String,
        val maskSize: PixelSize,
        val timings: SegmentationTimings,
        val durationMillis: Long,
    ) : CutoutResult

    data class NothingFound(val durationMillis: Long) : CutoutResult

    data class Failed(val failure: SegmentationFailure, val durationMillis: Long) : CutoutResult

    /** Play services cancelled the model download. (Cancelling the coroutine is not a result: it throws, as usual.) */
    data object Cancelled : CutoutResult
}
