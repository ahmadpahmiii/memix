package app.memix.engine.video

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.video.ExportSettings
import app.memix.core.domain.video.ExportedVideo
import app.memix.core.domain.video.VideoEngine
import app.memix.core.model.project.Project
import org.koin.dsl.module

actual val videoEngineModule = module {
    single<VideoEngine> { IosVideoEngine() }
}

// TODO(P7-05): export with AVMutableComposition and AVAssetWriter, built from the same CompositionPlan as Android.
private class IosVideoEngine : VideoEngine {
    override suspend fun export(
        project: Project,
        settings: ExportSettings,
        onProgress: (Float) -> Unit,
    ): Outcome<ExportedVideo> = Outcome.Failure(AppError.Unexpected(UnsupportedOperationException("Video export on iOS arrives with P7-05")))
}
