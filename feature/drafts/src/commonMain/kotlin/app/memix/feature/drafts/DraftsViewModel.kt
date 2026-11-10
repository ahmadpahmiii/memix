package app.memix.feature.drafts

import androidx.lifecycle.viewModelScope
import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.drafts.DeleteDraftUseCase
import app.memix.core.domain.drafts.DescribeEditTimeUseCase
import app.memix.core.domain.drafts.DuplicateDraftUseCase
import app.memix.core.domain.drafts.DuplicateOutcome
import app.memix.core.domain.drafts.ReadDraftThumbnailUseCase
import app.memix.core.domain.drafts.RenameDraftUseCase
import app.memix.core.domain.drafts.UndoableDelete
import app.memix.core.domain.project.ObserveProjectSummariesUseCase
import app.memix.core.model.project.ProjectSummary
import app.memix.core.ui.MemixViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import memix.feature.drafts.generated.resources.Res
import memix.feature.drafts.generated.resources.drafts_copy_name
import memix.feature.drafts.generated.resources.drafts_copy_name_n
import org.jetbrains.compose.resources.getString

/**
 * The Drafts tab (spec docs/ux/specs/P1-14-drafts.md): every saved draft, last edited first, with open, rename,
 * duplicate and delete with undo. Lives with the tabs (the app's main destination), so its sheets and toast can cover
 * the bottom navigation and its list is ready when the tab comes back from an editor.
 *
 * Nothing here marks a draft as edited: rename, duplicate and undo go through use cases that keep the edit times, so
 * the order only changes when a draft is edited (PM, 20 Oct 2026). No analytics event fires (spec → Analytics).
 * [decodeDispatcher] decodes thumbnails off the main thread.
 */
class DraftsViewModel(
    private val observeDrafts: ObserveProjectSummariesUseCase,
    private val renameDraft: RenameDraftUseCase,
    private val duplicateDraft: DuplicateDraftUseCase,
    private val deleteDraft: DeleteDraftUseCase,
    private val describeEditTime: DescribeEditTimeUseCase,
    readThumbnail: ReadDraftThumbnailUseCase,
    decodeDispatcher: CoroutineDispatcher,
) : MemixViewModel<DraftsUiState, DraftsIntent>(
    DraftsUiState(thumbnails = DraftThumbnailImages({ path -> readThumbnail(path) }, decodeDispatcher)),
) {
    private var summaries: List<ProjectSummary> = emptyList()
    private var listJob: Job? = null
    // True once the list has been read, until reading it fails.
    private var listLoaded = false
    private var minuteTick: Job? = null
    private var copyJob: Job? = null
    private var copyingDraftId: String? = null
    private val copyFraction = MutableStateFlow(0f)
    private var nextSerial = 0L
    private var lastEditorFailure = -1L
    // The delete that can still be undone; its toast comes back after another toast has gone (spec → Interactions → Delete).
    private var undoableDelete: UndoableDelete? = null
    // The draft opened from this list, as it was, to tell on return whether it was edited (spec → Entry and exit).
    private var openedDraft: OpenedDraft? = null
    private var returningTo: OpenedDraft? = null

    init {
        viewModelScope.launch { deleteDraft.undoable.collect(::showUndoable) }
    }

    override fun onIntent(intent: DraftsIntent) {
        when (intent) {
            DraftsIntent.Shown -> onShown()
            DraftsIntent.Hidden -> stopMinuteTick()
            DraftsIntent.TryAgain -> startList()
            is DraftsIntent.Open -> open(intent.draftId)
            DraftsIntent.DraftOpened -> updateState { it.copy(openDraftId = null, openDraftType = null) }
            is DraftsIntent.Rename -> startRename(intent.draftId)
            is DraftsIntent.SaveName -> saveName(intent.typedName)
            DraftsIntent.CloseRename -> closeRename(focusRow = true)
            is DraftsIntent.Duplicate -> duplicate(intent.draftId)
            DraftsIntent.CancelCopy -> cancelCopy()
            DraftsIntent.CloseSpaceSheet -> closeSpaceSheet()
            DraftsIntent.FreeUpSpaceUnavailable -> updateState { it.copy(canFreeUpSpace = false) }
            DraftsIntent.AppResumed -> retryCopyAfterSpace()
            is DraftsIntent.Delete -> delete(intent.draftId, intent.undoWindowMillis)
            is DraftsIntent.Undo -> undo(intent.draftId)
            is DraftsIntent.ToastTimedOut -> toastTimedOut(intent.serial)
            is DraftsIntent.FocusDone -> updateState { if (it.focus?.serial == intent.serial) it.copy(focus = null) else it }
            is DraftsIntent.EditorCouldntOpen -> editorCouldntOpen(intent.serial)
        }
    }

    private fun onShown() {
        if (listJob == null) startList() else showRows()
        startMinuteTick()
        val opened = openedDraft ?: return
        openedDraft = null
        returningTo = opened
        checkReturn()
        // The editor's last save lands just after its screen has gone, so the edited draft may move up a moment later.
        viewModelScope.launch {
            delay(RETURN_WATCH)
            if (returningTo?.draftId == opened.draftId) returningTo = null
        }
    }

    // The list is read from the first time the tab shows, not at app start, so the database waits for it.
    private fun startList() {
        listJob?.cancel()
        listLoaded = false
        updateState { it.copy(list = DraftsList.Loading) }
        listJob = viewModelScope.launch {
            observeDrafts()
                .catch { _ ->
                    listLoaded = false
                    updateState { it.copy(list = DraftsList.LoadFailed) }
                }
                .collect { drafts ->
                    summaries = drafts
                    listLoaded = true
                    showRows()
                    checkReturn()
                }
        }
    }

    private fun showRows() {
        if (!listLoaded) return
        val nowUs = describeEditTime.nowEpochUs()
        val rows = summaries.map { draft ->
            DraftRowUi(
                id = draft.id,
                name = draft.name,
                type = draft.type,
                edited = if (draft.needsNewerApp) null else describeEditTime(draft.updatedAtEpochUs, nowUs),
                needsNewerApp = draft.needsNewerApp,
                thumbnailPath = draft.thumbnailPath,
            )
        }
        updateState { it.copy(list = DraftsList.Loaded(rows.toImmutableList())) }
    }

    // One tick for every row (spec → Interactions: relative times update on return and once a minute).
    private fun startMinuteTick() {
        minuteTick?.cancel()
        minuteTick = viewModelScope.launch {
            while (isActive) {
                delay(1.minutes)
                showRows()
            }
        }
    }

    private fun stopMinuteTick() {
        minuteTick?.cancel()
        minuteTick = null
    }

    private fun open(draftId: String) {
        val draft = summaries.firstOrNull { it.id == draftId } ?: return
        if (draft.needsNewerApp) return showToast(DraftsToast.CantOpen(newSerial()))
        openedDraft = OpenedDraft(draft.id, draft.updatedAtEpochUs)
        updateState { it.copy(openDraftId = draftId, openDraftType = draft.type) }
    }

    // Edited in the editor: it's first now, so the list goes to the top; otherwise focus just returns to its row.
    private fun checkReturn() {
        val returning = returningTo ?: return
        val draft = summaries.firstOrNull { it.id == returning.draftId }
        if (draft == null) {
            // The editor deleted it (no clips left) or couldn't open it.
            returningTo = null
            return
        }
        if (draft.updatedAtEpochUs != returning.updatedAtEpochUs) {
            returningTo = null
            requestFocus(draft.id, scrollToTop = true)
        } else if (!returning.focused) {
            returningTo = returning.copy(focused = true)
            requestFocus(draft.id, scrollToTop = false)
        }
    }

    private fun startRename(draftId: String) {
        val draft = summaries.firstOrNull { it.id == draftId } ?: return
        if (draft.needsNewerApp) return showToast(DraftsToast.CantOpen(newSerial()))
        updateState { it.copy(renaming = RenameUi(draft.id, draft.name, draft.type, newSerial())) }
    }

    private fun saveName(typedName: String) {
        val renaming = state.value.renaming ?: return
        closeRename(focusRow = true)
        viewModelScope.launch {
            when (val renamed = renameDraft(renaming.draftId, typedName)) {
                is Outcome.Success -> Unit
                // Saved by a newer app, or damaged: it can't be read, so it can't be renamed either.
                is Outcome.Failure -> showToast(
                    if (renamed.error == AppError.ProjectUnreadable) DraftsToast.CantOpen(newSerial()) else DraftsToast.RenameFailed(newSerial()),
                )
            }
        }
    }

    private fun closeRename(focusRow: Boolean) {
        val renaming = state.value.renaming ?: return
        updateState { it.copy(renaming = null) }
        if (focusRow) requestFocus(renaming.draftId, scrollToTop = false)
    }

    private fun duplicate(draftId: String) {
        val draft = summaries.firstOrNull { it.id == draftId } ?: return
        if (draft.needsNewerApp) return showToast(DraftsToast.CantOpen(newSerial()))
        if (copyJob?.isActive == true) return
        copyingDraftId = draftId
        copyFraction.value = 0f
        val sheetAlreadyUp = state.value.copySheet != null
        copyJob = viewModelScope.launch {
            // A quick duplicate (links, small files) finishes before the sheet would show (spec → States).
            val sheetTimer = launch {
                if (!sheetAlreadyUp) delay(SHEET_DELAY)
                updateState { it.copy(copySheet = CopySheetUi.Copying(CopyProgress(copyFraction.asStateFlow()))) }
            }
            val outcome = try {
                duplicateDraft(draftId, ::copySuffix) { fraction -> copyFraction.value = fraction }
            } finally {
                sheetTimer.cancel()
            }
            showDuplicateOutcome(outcome)
        }
    }

    private suspend fun copySuffix(name: String, number: Int): String =
        if (number == 1) getString(Res.string.drafts_copy_name, name) else getString(Res.string.drafts_copy_name_n, name, number)

    private fun showDuplicateOutcome(outcome: DuplicateOutcome) {
        when (outcome) {
            is DuplicateOutcome.Duplicated -> {
                copyingDraftId = null
                updateState { it.copy(copySheet = null) }
                showToast(DraftsToast.Duplicated(newSerial()))
            }
            is DuplicateOutcome.NotEnoughSpace ->
                updateState { it.copy(copySheet = CopySheetUi.NotEnoughSpace(outcome.neededBytes, outcome.freeBytes)) }
            is DuplicateOutcome.Failed -> {
                copyingDraftId = null
                updateState { it.copy(copySheet = null) }
                showToast(
                    if (outcome.error == AppError.ProjectUnreadable) DraftsToast.CantOpen(newSerial()) else DraftsToast.DuplicateFailed(newSerial()),
                )
            }
        }
    }

    // Cancel deletes the partial copy (the use case cleans up on cancellation).
    private fun cancelCopy() {
        copyJob?.cancel()
        copyJob = null
        copyingDraftId = null
        updateState { it.copy(copySheet = null) }
    }

    private fun closeSpaceSheet() {
        if (state.value.copySheet !is CopySheetUi.NotEnoughSpace) return
        copyingDraftId = null
        updateState { it.copy(copySheet = null) }
    }

    // Back from the storage screen: try the whole duplicate again; it checks the space first.
    private fun retryCopyAfterSpace() {
        if (state.value.copySheet !is CopySheetUi.NotEnoughSpace) return
        copyingDraftId?.let(::duplicate)
    }

    private fun delete(draftId: String, undoWindowMillis: Long) {
        val rows = summaries
        val index = rows.indexOfFirst { it.id == draftId }
        if (index < 0) return
        // Focus goes to the next row, or the previous one at the end (spec → Accessibility).
        val nextFocus = rows.getOrNull(index + 1)?.id ?: rows.getOrNull(index - 1)?.id
        viewModelScope.launch {
            when (val deleted = deleteDraft(draftId, undoWindowMillis.milliseconds)) {
                is Outcome.Success -> if (nextFocus != null) requestFocus(nextFocus, scrollToTop = false)
                // The row stays. NotFound means it was already gone, which needs no message.
                is Outcome.Failure -> if (deleted.error != AppError.NotFound) showToast(DraftsToast.DeleteFailed(newSerial()))
            }
        }
    }

    private fun undo(draftId: String) {
        viewModelScope.launch {
            if (deleteDraft.undo(draftId) is Outcome.Success) requestFocus(draftId, scrollToTop = false)
        }
    }

    // The toast follows the delete that can still be undone; when its window ends it goes.
    private fun showUndoable(undoable: UndoableDelete?) {
        undoableDelete = undoable
        if (undoable != null) return showToast(DraftsToast.Deleted(undoable.draftId, newSerial()))
        updateState { if (it.toast is DraftsToast.Deleted) it.copy(toast = null) else it }
    }

    private fun editorCouldntOpen(serial: Long) {
        if (serial == lastEditorFailure) return
        lastEditorFailure = serial
        showToast(DraftsToast.CantOpen(newSerial()))
    }

    // Another toast may have replaced the Undo while the delete can still be undone: when it goes, the Undo returns.
    private fun toastTimedOut(serial: Long) {
        if (state.value.toast?.serial != serial) return
        val undoAgain = undoableDelete?.let { DraftsToast.Deleted(it.draftId, newSerial()) }
        updateState { if (it.toast?.serial == serial) it.copy(toast = undoAgain) else it }
    }

    // Toasts never stack: a new one replaces the one shown.
    private fun showToast(toast: DraftsToast) = updateState { it.copy(toast = toast) }

    private fun requestFocus(draftId: String, scrollToTop: Boolean) =
        updateState { it.copy(focus = FocusRequestUi(draftId, scrollToTop, newSerial())) }

    private fun newSerial(): Long = ++nextSerial

    private data class OpenedDraft(val draftId: String, val updatedAtEpochUs: Long, val focused: Boolean = false)

    private companion object {
        val SHEET_DELAY = 300.milliseconds
        val RETURN_WATCH = 2_000.milliseconds
    }
}
