package app.memix.debug

import app.memix.core.domain.segmentation.ModelPreparation
import app.memix.core.domain.segmentation.ModelState
import app.memix.core.domain.segmentation.SegmentationFailure
import app.memix.core.domain.segmentation.SegmentationOutcome
import app.memix.core.domain.segmentation.SegmentationTimings
import app.memix.core.domain.segmentation.Segmenter
import kotlinx.coroutines.awaitCancellation

/**
 * The failure hooks of the cut-out screen (spec docs/ux/specs/P2-07-background-removal.md → Requests; builds with hand checks):
 * `--es memix.bgRemoveFail <kind>` makes every segmentation end the way a phone would let it, so QA can reach each status card:
 * `unavailable` (no Play services or the model can't be had), `offline` (first use, no network), `memory` (out of memory),
 * `none` (no subject found) and `slow` (a download that never ends: the card offers Try again after 30 s). [clear] with
 * `--es memix.bgRemoveFail off` goes back to the real segmenter.
 */
object BgRemoveFailure {
    @kotlin.concurrent.Volatile
    private var kind: String? = null

    fun arm(kind: String) {
        this.kind = kind.takeUnless { it == "off" }
    }

    internal fun around(real: Segmenter): Segmenter = object : Segmenter {
        override suspend fun modelState(): ModelState = when (kind) {
            "unavailable" -> ModelState.Unavailable(SegmentationFailure.MODEL_UNAVAILABLE)
            "offline", "slow" -> ModelState.NotInstalled
            "memory", "none" -> ModelState.Ready
            else -> real.modelState()
        }

        override suspend fun prepareModel(onProgress: (Float?) -> Unit): ModelPreparation = when (kind) {
            "offline" -> ModelPreparation.Failed(SegmentationFailure.OFFLINE)
            "slow" -> {
                onProgress(SLOW_PROGRESS)
                awaitCancellation()
            }
            else -> real.prepareModel(onProgress)
        }

        override suspend fun segmentImage(sourcePath: String): SegmentationOutcome = when (kind) {
            "memory" -> SegmentationOutcome.Failed(SegmentationFailure.OUT_OF_MEMORY)
            "none" -> SegmentationOutcome.NothingFound(SegmentationTimings(0, 0, 0))
            else -> real.segmentImage(sourcePath)
        }
    }

    private const val SLOW_PROGRESS = 0.4f
}
