package app.memix.engine.video

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.video.ExportSettings
import app.memix.core.domain.video.ExportedVideo
import app.memix.core.domain.video.PreviewPlayback
import app.memix.core.domain.video.PreviewSession
import app.memix.core.domain.video.PreviewStatus
import app.memix.core.domain.video.VideoEngine
import app.memix.core.model.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    // TODO(P7-03): AVPlayer over the same CompositionPlan. Until then the editor shows its "preview stopped" state.
    override fun createPreview(project: Project): PreviewSession = UnavailablePreviewSession()
}

private class UnavailablePreviewSession : PreviewSession {
    override val playback: StateFlow<PreviewPlayback> = MutableStateFlow(PreviewPlayback.Preparing.copy(status = PreviewStatus.FAILED))
    override val positionUs: StateFlow<Long> = MutableStateFlow(0L)

    override fun play() = Unit
    override fun pause() = Unit
    override fun seekTo(positionUs: Long) = Unit
    override fun update(project: Project) = Unit
    override fun close() = Unit
}
