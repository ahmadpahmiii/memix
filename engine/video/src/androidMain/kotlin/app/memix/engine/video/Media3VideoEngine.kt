package app.memix.engine.video

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import app.memix.core.domain.AppError
import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.video.ExportSettings
import app.memix.core.domain.video.ExportedVideo
import app.memix.core.domain.video.PreviewSession
import app.memix.core.domain.video.ThumbnailReader
import app.memix.core.domain.video.VideoEngine
import app.memix.core.model.project.MediaRef
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
) : VideoEngine {
    private val sourceResolver = MediaSourceResolver(context.filesDir)
    private val transformerExport = TransformerExport(context)

    override fun createPreview(project: Project): PreviewSession =
        Media3PreviewSession(context, sourceResolver, logger, ioDispatcher, project)

    override fun openThumbnails(): ThumbnailReader = MediaThumbnailReader(context.filesDir, ioDispatcher, logger)

    override suspend fun export(
        project: Project,
        settings: ExportSettings,
        onProgress: (Float) -> Unit,
    ): Outcome<ExportedVideo> = when (val planned = CompositionPlanner.plan(project)) {
        is PlanResult.Unplayable -> unplayable(planned.problem)
        is PlanResult.Ready -> exportPlan(planned.plan, settings, onProgress)
    }

    private suspend fun exportPlan(
        plan: CompositionPlan,
        settings: ExportSettings,
        onProgress: (Float) -> Unit,
    ): Outcome<ExportedVideo> {
        plan.notRendered.forEach { logger.debug(TAG, it) }
        val resolved = withContext(ioDispatcher) { sourceResolver.resolve(plan) }
        val firstMissing = resolved.missingItemIds.firstOrNull()
        if (firstMissing != null) {
            logger.debug(TAG, "Export stopped: the media for item $firstMissing isn't on the phone")
            return Outcome.Failure(AppError.NotFound)
        }
        val sources = resolved.uriByMedia
        val outputFile = try {
            withContext(ioDispatcher) { newOutputFile() }
        } catch (e: IOException) {
            return Outcome.Failure(failureFor(e))
        }
        return writeFile(plan, sources, settings, outputFile, onProgress)
    }

    private suspend fun writeFile(
        plan: CompositionPlan,
        sources: Map<MediaRef, Uri>,
        settings: ExportSettings,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ): Outcome<ExportedVideo> {
        val started = TimeSource.Monotonic.markNow()
        var succeeded = false
        try {
            val composition = Media3CompositionBuilder(plan, sources, settings.frameRate).build()
            val result = transformerExport.run(composition, outputFile.path, onProgress)
            val video = withContext(ioDispatcher) { exportedVideo(outputFile, result, plan) }
            logger.debug(TAG, "Exported ${plan.durationUs} µs of video in ${started.elapsedNow().inWholeMilliseconds} ms")
            succeeded = true
            return Outcome.Success(video)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ExportException from Media3, or IllegalArgumentException/IllegalStateException from its input checks.
            return Outcome.Failure(failureFor(e))
        } finally {
            // Media3 leaves a failed or cancelled file behind.
            if (!succeeded) withContext(NonCancellable + ioDispatcher) { outputFile.delete() }
        }
    }

    // Exports go to the cache, which the OS may clear; P1-12 moves each finished file to the gallery.
    private fun newOutputFile(): File {
        val exportsDir = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }
        return File.createTempFile(FILE_PREFIX, FILE_SUFFIX, exportsDir)
    }

    private fun exportedVideo(file: File, result: ExportResult, plan: CompositionPlan) = ExportedVideo(
        path = file.path,
        // Media3 reports whole milliseconds, or nothing when it can't tell; the plan has the intended length.
        durationUs = if (result.approximateDurationMs == C.TIME_UNSET) {
            plan.durationUs
        } else {
            result.approximateDurationMs * MICROS_PER_MILLI
        },
        sizeBytes = file.length(),
        hasAudio = result.audioMimeType != null,
    )

    private fun unplayable(problem: PlanProblem): Outcome<ExportedVideo> {
        val reason = when (problem) {
            PlanProblem.EmptyMainTrack -> "the main video track is empty"
            is PlanProblem.MainClipOutOfPlace -> "main video clip ${problem.itemId} starts before the clip in front of it ends"
        }
        val error = IllegalArgumentException("Can't export: $reason")
        logger.error(TAG, "Export refused", error)
        return Outcome.Failure(AppError.Unexpected(error))
    }

    private fun failureFor(e: Exception): AppError {
        val error = when {
            e is ExportException && e.errorCode in UNREADABLE_MEDIA_ERRORS -> AppError.NotFound
            e.isOutOfSpace() -> AppError.StorageFull
            else -> AppError.Unexpected(e)
        }
        val code = (e as? ExportException)?.errorCodeName ?: e::class.simpleName
        // A deleted file or a full disk isn't a bug, so only the rest goes to Crashlytics as an error.
        if (error is AppError.Unexpected) logger.error(TAG, "Export failed: $code", e) else logger.debug(TAG, "Export stopped: $code")
        return error
    }

    // A full disk surfaces as ENOSPC somewhere down the cause chain, usually an ErrnoException under an IOException.
    private fun Throwable.isOutOfSpace(): Boolean =
        generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { it.message?.contains(NO_SPACE_ERRNO) == true }

    private companion object {
        const val TAG = "MemixVideoEngine"
        const val EXPORTS_DIR = "exports"
        const val FILE_PREFIX = "export-"
        const val FILE_SUFFIX = ".mp4"
        const val MICROS_PER_MILLI = 1_000L
        const val NO_SPACE_ERRNO = "ENOSPC"
        const val MAX_CAUSE_DEPTH = 10
        val UNREADABLE_MEDIA_ERRORS = setOf(ExportException.ERROR_CODE_IO_FILE_NOT_FOUND, ExportException.ERROR_CODE_IO_NO_PERMISSION)
    }
}
