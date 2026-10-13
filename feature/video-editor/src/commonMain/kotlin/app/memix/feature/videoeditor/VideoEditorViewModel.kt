package app.memix.feature.videoeditor

import androidx.lifecycle.viewModelScope
import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.OpenVideoDraftUseCase
import app.memix.core.domain.project.ProjectEditSession
import app.memix.core.domain.project.ProjectEditState
import app.memix.core.domain.project.StartEditSessionUseCase
import app.memix.core.domain.project.TrimEdge
import app.memix.core.domain.project.canDelete
import app.memix.core.domain.project.canSplit
import app.memix.core.domain.project.deleteItem
import app.memix.core.domain.project.duplicateItem
import app.memix.core.domain.project.hasNoClips
import app.memix.core.domain.project.mainClipIds
import app.memix.core.domain.project.moveMainClip
import app.memix.core.domain.project.reorderMainClips
import app.memix.core.domain.project.splitItem
import app.memix.core.domain.project.trimItem
import app.memix.core.domain.project.trimRange
import app.memix.core.domain.project.videoLengthUs
import app.memix.core.domain.video.ExportSettings
import app.memix.core.domain.video.PreviewPlayback
import app.memix.core.domain.video.PreviewSession
import app.memix.core.domain.video.PreviewStatus
import app.memix.core.domain.video.StartPreviewUseCase
import app.memix.core.domain.video.StartThumbnailsUseCase
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind
import app.memix.core.ui.MemixViewModel
import app.memix.feature.videoeditor.timeline.ItemName
import app.memix.feature.videoeditor.timeline.TimelineThumbnails
import app.memix.feature.videoeditor.timeline.TrimRanges
import app.memix.feature.videoeditor.timeline.nameOf
import app.memix.feature.videoeditor.timeline.timelineUiOf
import app.memix.feature.videoeditor.timeline.trackOf
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The video editor (specs docs/ux/specs/P1-04-editor-and-preview.md, P1-05-timeline.md and
 * P1-06-clip-edits-and-audio.md): opens a draft, edits it through a [ProjectEditSession] (undo, redo, auto-save),
 * shows it in a live [PreviewSession] and on the timeline.
 *
 * Every edit pauses playback, commits one step and shows on the timeline at once; the preview catches up by itself
 * and keeps its last frame meanwhile (spec P1-06 → Preview after an edit). The edits themselves are the domain's
 * (TimelineEdits); this class picks the item, explains when a tool can't act, and announces what happened.
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
    private var nextLandingSerial = 0L
    // Runs from the first edit the preview hasn't shown yet; one second later the stage says it's updating.
    private var catchUpTimer: Job? = null
    // The trim being dragged: its video or photo clip as it was when the drag began, so each preview frame is a seek
    // in one composition. Null when nothing is dragged or the item has no picture (sounds, captions).
    private var trimmedClip: MediaClip? = null
    private var trimmedEdge = TrimEdge.START

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
            VideoEditorIntent.ClearSelection -> updateState { it.withSelection(null, null) }
            VideoEditorIntent.Back -> goBack()
            VideoEditorIntent.EditTool -> selectClipUnderPlayhead()
            VideoEditorIntent.SplitSelected -> splitSelected()
            VideoEditorIntent.DeleteSelected -> deleteSelected()
            VideoEditorIntent.DuplicateSelected -> duplicateSelected()
            is VideoEditorIntent.MoveSelectedClip -> moveSelectedClip(intent.steps)
            is VideoEditorIntent.TrimStarted -> startTrim(intent.itemId, intent.edge)
            is VideoEditorIntent.TrimMoved -> showTrimFrame(intent.edgeUs)
            is VideoEditorIntent.TrimEnded -> endTrim(intent.itemId, intent.edge, intent.edgeUs, intent.playheadUs)
            is VideoEditorIntent.TrimCancelled -> cancelTrim(intent.playheadUs)
            is VideoEditorIntent.TrimTo -> trimTo(intent.itemId, intent.edge, intent.edgeUs)
            VideoEditorIntent.StartReorderSelected -> state.value.selectedItemId?.let { startReorder(it, byDrag = false) }
            is VideoEditorIntent.StartReorder -> startReorder(intent.itemId, intent.byDrag)
            is VideoEditorIntent.ReorderMoveTo -> moveInReorder(intent.index)
            is VideoEditorIntent.ReorderStep -> stepInReorder(intent.steps)
            VideoEditorIntent.ReorderDone -> finishReorder()
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
        // Reorder mode can't outlive a change to the clips it's ordering (an undo pressed meanwhile): it closes unapplied.
        val clipIds = project.mainClipIds().toSet()
        updateState {
            it.copy(lengthUs = project.videoLengthUs(), timeline = timeline, reorder = it.reorder?.takeIf { reorder -> reorder.originalOrder.toSet() == clipIds })
                .withSelection(it.selectedItemId, project)
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
        showCatchingUp(false)
        updateState { it.copy(preview = null) }
    }

    private fun showPlayback(playback: PreviewPlayback) {
        val stage = when {
            playback.status == PreviewStatus.FAILED -> StageContent.ERROR
            playback.firstFrameShown -> StageContent.VIDEO
            else -> StageContent.OPENING
        }
        updateState { it.copy(stage = stage, isPlaying = playback.isPlaying) }
        showCatchingUp(playback.isCatchingUp)
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
        updateState { if (it.timeline.item(itemId) != null) it.withSelection(itemId, currentProject()) else it }
    }

    /**
     * The innermost thing first: reorder mode is cancelled, then the selection clears, then the editor closes (spec
     * P1-04 → System back order).
     */
    private fun goBack() {
        when {
            state.value.reorder != null -> updateState { it.copy(reorder = null) }
            state.value.selectedItemId != null -> updateState { it.withSelection(null, null) }
            else -> requestClose()
        }
    }

    // The tap route to clip tools, also for clips too thin to hit (spec P1-06 → Main tool bar).
    private fun selectClipUnderPlayhead() {
        val mainTrack = state.value.timeline.tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO } ?: return
        val atUs = currentPositionUs()
        val clip = mainTrack.items.firstOrNull { atUs >= it.startUs && atUs < it.endUs } ?: mainTrack.items.lastOrNull() ?: return
        updateState { it.withSelection(clip.id, currentProject()) }
    }

    private fun splitSelected() {
        val itemId = state.value.selectedItemId ?: return
        val project = currentProject() ?: return
        preview?.pause()
        val atUs = currentPositionUs()
        if (!project.canSplit(itemId, atUs)) return showToast(ToastMessage.Explain(Explanation.SPLIT_OUTSIDE))
        val rightPieceId = newId()
        if (!commit(VideoEdit.SPLIT) { it.splitItem(itemId, atUs, rightPieceId) }) return
        // The piece from the playhead on is selected, so "Split, then Delete" removes everything after the playhead.
        updateState { it.withSelection(rightPieceId, currentProject()) }
        announce(VideoEdit.SPLIT, state.value.timeline.nameOf(rightPieceId))
    }

    private fun deleteSelected() {
        val itemId = state.value.selectedItemId ?: return
        val project = currentProject() ?: return
        preview?.pause()
        if (!project.canDelete(itemId)) return showToast(ToastMessage.Explain(Explanation.LAST_CLIP))
        // Named before it's gone.
        val name = state.value.timeline.nameOf(itemId)
        if (!commit(VideoEdit.DELETE) { it.deleteItem(itemId) }) return
        updateState { it.withSelection(null, null) }
        announce(VideoEdit.DELETE, name)
    }

    private fun duplicateSelected() {
        val itemId = state.value.selectedItemId ?: return
        preview?.pause()
        val copyId = newId()
        if (!commit(VideoEdit.DUPLICATE) { it.duplicateItem(itemId, copyId, newTrackId = newId()) }) return
        // The copy is new content, so it lands with the bonk (spec P1-06 → Duplicate).
        updateState { it.withSelection(copyId, currentProject()).copy(landed = LandedItem(copyId, nextLandingSerial++)) }
        announce(VideoEdit.DUPLICATE, state.value.timeline.nameOf(copyId))
    }

    // Screen readers' Move earlier and Move later on a main clip.
    private fun moveSelectedClip(steps: Int) {
        val itemId = state.value.selectedItemId ?: return
        val clipIds = currentProject()?.mainClipIds() ?: return
        val from = clipIds.indexOf(itemId)
        if (from < 0) return
        preview?.pause()
        val explanation = when {
            clipIds.size < 2 -> Explanation.REORDER_ONE_CLIP
            from + steps < 0 -> Explanation.ALREADY_FIRST
            from + steps > clipIds.lastIndex -> Explanation.ALREADY_LAST
            else -> null
        }
        if (explanation != null) return showToast(ToastMessage.Explain(explanation))
        if (!commit(VideoEdit.REORDER) { it.moveMainClip(itemId, steps) }) return
        // The moved clip comes into view under the playhead (spec P1-06 → Reorder → Finishing).
        state.value.timeline.item(itemId)?.let { preview?.seekTo(it.startUs) }
        announce(VideoEdit.REORDER, state.value.timeline.nameOf(itemId))
    }

    // Reorder mode (spec P1-06 → Reorder): the order lives here until Done or the drop commits it as one step.
    private fun startReorder(itemId: String, byDrag: Boolean) {
        val clipIds = currentProject()?.mainClipIds() ?: return
        if (itemId !in clipIds) return
        preview?.pause()
        if (clipIds.size < 2) return showToast(ToastMessage.Explain(Explanation.REORDER_ONE_CLIP))
        val order = clipIds.toImmutableList()
        updateState { it.withSelection(itemId, currentProject()).copy(reorder = ReorderUi(order, itemId, byDrag, order)) }
    }

    private fun moveInReorder(index: Int) {
        val reorder = state.value.reorder ?: return
        val to = index.coerceIn(0, reorder.order.lastIndex)
        if (to == reorder.movingIndex) return
        val order = reorder.order.toMutableList().apply { add(to, removeAt(reorder.movingIndex)) }.toImmutableList()
        updateState { it.copy(reorder = reorder.copy(order = order)) }
        showToast(ToastMessage.ReorderPosition(to + 1, order.size), spokenOnly = true)
    }

    private fun stepInReorder(steps: Int) {
        val reorder = state.value.reorder ?: return
        val to = reorder.movingIndex + steps
        when {
            to < 0 -> showToast(ToastMessage.Explain(Explanation.ALREADY_FIRST))
            to > reorder.order.lastIndex -> showToast(ToastMessage.Explain(Explanation.ALREADY_LAST))
            else -> moveInReorder(to)
        }
    }

    private fun finishReorder() {
        val reorder = state.value.reorder ?: return
        updateState { it.copy(reorder = null) }
        // An unchanged order records nothing.
        if (reorder.order == reorder.originalOrder) return
        if (!commit(VideoEdit.REORDER) { it.reorderMainClips(reorder.order) }) return
        // The moved clip comes into view under the playhead.
        state.value.timeline.item(reorder.movingId)?.let { preview?.seekTo(it.startUs) }
        announce(VideoEdit.REORDER, state.value.timeline.nameOf(reorder.movingId))
    }

    private fun startTrim(itemId: String, edge: TrimEdge) {
        preview?.pause()
        trimmedEdge = edge
        trimmedClip = currentProject()?.video?.tracks.orEmpty().firstNotNullOfOrNull { track ->
            track.items.firstOrNull { it.id == itemId } as? MediaClip
        }
    }

    // Video and photo clips show the frame at the moving edge; sounds and captions leave the preview where it is.
    private fun showTrimFrame(edgeUs: Long) {
        val clip = trimmedClip ?: return
        val sourceUs = when {
            clip.source.kind == MediaKind.IMAGE -> 0L
            trimmedEdge == TrimEdge.START -> clip.trimInUs + (edgeUs - clip.startUs)
            // The new last frame starts one frame before the new end.
            else -> clip.trimOutUs + (edgeUs - (clip.startUs + clip.durationUs)) - FRAME_US
        }
        preview?.showTrimFrame(clip, sourceUs.coerceAtLeast(0))
    }

    private fun endTrim(itemId: String, edge: TrimEdge, edgeUs: Long, playheadUs: Long) {
        stopTrimFrames(playheadUs)
        if (!commit(VideoEdit.TRIM) { it.trimItem(itemId, edge, edgeUs) }) return
        announce(VideoEdit.TRIM, state.value.timeline.nameOf(itemId))
    }

    private fun cancelTrim(playheadUs: Long) = stopTrimFrames(playheadUs)

    // After release the preview returns to the playhead's frame (spec P1-06 → Trim → Preview while trimming).
    private fun stopTrimFrames(playheadUs: Long) {
        if (trimmedClip != null) preview?.endTrimFrames()
        trimmedClip = null
        preview?.seekTo(playheadUs)
    }

    private fun trimTo(itemId: String, edge: TrimEdge, edgeUs: Long) {
        preview?.pause()
        if (!commit(VideoEdit.TRIM) { it.trimItem(itemId, edge, edgeUs) }) return
        announce(VideoEdit.TRIM, state.value.timeline.nameOf(itemId))
    }

    /**
     * Applies one edit as one undo step and shows it on the timeline at once, without waiting for the session's
     * state to come round. Returns false when the edit changed nothing (no step, no `tool_use`).
     */
    private fun commit(edit: VideoEdit, change: (Project) -> Project): Boolean {
        val session = editSession ?: return false
        val recorded = session.commit(edit.tool, change)
        if (recorded) showEditState(session.state.value)
        return recorded
    }

    // Polite and invisible: the timeline already shows what happened.
    private fun announce(edit: VideoEdit, item: ItemName?) {
        if (item != null) showToast(ToastMessage.Edited(edit, item), spokenOnly = true)
    }

    private fun currentProject(): Project? = editSession?.state?.value?.project

    // The preview's time is the timeline's: scrubs, seeks and playback all go through it.
    private fun currentPositionUs(): Long = preview?.positionUs?.value ?: 0L

    private fun newId(): String = Uuid.random().toString()

    private fun undo() {
        val session = editSession ?: return
        val tool = session.state.value.takeIf { it.canUndo }?.undoTool ?: return
        preview?.pause()
        session.undo()
        VideoEdit.of(tool)?.let { showToast(ToastMessage.Undone(it)) }
    }

    private fun redo() {
        val session = editSession ?: return
        val tool = session.state.value.takeIf { it.canRedo }?.redoTool ?: return
        preview?.pause()
        session.redo()
        VideoEdit.of(tool)?.let { showToast(ToastMessage.Redone(it)) }
    }

    private fun makeDebugEdit() {
        val edit = debugEdit ?: return
        preview?.pause()
        commit(VideoEdit.TRIM, edit::apply)
    }

    /** Counts one second from the first edit the preview hasn't shown; a quick run of edits doesn't restart it. */
    private fun showCatchingUp(catchingUp: Boolean) {
        if (catchingUp) {
            if (catchUpTimer == null) {
                catchUpTimer = viewModelScope.launch {
                    delay(PREVIEW_UPDATING_AFTER)
                    updateState { it.copy(previewUpdating = true) }
                }
            }
            return
        }
        catchUpTimer?.cancel()
        catchUpTimer = null
        if (state.value.previewUpdating) updateState { it.copy(previewUpdating = false) }
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
    private fun showToast(message: ToastMessage, spokenOnly: Boolean = false) {
        val id = nextToastId++
        updateState { it.copy(toast = EditorToast(id, message, isSpokenOnly = spokenOnly || it.saveBanner != null)) }
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

        /** Up to a second a delay needs no feedback (spec P1-06 → Preview catching up, Nielsen's limits). */
        val PREVIEW_UPDATING_AFTER = 1.seconds

        /** One frame at the export's frame rate. */
        const val FRAME_US = 1_000_000L / ExportSettings.DEFAULT_FRAME_RATE
    }
}

/**
 * [itemId] selected when the timeline holds it, with that item's clip tools and where [project] lets its handles
 * trim it; null (or a vanished item) clears all three.
 */
private fun VideoEditorUiState.withSelection(itemId: String?, project: Project?): VideoEditorUiState {
    val track = itemId?.let(timeline::trackOf) ?: return copy(selectedItemId = null, clipTools = null, trimRanges = null)
    val start = project?.trimRange(itemId, TrimEdge.START)
    val end = project?.trimRange(itemId, TrimEdge.END)
    val ranges = if (start != null && end != null) TrimRanges(start, end) else null
    return copy(selectedItemId = itemId, clipTools = clipToolsFor(track.kind), trimRanges = ranges)
}

private fun clipToolsFor(kind: TrackKind): ClipTools = when (kind) {
    TrackKind.MAIN_VIDEO -> ClipTools.MAIN_CLIP
    TrackKind.TEXT -> ClipTools.TEXT
    TrackKind.OVERLAY, TrackKind.STICKER, TrackKind.MEME_SOUND, TrackKind.AUDIO, TrackKind.EFFECT -> ClipTools.OTHER
}
