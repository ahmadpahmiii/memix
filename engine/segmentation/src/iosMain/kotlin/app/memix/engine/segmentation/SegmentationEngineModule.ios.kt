package app.memix.engine.segmentation

import app.memix.core.domain.segmentation.ModelPreparation
import app.memix.core.domain.segmentation.ModelState
import app.memix.core.domain.segmentation.SegmentationFailure
import app.memix.core.domain.segmentation.SegmentationOutcome
import app.memix.core.domain.segmentation.Segmenter
import org.koin.dsl.module

actual val segmentationEngineModule = module {
    single<Segmenter> { UnsupportedSegmenter }
}

// TODO(P7-07): VNGenerateForegroundInstanceMaskRequest (iOS 17+), the same mask contract as the Android segmenter.
private object UnsupportedSegmenter : Segmenter {
    override suspend fun modelState() = ModelState.Unavailable(SegmentationFailure.UNSUPPORTED)

    override suspend fun prepareModel(onProgress: (Float?) -> Unit) = ModelPreparation.Failed(SegmentationFailure.UNSUPPORTED)

    override suspend fun segmentImage(sourcePath: String) = SegmentationOutcome.Failed(SegmentationFailure.UNSUPPORTED)
}
