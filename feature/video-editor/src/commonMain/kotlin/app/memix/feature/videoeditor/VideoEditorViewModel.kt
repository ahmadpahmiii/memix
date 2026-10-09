package app.memix.feature.videoeditor

import androidx.lifecycle.viewModelScope
import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.OpenVideoDraftUseCase
import app.memix.core.domain.project.ProjectEditSession
import app.memix.core.domain.project.ProjectEditState
import app.memix.core.domain.project.StartEditSessionUseCase
import app.memix.core.domain.project.hasNoClips
import app.memix.core.domain.project.videoLengthUs
import app.memix.core.domain.video.ExportSettings
import app.memix.core.domain.video.PreviewPlayback
import app.memix.core.domain.video.PreviewSession
import app.memix.core.domain.video.PreviewStatus
import app.memix.core.domain.video.StartPreviewUseCase
import app.memix.core.domain.video.StartThumbnailsUseCase
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.Project
import app.memix.core.ui.MemixViewModel
import app.memix.feature.videoeditor.timeline.TimelineThumbnails
import app.memix.feature.videoeditor.timeline.timelineUiOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The video editor (specs docs/ux/specs/P1-04-editor-and-preview.md and P1-05-timeline.md): opens a draft, edits it
 * through a [ProjectEditSession] (undo, redo, auto-save), shows it in a live [PreviewSession] and on the timeline.
 *
 * The session's state, the preview's playback, the timeline's items and the selection are copied into the one
 * UiState; the preview's time is not (it changes every frame, see [EditorPreview]), and neither are the timeline's
 * scroll and zoom (the timeline keeps them). The sessions and the thumbnail reader close with this ViewModel: the
 * edit session saves on the way out (or deletes a draft left with no clips), the preview frees its decoders and the
 * thumbnails their open files. [decodeDispatcher] decodes thumbnail pictures off the main thread.
 */
class VideoEditorViewModel(
    projectId: String,
    openVideoDraft: OpenVideoDraftUseCase,
    private val startEditSession: StartEditSessionUseCase,
    private val startPreview: StartPreviewUseCase,
    private val startThumbnails: StartThumbnailsUseCase,
    private val decodeDispatcher: CoroutineDispatcher,
    private val debugEdit: EditorDebugEdit?,
) : MemixViewModel<VideoEditorUiState, VideoEditorIntent>(VideoEditorUiState(canMakeDebugEdit = debugEdit != null)) {

    private var editSession: ProjectEditSession? = null
    private var preview: PreviewSession? = null
    private var previewWatch: Job? = null
    private var shownProject: Project? = null
    private var saveFailure: AppError? = null
    private var bannerDismissed = false
    private var missingMediaShown = false
    private var nextToastId = 0L

    init {
        addCloseable(AutoCloseable { preview?.close() })
        viewModelScope.launch {
            when (val opened = openVideoDraft(projectId)) {
                is Outcome.Success -> startEditing(opened.value)
                // Not reachable from P1 flows (the import opens the draft it just saved); P1-14 specs what an
                // unreadable draft says. The repository has logged why.
                is Outcome.Failure -> updateState { it.copy(isClosing = true) }
            }
        }
    }

    override fun onIntent(intent: VideoEditorIntent) {
        when (intent) {
            VideoEditorIntent.PlayPause -> togglePlayback()
            VideoEditorIntent.Undo -> undo()
            VideoEditorIntent.Redo -> redo()
            VideoEditorIntent.Close -> requestClose()
            VideoEditorIntent.LeaveAnyway -> updateState { it.copy(unsavedSheetOpen = false, isClosing = true) }
            VideoEditorIntent.StayInEditor -> updateState { it.copy(unsavedSheetOpen = false) }
            VideoEditorIntent.DismissSaveBanner -> dismissSaveBanner()
            VideoEditorIntent.FreeUpSpaceUnavailable -> updateState { it.copy(canFreeUpSpace = false) }
            VideoEditorIntent.RetryPreview -> retryPreview()
            is VideoEditorIntent.ToastTimedOut -> updateState { if (it.toast?.id == intent.id) it.copy(toast = null) else it }
            VideoEditorIntent.AppStarted -> retryFailedSave()
            VideoEditorIntent.AppStopped -> goToBackground()
            VideoEditorIntent.DebugEdit -> makeDebugEdit()
            VideoEditorIntent.TimelineTouched -> preview?.pause()
            VideoEditorIntent.ScrubStarted -> startScrubbing()
            is VideoEditorIntent.ScrubTo -> preview?.seekTo(intent.positionUs)
            is VideoEditorIntent.ScrubEnded -> endScrubbing(intent.positionUs)
            is VideoEditorIntent.SeekTo -> seekTo(intent.positionUs)
            is VideoEditorIntent.SelectItem -> selectItem(intent.itemId)
            VideoEditorIntent.ClearSelection -> updateState { it.copy(selectedItemId = null) }
        }
    }

    private fun startEditing(project: Project) {
        val session = startEditSession(project)
        addCloseable(session)
        editSession = session
        val thumbnails = TimelineThumbnails(startThumbnails(), viewModelScope, decodeDispatcher)
        addCloseable(thumbnails)
        updateState { it.copy(canvas = canvasUiOf(project.canvas), thumbnails = thumbnails) }
        viewModelScope.launch { session.state.collect(::showEditState) }
    }

    private fun showEditState(edit: ProjectEditState) {
        if (edit.project !== shownProject) showProject(edit.project)
        showSaveState(edit.saveFailure)
        updateState { it.copy(canUndo = edit.canUndo, canRedo = edit.canRedo) }
    }

    private fun showProject(project: Project) {
        shownProject = project
        val timeline = timelineUiOf(project)
        // The selection outlives edits, undo and redo while its item exists (spec P1-04, P1-05 → Selection).
        updateState {
            it.copy(lengthUs = project.videoLengthUs(), timeline = timeline, selectedItemId = it.selectedItemId?.takeIf { id -> timeline.item(id) != null })
        }
        if (project.hasNoClips()) {
            closePreview()
            updateState { it.copy(stage = StageContent.EMPTY, isPlaying = false) }
            return
        }
        val current = preview
        if (current == null) openPreview(project) else current.update(project)
    }

    private fun openPreview(project: Project) {
        val session = startPreview(project)
        preview = session
        updateState { it.copy(preview = EditorPreview(session), stage = StageContent.OPENING) }
        previewWatch = viewModelScope.launch { session.playback.collect(::showPlayback) }
    }

    private fun closePreview() {
        previewWatch?.cancel()
        preview?.close()
        preview = null
        updateState { it.copy(preview = null) }
    }

    private fun showPlayback(playback: PreviewPlayback) {
        val stage = when {
            playback.status == PreviewStatus.FAILED -> StageContent.ERROR
            playback.firstFrameShown -> StageContent.VIDEO
            else -> StageContent.OPENING
        }
        updateState { it.copy(stage = stage, isPlaying = playback.isPlaying) }
        if (playback.missingMedia && !missingMediaShown) {
            missingMediaShown = true
            showToast(ToastMessage.MissingMedia)
        }
    }

    private fun togglePlayback() {
        val session = preview ?: return
        if (!state.value.canPlay) return
        if (session.playback.value.isPlaying) {
            session.pause()
            return
        }
        // At the end (within a frame), Play starts again from 0:00; there is no loop (spec P1-04 → Playback).
        if (session.positionUs.value >= state.value.lengthUs - END_TOLERANCE_US) session.seekTo(0)
        session.play()
    }

    // Media3's scrubbing mode shows fast nearby frames while the timeline moves; paused, as the session asks.
    private fun startScrubbing() {
        val session = preview ?: return
        session.pause()
        session.setScrubbing(true)
    }

    // The exact frame under the playhead once the timeline rests.
    private fun endScrubbing(positionUs: Long) {
        val session = preview ?: return
        session.setScrubbing(false)
        session.seekTo(positionUs)
    }

    private fun seekTo(positionUs: Long) {
        val session = preview ?: return
        session.pause()
        session.seekTo(positionUs)
    }

    private fun selectItem(itemId: String) {
        updateState { if (it.timeline.item(itemId) != null) it.copy(selectedItemId = itemId) else it }
    }

    private fun undo() {
        val session = editSession ?: return
        val edit = session.state.value.takeIf { it.canUndo }?.undoEditName ?: return
        preview?.pause()
        session.undo()
        VideoEdit.fromId(edit)?.let { showToast(ToastMessage.Undone(it)) }
    }

    private fun redo() {
        val session = editSession ?: return
        val edit = session.state.value.takeIf { it.canRedo }?.redoEditName ?: return
        preview?.pause()
        session.redo()
        VideoEdit.fromId(edit)?.let { showToast(ToastMessage.Redone(it)) }
    }

    private fun makeDebugEdit() {
        val edit = debugEdit ?: return
        preview?.pause()
        editSession?.commit(VideoEdit.TRIM.id, edit::apply)
    }

    // Leaving saves by itself; only changes that can't be saved make it ask first (spec P1-04 → Saving).
    private fun requestClose() {
        preview?.pause()
        if (saveFailure != null) updateState { it.copy(unsavedSheetOpen = true) } else updateState { it.copy(isClosing = true) }
    }

    private fun retryPreview() {
        val project = shownProject ?: return
        closePreview()
        openPreview(project)
    }

    private fun goToBackground() {
        preview?.pause()
        // Android may end a background app without warning, so the newest change is written now.
        editSession?.saveNow()
    }

    // Back from the storage manager, or from anywhere else: a failed save tries again.
    private fun retryFailedSave() {
        if (saveFailure != null) editSession?.saveNow()
    }

    private fun showSaveState(failure: AppError?) {
        val failedBefore = saveFailure != null
        saveFailure = failure
        when {
            failure != null -> updateState {
                it.copy(isSaveFailing = true, saveBanner = if (bannerDismissed) null else saveProblemOf(failure))
            }
            failedBefore -> {
                // The banner can come back if a later save fails again.
                bannerDismissed = false
                updateState {
                    it.copy(isSaveFailing = false, saveBanner = null, unsavedSheetOpen = false, savedAgainCount = it.savedAgainCount + 1)
                }
            }
        }
    }

    private fun dismissSaveBanner() {
        bannerDismissed = true
        updateState { it.copy(saveBanner = null) }
    }

    // The save banner owns the one message slot: a toast that arrives under it is spoken, not shown or queued.
    private fun showToast(message: ToastMessage) {
        val id = nextToastId++
        updateState { it.copy(toast = EditorToast(id, message, isSpokenOnly = it.saveBanner != null)) }
    }

    private fun saveProblemOf(failure: AppError): SaveProblem =
        if (failure == AppError.StorageFull) SaveProblem.STORAGE_FULL else SaveProblem.OTHER

    private fun canvasUiOf(canvas: Canvas) = CanvasUi(
        aspectRatio = canvas.widthPx.toFloat() / canvas.heightPx,
        ratioLabel = ratioLabelOf(canvas),
    )

    private fun ratioLabelOf(canvas: Canvas): String = when (canvas.ratio) {
        CanvasRatio.RATIO_9_16 -> "9:16"
        CanvasRatio.RATIO_1_1 -> "1:1"
        CanvasRatio.RATIO_4_5 -> "4:5"
        CanvasRatio.RATIO_3_4 -> "3:4"
        CanvasRatio.RATIO_16_9 -> "16:9"
        CanvasRatio.CUSTOM -> {
            val divisor = greatestCommonDivisor(canvas.widthPx, canvas.heightPx).coerceAtLeast(1)
            "${canvas.widthPx / divisor}:${canvas.heightPx / divisor}"
        }
    }

    private tailrec fun greatestCommonDivisor(a: Int, b: Int): Int = if (b == 0) a else greatestCommonDivisor(b, a % b)

    private companion object {
        /** One frame at the export's frame rate: Play this close to the end counts as "at the end". */
        const val END_TOLERANCE_US = 1_000_000L / ExportSettings.DEFAULT_FRAME_RATE
    }
}
