package app.memix.engine.video

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import app.memix.core.domain.Logger
import app.memix.core.domain.sound.SoundPlayer
import app.memix.core.domain.video.EXPORT_AUDIO_BITRATE_BPS
import app.memix.core.domain.video.ExportCapabilities
import app.memix.core.domain.video.ExportError
import app.memix.core.domain.video.ExportOutcome
import app.memix.core.domain.video.ExportSettings
import app.memix.core.domain.video.ExportedVideo
import app.memix.core.domain.video.PreviewSession
import app.memix.core.domain.video.ResultPlayer
import app.memix.core.domain.video.ThumbnailReader
import app.memix.core.domain.video.VideoEngine
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The Android [VideoEngine]: plans the project in shared code ([CompositionPlanner]), builds a Media3
 * composition from the plan, exports it with Transformer into the app's cache, previews it with
 * CompositionPlayer ([Media3PreviewSession]), and reads the timeline's thumbnails ([MediaThumbnailReader]).
 */
@OptIn(UnstableApi::class)
internal class Media3VideoEngine(
    private val context: Context,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher,
    /** Draws the captions of every preview and export. */
    private val captionRenderer: AndroidCaptionRenderer,
    /** Builds with hand checks: previews log where each sound is heard ([SoundOnsetLog]). */
    private val measureSoundOnsets: Boolean = false,
) : VideoEngine {
    private val sourceResolver = MediaSourceResolver(context.filesDir)
    private val transformerExport = TransformerExport(context)
    private val capabilityScanner = ExportCapabilityScanner(ioDispatcher, GalleryVideoFile.WRITES_STRAIGHT_TO_GALLERY)

    override fun createPreview(project: Project): PreviewSession =
        Media3PreviewSession(context, sourceResolver, captionRenderer, logger, ioDispatcher, project, measureSoundOnsets)

    override fun openThumbnails(): ThumbnailReader = MediaThumbnailReader(context.filesDir, ioDispatcher, logger)

    override fun openSoundPlayer(): SoundPlayer = Media3SoundPlayer(context, logger)

    override fun openResultPlayer(location: String): ResultPlayer = Media3ResultPlayer(context, location, logger)

    override suspend fun exportCapabilities(canvasSize: PixelSize): ExportCapabilities = capabilityScanner.capabilitiesFor(canvasSize)

    override suspend fun discardUnfinishedExports() = withContext(ioDispatcher) {
        try {
            GalleryVideoFile.discardUnfinished(context)
        } catch (e: RuntimeException) {
            // The gallery deletes pending items itself after a week; a refused query costs nothing else.
            logger.error(TAG, "Unfinished exports couldn't be cleaned up", e)
        }
    }

    override suspend fun export(
        project: Project,
        settings: ExportSettings,
        onProgress: (Float) -> Unit,
    ): ExportOutcome = when (val planned = CompositionPlanner.plan(project)) {
        is PlanResult.Unplayable -> unplayable(planned.problem)
        is PlanResult.Ready -> exportPlan(planned.plan, settings, onProgress)
    }

    private suspend fun exportPlan(plan: CompositionPlan, settings: ExportSettings, onProgress: (Float) -> Unit): ExportOutcome {
        plan.notRendered.forEach { logger.debug(TAG, it) }
        val resolved = withContext(ioDispatcher) { sourceResolver.resolve(plan) }
        // A clip already missing exports black and silent, as the preview plays it (the sheet said so first).
        if (resolved.missingItemIds.isNotEmpty()) logger.debug(TAG, "Exporting ${resolved.missingItemIds.size} missing clip(s) as black")
        val playable = plan.withGapsFor(resolved.missingItemIds.toSet())
        val output = try {
            withContext(ioDispatcher) { ExportOutput.create(context, settings.destination) }
        } catch (e: IOException) {
            return failed(e)
        } catch (e: SecurityException) {
            return failed(e)
        }
        return writeFile(playable, resolved.uriByMedia, resolved.backgroundFile, settings, output, onProgress)
    }

    private suspend fun writeFile(
        plan: CompositionPlan,
        sources: Map<MediaRef, Uri>,
        backgroundFile: File?,
        settings: ExportSettings,
        output: ExportOutput,
        onProgress: (Float) -> Unit,
    ): ExportOutcome {
        val started = TimeSource.Monotonic.markNow()
        var succeeded = false
        try {
            captionRenderer.prepare()
            val captions = plan.captions.takeIf { it.isNotEmpty() }?.let {
                CaptionOverlay(it, captionRenderer, plan.canvasWidthPx, plan.canvasHeightPx)
            }
            // Off the main thread: a background picture is decoded at the export's frame size (P2-12).
            val composition = withContext(ioDispatcher) {
                Media3CompositionBuilder(
                    plan,
                    sources,
                    settings.frameRate,
                    captionOverlay = captions,
                    watermarkOverlay = if (settings.watermark) WatermarkOverlay(captionRenderer) else null,
                    frameShortSidePx = settings.shortSidePx,
                    frameSizePx = settings.frameSize,
                    backgroundFile = backgroundFile,
                ).build()
            }
            val result = transformerExport.run(
                composition,
                output.transformerPath,
                settings.codec.mimeType(),
                settings.videoBitrateBps,
                output.muxerFactory(),
                onProgress,
            )
            val video = withContext(ioDispatcher) { exportedVideo(output.finish(), result, plan) }
            logEstimateCheck(plan, settings, video, started.elapsedNow().inWholeMilliseconds)
            succeeded = true
            return ExportOutcome.Done(video)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ExportException from Media3, IOException from the gallery, or IllegalArgumentException/IllegalStateException
            // from Media3's input checks.
            return failed(e)
        } finally {
            // Media3 leaves a failed or cancelled file behind.
            if (!succeeded) withContext(NonCancellable + ioDispatcher) { output.discard() }
        }
    }

    // QA's "never larger than shown" reads this line (spec P1-12 → Measured). The estimate's formula is the sheet's.
    private fun logEstimateCheck(plan: CompositionPlan, settings: ExportSettings, video: ExportedVideo, tookMs: Long) {
        val estimateBytes = settings.videoBitrateBps?.let { videoBps ->
            ((videoBps.toLong() + EXPORT_AUDIO_BITRATE_BPS) * plan.durationUs / MICROS_PER_SECOND * CONTAINER_ALLOWANCE / BITS_PER_BYTE).toLong()
        }
        logger.debug(
            TAG,
            "Exported ${plan.durationUs} µs in $tookMs ms: ${video.sizeBytes} bytes (estimate $estimateBytes), " +
                "averageVideoBitrate=${video.averageVideoBitrateBps}, requested=${settings.videoBitrateBps}, " +
                "${settings.codec} ${settings.frameSize} at ${settings.frameRate} fps",
        )
    }

    private fun exportedVideo(finished: FinishedFile, result: ExportResult, plan: CompositionPlan) = ExportedVideo(
        location = finished.location,
        // Media3 reports whole milliseconds, or nothing when it can't tell; the plan has the intended length.
        durationUs = if (result.approximateDurationMs == C.TIME_UNSET) {
            plan.durationUs
        } else {
            result.approximateDurationMs * MICROS_PER_MILLI
        },
        sizeBytes = finished.sizeBytes,
        hasAudio = result.audioMimeType != null,
        averageVideoBitrateBps = result.averageVideoBitrate.takeIf { it != C.RATE_UNSET_INT },
    )

    private fun unplayable(problem: PlanProblem): ExportOutcome {
        val reason = when (problem) {
            PlanProblem.EmptyMainTrack -> "the main video track is empty"
            is PlanProblem.MainClipOutOfPlace -> "main video clip ${problem.itemId} starts before the clip in front of it ends"
        }
        logger.error(TAG, "Export refused", IllegalArgumentException("Can't export: $reason"))
        return ExportOutcome.Failed(ExportError.UNKNOWN)
    }

    private fun failed(e: Exception): ExportOutcome.Failed {
        val error = exportErrorOf(e)
        val code = (e as? ExportException)?.errorCodeName ?: e::class.simpleName
        // A deleted file or a full disk isn't a bug, so only the rest goes to Crashlytics as an error.
        val expected = error == ExportError.STORAGE_FULL || error == ExportError.MISSING_MEDIA
        if (expected) logger.debug(TAG, "Export stopped: $code") else logger.error(TAG, "Export failed: $code", e)
        return ExportOutcome.Failed(error)
    }

    private companion object {
        const val TAG = "MemixVideoEngine"
        const val MICROS_PER_MILLI = 1_000L
        const val MICROS_PER_SECOND = 1_000_000L
        const val CONTAINER_ALLOWANCE = 1.05
        const val BITS_PER_BYTE = 8
    }
}
