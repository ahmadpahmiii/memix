package app.memix.feature.export

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import app.memix.core.domain.Outcome
import app.memix.core.domain.share.ShareVideoUseCase
import app.memix.core.domain.video.ExportCapabilities
import app.memix.core.domain.video.ExportChoice
import app.memix.core.domain.video.ExportError
import app.memix.core.domain.video.ExportJob
import app.memix.core.domain.video.ExportJobState
import app.memix.core.domain.video.ExportNotificationPermissionUseCase
import app.memix.core.domain.video.ExportResolution
import app.memix.core.domain.video.ExportVideoUseCase
import app.memix.core.domain.video.GetExportCapabilitiesUseCase
import app.memix.core.domain.video.OpenResultPlayerUseCase
import app.memix.core.domain.video.ResultPlayer
import app.memix.core.domain.video.StartThumbnailsUseCase
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.FrameFit
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind
import app.memix.core.ui.MemixViewModel
import kotlinx.coroutines.launch

/**
 * The Exporting and Exported screen (spec docs/ux/specs/P1-12-export.md → Exporting screen, Exported screen, failure
 * states): follows the one running export ([ExportVideoUseCase]), shows the draft's first frame while it runs, and
 * after it the result with Share, or the failure with what can be tried. Leaving goes back to the editor, Home or
 * Home with Create open; the screen never changes the draft.
 */
class ExportViewModel(
    private val export: ExportVideoUseCase,
    private val capabilities: GetExportCapabilitiesUseCase,
    private val notificationPermission: ExportNotificationPermissionUseCase,
    private val share: ShareVideoUseCase,
    private val startThumbnails: StartThumbnailsUseCase,
    private val openResultPlayer: OpenResultPlayerUseCase,
) : MemixViewModel<ExportUiState, ExportIntent>(ExportUiState()) {
    private var askedPermission = false
    private var frameLoadedFor: String? = null
    private var resultPlayer: ResultPlayer? = null
    private var resultLocation: String? = null

    init {
        addCloseable(AutoCloseable { resultPlayer?.close() })
        viewModelScope.launch { export.state.collect(::show) }
    }

    override fun onIntent(intent: ExportIntent) {
        when (intent) {
            ExportIntent.Cancel -> export.cancel()
            ExportIntent.Back -> back()
            ExportIntent.KeepExporting -> updateState { it.copy(stopSheetOpen = false) }
            ExportIntent.Stop -> {
                updateState { it.copy(stopSheetOpen = false) }
                export.cancel()
            }
            ExportIntent.BackToEditor -> leave(ExportExit.EDITOR)
            ExportIntent.Done -> leave(ExportExit.HOME)
            ExportIntent.MakeAnother -> leave(ExportExit.MAKE_ANOTHER)
            is ExportIntent.Share -> shareResult(intent.chooserTitle)
            ExportIntent.ToggleResultPlayback -> resultPlayer?.togglePlayback()
            ExportIntent.LeftScreen -> resultPlayer?.pause()
            ExportIntent.TryAgain -> retry(lower = false)
            ExportIntent.TryLower -> retry(lower = true)
            ExportIntent.NotificationPermissionAsked -> updateState { it.copy(askNotificationPermission = false) }
        }
    }

    private fun show(jobState: ExportJobState) {
        when (jobState) {
            // Nothing to show (the app came back with no export): back to where it was.
            ExportJobState.Idle -> if (state.value.exit == null) updateState { it.copy(exit = ExportExit.EDITOR) }
            is ExportJobState.Running -> showRunning(jobState)
            is ExportJobState.Done -> showResult(jobState)
            is ExportJobState.Failed -> showFailure(jobState.job, jobState.error)
            // The editor says "Export stopped." and clears the state.
            is ExportJobState.Cancelled -> updateState { it.copy(exit = ExportExit.EDITOR, stopSheetOpen = false) }
        }
    }

    private fun showRunning(running: ExportJobState.Running) {
        loadFrame(running.job.project)
        updateState { it.copy(phase = ExportPhase.Exporting(running.percent, running.secondsLeft)) }
        if (!askedPermission) {
            askedPermission = true
            // Asked once ever, after the export has started; it never waits for the answer (owner, 21 Oct 2026).
            viewModelScope.launch { if (notificationPermission.shouldAskNow()) updateState { it.copy(askNotificationPermission = true) } }
        }
    }

    // The single place between Exporting and Exported: P5-03's interstitial hooks in here, when its rules allow.
    private fun showResult(done: ExportJobState.Done) {
        loadFrame(done.job.project)
        showResultPlayer(done.video.location)
        updateState {
            it.copy(
                phase = ExportPhase.Exported(done.video.location, done.video.durationUs, done.job.choice.resolution.label),
                stopSheetOpen = false,
            )
        }
    }

    // One player per saved file, opened paused so its first frame sits behind the play disc.
    private fun showResultPlayer(location: String) {
        if (resultLocation == location) return
        resultPlayer?.close()
        val player = openResultPlayer(location)
        resultPlayer = player
        resultLocation = location
        updateState { it.copy(resultPlayer = player) }
    }

    private fun showFailure(job: ExportJob, error: ExportError) {
        loadFrame(job.project)
        updateState { it.copy(phase = ExportPhase.Failed(error, lowerResolution = null), stopSheetOpen = false) }
        if (error != ExportError.ENCODER) return
        // "Export at 720p": the next lower resolution the check allows for this canvas (spec → Failed: encoder).
        viewModelScope.launch {
            val canvas = PixelSize(job.project.canvas.widthPx, job.project.canvas.heightPx)
            val lower = lowerChoiceOf(capabilities(canvas), job.choice)
            updateState { state ->
                val failed = state.phase as? ExportPhase.Failed ?: return@updateState state
                state.copy(phase = failed.copy(lowerResolution = lower?.resolution?.label), retryChoice = lower)
            }
        }
    }

    private fun back() {
        if (state.value.exit != null) return
        when (state.value.phase) {
            // A back gesture can be accidental, and a long export is worth one question (spec → Back while exporting).
            is ExportPhase.Exporting -> updateState { it.copy(stopSheetOpen = true) }
            is ExportPhase.Exported, is ExportPhase.Failed -> leave(ExportExit.EDITOR)
        }
    }

    private fun leave(exit: ExportExit) {
        export.dismiss()
        // The phase stays, so the screen doesn't change its look while it slides away.
        updateState { it.copy(exit = exit) }
    }

    private fun shareResult(chooserTitle: String) {
        val exported = state.value.phase as? ExportPhase.Exported ?: return
        share(exported.location, chooserTitle)
    }

    private fun retry(lower: Boolean) {
        val failed = export.state.value as? ExportJobState.Failed ?: return
        val choice = if (lower) state.value.retryChoice ?: return else failed.job.choice
        export.dismiss()
        export.start(failed.job.project, choice)
    }

    // The draft's first frame (spec → Exporting screen: "as the Drafts thumbnail"): the first main clip's first frame
    // from the timeline's thumbnail reader, placed as the clip sits on the canvas (P1-14 can reuse it).
    private fun loadFrame(project: Project) {
        if (frameLoadedFor == project.id) return
        frameLoadedFor = project.id
        val clip = project.video?.tracks.orEmpty().firstOrNull { it.kind == TrackKind.MAIN_VIDEO }?.items
            ?.filterIsInstance<MediaClip>()?.minByOrNull { it.startUs }
        val background = (project.canvas.background as? CanvasBackground.Solid)?.color?.argb
        val frame = ExportFrameUi(project.canvas.widthPx.toFloat() / project.canvas.heightPx, background, clipAspectOf(clip, project), picture = null)
        updateState { it.copy(frame = frame) }
        if (clip == null) return
        viewModelScope.launch {
            val reader = startThumbnails()
            val picture = try {
                (reader.thumbnail(clip.source, clip.trimInUs, FRAME_PICTURE_PX) as? Outcome.Success)?.value?.jpegBytes
            } finally {
                reader.close()
            }
            if (picture != null) updateState { it.copy(frame = it.frame?.copy(picture = picture)) }
        }
    }

    // The clip's shape on the canvas: its own when it's fitted, the canvas's when it fills (or its size is unknown).
    private fun clipAspectOf(clip: MediaClip?, project: Project): Float {
        val canvasAspect = project.canvas.widthPx.toFloat() / project.canvas.heightPx
        val size = clip?.source?.pixelSize ?: return canvasAspect
        return if (clip.fit == FrameFit.FILL) canvasAspect else size.widthPx.toFloat() / size.heightPx
    }

    private companion object {
        /** The square tile's side: sharp at the frame's size on the Exporting screen. */
        const val FRAME_PICTURE_PX = 720
    }
}

/** The next lower resolution than [failed]'s that works for this canvas, at its frame rate or the nearest below; null at 480p. */
internal fun lowerChoiceOf(capabilities: ExportCapabilities, failed: ExportChoice): ExportChoice? {
    val lower = ExportResolution.entries.filter { it < failed.resolution }.lastOrNull() ?: return null
    return capabilities.nearestAtOrBelow(ExportChoice(lower, failed.frameRate))?.takeIf { it.resolution < failed.resolution }
}

/** What the screen shows (spec P1-12 → States). */
@Immutable
data class ExportUiState(
    val phase: ExportPhase = ExportPhase.Exporting(percent = 0, secondsLeft = null),
    /** The draft's frame, shown on every phase; null until the export has been read. */
    val frame: ExportFrameUi? = null,
    /** "Stop exporting?" after back during an export. */
    val stopSheetOpen: Boolean = false,
    /** Ask for the notification permission now (once ever); the screen sends NotificationPermissionAsked. */
    val askNotificationPermission: Boolean = false,
    /** The lower setting "Export at 720p" retries with. */
    val retryChoice: ExportChoice? = null,
    /** Plays the saved file on the Exported screen; null until the export is done. */
    val resultPlayer: ResultPlayer? = null,
    /** Where the app goes now; the screen sends it to its caller once. Null while the screen stays. */
    val exit: ExportExit? = null,
)

sealed interface ExportPhase {
    /** [percent] 0–100; [secondsLeft] once known. */
    data class Exporting(val percent: Int, val secondsLeft: Long?) : ExportPhase

    /** In the gallery at [location], [durationUs] long, at [resolutionLabel] ("1080p"). */
    data class Exported(val location: String, val durationUs: Long, val resolutionLabel: String) : ExportPhase

    /** Ended with [error]; [lowerResolution] ("720p") is offered after an encoder failure when the check allows one. */
    data class Failed(val error: ExportError, val lowerResolution: String?) : ExportPhase
}

/** Where the screen goes when it's left. */
enum class ExportExit {
    /** × or back after the export, Back to editor, or a Cancel. */
    EDITOR,

    /** Done. */
    HOME,

    /** Make another: Home with the Create sheet open (PM, 20 Oct 2026). */
    MAKE_ANOTHER,
}

/**
 * The meme's frame: the canvas's [aspectRatio], its background color ([backgroundArgb], null for blur, shown black)
 * and the first clip's first [picture] (a square JPEG, cropped to [pictureAspectRatio] as the clip sits on the canvas).
 */
@Immutable
data class ExportFrameUi(val aspectRatio: Float, val backgroundArgb: Long?, val pictureAspectRatio: Float, val picture: ByteArray?) {
    override fun equals(other: Any?): Boolean = other is ExportFrameUi && aspectRatio == other.aspectRatio &&
        backgroundArgb == other.backgroundArgb && pictureAspectRatio == other.pictureAspectRatio && picture === other.picture

    override fun hashCode(): Int = ((aspectRatio.hashCode() * HASH + backgroundArgb.hashCode()) * HASH + pictureAspectRatio.hashCode()) * HASH +
        (picture?.size ?: 0)

    private companion object {
        const val HASH = 31
    }
}

sealed interface ExportIntent {
    /** Cancel while exporting. */
    data object Cancel : ExportIntent

    /** System back or Escape. */
    data object Back : ExportIntent

    data object KeepExporting : ExportIntent

    /** Stop in "Stop exporting?". */
    data object Stop : ExportIntent

    /** × on the result, or Back to editor on a failure. */
    data object BackToEditor : ExportIntent

    data object Done : ExportIntent

    data object MakeAnother : ExportIntent

    /** Share opens the phone's share sheet titled [chooserTitle]. */
    data class Share(val chooserTitle: String) : ExportIntent

    data object TryAgain : ExportIntent

    /** "Export at 720p" after an encoder failure. */
    data object TryLower : ExportIntent

    data object NotificationPermissionAsked : ExportIntent

    /** Tap on the result: play once, or pause. */
    data object ToggleResultPlayback : ExportIntent

    /** The app left the screen: the result stops playing. */
    data object LeftScreen : ExportIntent
}
