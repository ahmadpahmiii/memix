package app.memix.engine.video

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.sound.SoundPlayer
import app.memix.core.domain.text.CaptionBlockSize
import app.memix.core.domain.text.CaptionMeasurer
import app.memix.core.domain.video.ExportCapabilities
import app.memix.core.domain.video.ExportError
import app.memix.core.domain.video.ExportOutcome
import app.memix.core.domain.video.ExportSettings
import app.memix.core.domain.video.PreviewPlayback
import app.memix.core.domain.video.PreviewSession
import app.memix.core.domain.video.PreviewStatus
import app.memix.core.domain.video.ResultPlayer
import app.memix.core.domain.video.Thumbnail
import app.memix.core.domain.video.ThumbnailReader
import app.memix.core.domain.video.VideoEngine
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import kotlin.math.ceil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.dsl.module

actual val videoEngineModule = module {
    single<VideoEngine> { IosVideoEngine() }
    single<CaptionMeasurer> { EstimatedCaptionMeasurer }
}

// TODO(P7-04): measure with Core Text and the bundled fonts, the same rules as Android's AndroidCaptionRenderer. Until
// then an estimate sizes the editor's selection frame; nothing draws captions on iOS.
private object EstimatedCaptionMeasurer : CaptionMeasurer {
    override suspend fun prepare() = Unit

    override fun measure(text: String, style: CaptionStyle, canvasWidthPx: Int, canvasHeightPx: Int): CaptionBlockSize {
        val shortSidePx = minOf(canvasWidthPx, canvasHeightPx).toFloat()
        val fontSizePx = shortSidePx * CaptionCatalog.FONT_SIZE_OF_SHORT_SIDE
        val wrapPx = shortSidePx * CaptionCatalog.WRAP_WIDTH_OF_SHORT_SIDE
        val lines = text.lines()
        val widestPx = lines.maxOf { it.length } * fontSizePx * AVERAGE_GLYPH_WIDTH
        val wrappedLines = lines.sumOf { maxOf(1, ceil(it.length * fontSizePx * AVERAGE_GLYPH_WIDTH / wrapPx).toInt()) }
        return CaptionBlockSize(minOf(widestPx, wrapPx), wrappedLines * fontSizePx * LINE_HEIGHT)
    }

    private const val AVERAGE_GLYPH_WIDTH = 0.5f
    private const val LINE_HEIGHT = 1.2f
}

// TODO(P7-05): export with AVMutableComposition and AVAssetWriter, built from the same CompositionPlan as Android.
private class IosVideoEngine : VideoEngine {
    override suspend fun export(project: Project, settings: ExportSettings, onProgress: (Float) -> Unit): ExportOutcome =
        ExportOutcome.Failed(ExportError.UNKNOWN)

    // TODO(P7-05): AVAssetExportSession presets and VTCopySupportedPropertyDictionary. Until then nothing is possible, and
    // the sheet explains (spec P1-12 → No resolution possible).
    override suspend fun exportCapabilities(canvasSize: PixelSize): ExportCapabilities = ExportCapabilities(emptyMap(), emptySet())

    // TODO(P7-05): Photos placeholders. Nothing is written yet.
    override suspend fun discardUnfinishedExports() = Unit

    // TODO(P7-03): AVPlayer over the same CompositionPlan. Until then the editor shows its "preview stopped" state.
    override fun createPreview(project: Project): PreviewSession = UnavailablePreviewSession()

    // TODO(P7-03): AVAssetImageGenerator frames (UIImage -> JPEG). Until then the timeline's tiles stay empty.
    override fun openThumbnails(): ThumbnailReader = UnavailableThumbnailReader

    // TODO(P7-03): AVAudioPlayer. Until then the sounds sheet's play buttons do nothing.
    override fun openSoundPlayer(): SoundPlayer = SilentSoundPlayer()

    // TODO(P7-05): AVPlayer on the exported file.
    override fun openResultPlayer(location: String): ResultPlayer = SilentResultPlayer()
}

private class SilentResultPlayer : ResultPlayer {
    override val isPlaying: StateFlow<Boolean> = MutableStateFlow(false)

    override fun togglePlayback() = Unit
    override fun pause() = Unit
    override fun close() = Unit
}

private class SilentSoundPlayer : SoundPlayer {
    override val playing: StateFlow<MediaRef?> = MutableStateFlow(null)

    override fun play(sound: MediaRef) = Unit
    override fun stop() = Unit
    override fun close() = Unit
}

private class UnavailablePreviewSession : PreviewSession {
    override val playback: StateFlow<PreviewPlayback> = MutableStateFlow(PreviewPlayback.Preparing.copy(status = PreviewStatus.FAILED))
    override val positionUs: StateFlow<Long> = MutableStateFlow(0L)

    override fun play() = Unit
    override fun pause() = Unit
    override fun seekTo(positionUs: Long) = Unit
    override fun setScrubbing(scrubbing: Boolean) = Unit
    override fun update(project: Project) = Unit
    override fun showTrimFrame(clip: MediaClip, sourceTimeUs: Long) = Unit
    override fun endTrimFrames() = Unit
    override fun previewVolume(itemId: String, volume: Float) = Unit
    override fun hideCaption(itemId: String?) = Unit
    override fun close() = Unit
}

private object UnavailableThumbnailReader : ThumbnailReader {
    override suspend fun thumbnail(source: MediaRef, timeUs: Long, sizePx: Int): Outcome<Thumbnail> =
        Outcome.Failure(AppError.Unexpected(UnsupportedOperationException("Thumbnails on iOS arrive with P7-03")))

    override fun close() = Unit
}
