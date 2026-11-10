package app.memix.core.domain.photo

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.Logger
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.video.ExportError
import app.memix.core.domain.video.ExportPreferences
import app.memix.core.model.project.PhotoExportCatalog
import app.memix.core.model.project.PhotoExportFormat
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one photo export at a time (spec docs/ux/specs/P2-08-photo-export.md): Save to gallery or Copy, in the app's scope so
 * it survives a rotation and the user leaving Memix, and ends as Saved, Copied, Failed or Cancelled with the analytics
 * events. Closing the sheet while it works calls [cancel]. Not a foreground service: a photo takes a second or two.
 *
 * Main thread only for every call; the rendering runs on the renderer's own threads. The project is the copy taken at
 * the tap, so the file matches what the user saw.
 */
class ExportPhotoUseCase(
    private val renderer: PhotoRenderer,
    private val clipboard: PictureClipboard,
    private val preferences: PhotoExportPreferences,
    private val exportPreferences: ExportPreferences,
    private val analytics: Analytics,
    private val logger: Logger,
    private val appScope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
) {
    private val mutableState = MutableStateFlow<PhotoExportState>(PhotoExportState.Idle)

    /** The export now, or how the last one ended until [dismiss]. */
    val state: StateFlow<PhotoExportState> = mutableState.asStateFlow()

    private var running: Job? = null

    // Once the picture is written, a late cancel is too late: the file is in the gallery or on the clipboard's way, so the export
    // ends as Saved or Copied instead of showing Cancelled over a picture that exists.
    private var pictureWritten = false

    /** Whether the phone shows its own confirmation after a copy (no toast from the app then). */
    val clipboardConfirmsItself: Boolean get() = clipboard.confirmsCopyItself

    /** Starts [action] on [project] with [settings]. False when an export is already running. */
    fun start(project: Project, settings: PhotoExportSettings, action: PhotoExportAction): Boolean {
        if (state.value is PhotoExportState.Running) return false
        val job = PhotoExportJob(project, settings, action)
        val quality = settings.quality.takeIf { settings.format == PhotoExportFormat.JPG }
        analytics.log(AnalyticsEvent.PhotoExportStart(settings.format, settings.shortSidePx, action, quality))
        pictureWritten = false
        mutableState.value = PhotoExportState.Running(job)
        appScope.launch(mainDispatcher) { preferences.saveLastSettings(settings) }
        running = appScope.launch(mainDispatcher) { run(job) }
        return true
    }

    /** Close, the scrim or back while working: stops at once; the partial file is deleted by the renderer. */
    fun cancel() {
        val now = state.value as? PhotoExportState.Running ?: return
        if (pictureWritten) return
        running?.cancel()
        running = null
        analytics.log(AnalyticsEvent.ExportCancel(ProjectType.PHOTO, progress = 0))
        mutableState.value = PhotoExportState.Cancelled(now.job)
    }

    /** The screen showed how the export ended: back to idle. */
    fun dismiss() {
        if (state.value !is PhotoExportState.Running) mutableState.value = PhotoExportState.Idle
    }

    private var oldCopiesDeleted = false

    /**
     * At start-up: deletes the clipboard copies and unfinished gallery items earlier runs left. Once per process, and never
     * while an export runs: the screen calls it from a composition that a rotation starts again, and that must not delete
     * the picture being written or the copy the clipboard points to.
     */
    suspend fun deleteOldCopies() {
        if (oldCopiesDeleted || state.value is PhotoExportState.Running) return
        oldCopiesDeleted = true
        renderer.deleteOldCopies()
    }

    private suspend fun run(job: PhotoExportJob) {
        val started = TimeSource.Monotonic.markNow()
        val destination = if (job.action == PhotoExportAction.SAVE) PhotoDestination.GALLERY else PhotoDestination.CLIPBOARD_FILE
        // A cancel throws out of here; cancel() has already said how it ended, and the renderer has deleted the partial file.
        val outcome = renderer.render(job.project, PhotoRenderRequest(job.settings, destination))
        pictureWritten = true
        running = null
        when (outcome) {
            is PhotoRenderOutcome.Failed -> fail(job, outcome.error)
            is PhotoRenderOutcome.Done -> finish(job, outcome.photo, started.elapsedNow().inWholeMilliseconds)
        }
    }

    private suspend fun finish(job: PhotoExportJob, photo: RenderedPhoto, tookMs: Long) {
        if (job.action == PhotoExportAction.COPY && !clipboard.copyPicture(photo.location, photo.format.mimeType)) {
            return fail(job, ExportError.UNKNOWN)
        }
        logEstimateAgainstActual(job, photo, tookMs)
        analytics.log(AnalyticsEvent.ExportComplete(ProjectType.PHOTO, tookMs, photo.sizeBytes / BYTES_PER_MB, photo.format.eventValue))
        measuredMbPerMegapixel(photo.sizeBytes, photo.pixelSize)?.let { preferences.saveMeasuredMbPerMegapixel(photo.format, job.settings.quality, it) }
        // Saves and copies count alike, photo and video together, toward P5-03's "1 interstitial per 3 exports"; no ad shows here.
        exportPreferences.countFinishedExport()
        mutableState.value = if (job.action == PhotoExportAction.SAVE) PhotoExportState.Saved(job, photo) else PhotoExportState.Copied(job, photo)
    }

    // So the table in design/tokens.json can be tuned (spec → Estimate).
    private suspend fun logEstimateAgainstActual(job: PhotoExportJob, photo: RenderedPhoto, tookMs: Long) {
        val measured = preferences.measuredMbPerMegapixel(photo.format, job.settings.quality)
        val estimate = estimatePhotoBytes(photo.format, job.settings.quality, photo.pixelSize, measured)
        logger.debug(
            TAG,
            "${job.action.eventValue}: ${photo.format.label} q${job.settings.quality} ${photo.pixelSize.widthPx} x ${photo.pixelSize.heightPx}, " +
                "estimate $estimate bytes, actual ${photo.sizeBytes} bytes, $tookMs ms",
        )
    }

    private fun fail(job: PhotoExportJob, error: ExportError) {
        analytics.log(AnalyticsEvent.ExportFail(ProjectType.PHOTO, error.eventValue))
        mutableState.value = PhotoExportState.Failed(job, error)
    }

    private companion object {
        const val TAG = "MemixPhotoExport"
        const val BYTES_PER_MB = 1_000_000.0
    }
}

/** What one export was asked to make: the draft as it was at the tap, with the settings and the action. */
data class PhotoExportJob(val project: Project, val settings: PhotoExportSettings, val action: PhotoExportAction)

/** Where the photo export stands (spec P2-08 → States). */
sealed interface PhotoExportState {
    data object Idle : PhotoExportState

    data class Running(val job: PhotoExportJob) : PhotoExportState

    /** In the gallery: the Exported screen shows [photo]. */
    data class Saved(val job: PhotoExportJob, val photo: RenderedPhoto) : PhotoExportState

    /** On the clipboard. */
    data class Copied(val job: PhotoExportJob, val photo: RenderedPhoto) : PhotoExportState

    data class Failed(val job: PhotoExportJob, val error: ExportError) : PhotoExportState

    data class Cancelled(val job: PhotoExportJob) : PhotoExportState
}

/** The sheet's remembered settings, learned sizes and free space (spec P2-08 → Defaults, Estimate, Free space). Main-safe. */
class PhotoExportSheetFactsUseCase(
    private val preferences: PhotoExportPreferences,
    private val mediaFiles: MediaFiles,
) {
    /** The last settings, or JPG 90 at 1080 the first time; a remembered quality is pulled onto the slider's grid. */
    suspend fun lastSettings(): PhotoExportSettings {
        val last = preferences.lastSettings()
        return PhotoExportSettings(
            format = last?.format ?: PhotoExportCatalog.DEFAULT_FORMAT,
            quality = PhotoExportCatalog.snappedQuality(last?.quality ?: PhotoExportCatalog.DEFAULT_QUALITY),
            shortSidePx = last?.shortSidePx?.takeIf { it in PhotoExportCatalog.SHORT_SIDES_PX } ?: PhotoExportCatalog.DEFAULT_SHORT_SIDE_PX,
        )
    }

    suspend fun measuredMbPerMegapixel(settings: PhotoExportSettings): Double? = preferences.measuredMbPerMegapixel(settings.format, settings.quality)

    /** Bytes the app could still write. */
    suspend fun freeBytes(): Long = mediaFiles.allocatableBytes()
}
