package app.memix.debug

import app.memix.core.domain.Logger
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaPaths
import app.memix.core.domain.segmentation.CutoutProgress
import app.memix.core.domain.segmentation.CutoutResult
import app.memix.core.domain.segmentation.RemoveBackgroundUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The debug segmentation hook (P2-06; builds with hand checks): `--es memix.segmentCheck <photo path relative to the files
 * directory> --ei memix.segmentPasses 6` runs the cut-out that many times on one photo, logs each pass (logcat tag [TAG]):
 * what the user waits for, and the decode, model and mask parts. Pass 1 is reported apart, since it can include the model
 * download and the model's first load; the median of the other passes is the warm number the ticket's 1.5 s bar is read from.
 * The masks are written to the throwaway project folder `media/segment-check/` and deleted at the end, unless [keepMasks]
 * (`--ez memix.segmentKeepMasks true`) leaves them there to pull and look at.
 */
class SegmentCheck : KoinComponent {
    private val removeBackground: RemoveBackgroundUseCase by inject()
    private val mediaFiles: MediaFiles by inject()
    private val logger: Logger by inject()

    suspend fun run(sourcePath: String, passes: Int, keepMasks: Boolean = false) {
        val warmTotals = mutableListOf<Long>()
        for (pass in 1..passes) {
            val result = removeBackground(CHECK_PROJECT, sourcePath) { progress ->
                if (progress is CutoutProgress.Preparing) logger.debug(TAG, "pass $pass: downloading the model, ${progress.fraction}")
            }
            val line = describe(result)
            logger.debug(TAG, "pass $pass${if (pass == 1) " (first: may include the model download)" else ""}: $line")
            if (pass > 1 && result is CutoutResult.Done) warmTotals += result.durationMillis
        }
        if (keepMasks) logger.debug(TAG, "masks kept in files/${MediaPaths.projectFolder(CHECK_PROJECT)}/") else mediaFiles.deleteFolder(MediaPaths.projectFolder(CHECK_PROJECT))
        if (warmTotals.isEmpty()) return logger.debug(TAG, "No warm pass finished: nothing to take a median of")
        logger.debug(TAG, "warm median of ${warmTotals.size}: ${warmTotals.sorted()[warmTotals.size / 2]} ms (worst ${warmTotals.max()} ms)")
    }

    private fun describe(result: CutoutResult): String = when (result) {
        is CutoutResult.Done -> "${result.durationMillis} ms in total (decode ${result.timings.decodeMillis}, model ${result.timings.modelMillis}, " +
            "mask ${result.timings.maskMillis}), mask ${result.maskSize.widthPx}x${result.maskSize.heightPx}"
        is CutoutResult.NothingFound -> "nothing found after ${result.durationMillis} ms"
        is CutoutResult.Failed -> "failed (${result.failure}) after ${result.durationMillis} ms"
        CutoutResult.Cancelled -> "the model download was cancelled"
    }

    private companion object {
        const val TAG = "MemixSegmentCheck"
        const val CHECK_PROJECT = "segment-check"
    }
}
