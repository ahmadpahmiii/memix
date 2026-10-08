package app.memix.feature.videoeditor.importmedia

import androidx.lifecycle.viewModelScope
import app.memix.core.domain.Outcome
import app.memix.core.domain.media.CopyRun
import app.memix.core.domain.media.ImportBatch
import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.domain.media.ItemOutcome
import app.memix.core.domain.media.MAX_ITEMS_PER_PICK
import app.memix.core.domain.media.SaveGalleryProjectUseCase
import app.memix.core.domain.media.SpaceCheck
import app.memix.core.domain.project.CreateVideoProjectUseCase
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.Project
import app.memix.core.ui.MemixViewModel
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The first import of a new video project (Create → Video meme): copies what the user picked, shows the import
 * sheet when copying takes a while, saves the project once something went in, and hands the project to the editor.
 *
 * Lives as long as the app's screen (activity), so copying carries on across rotation and in the background.
 * The picker itself is opened by the app, which sends the result here as [ImportIntent.Picked].
 */
class MediaImportViewModel(
    private val createVideoProject: CreateVideoProjectUseCase,
    private val importMedia: ImportMediaUseCase,
    private val saveGalleryProject: SaveGalleryProjectUseCase,
) : MemixViewModel<ImportUiState, ImportIntent>(ImportUiState.Idle) {

    private var importJob: Job? = null
    // Kept while "Not enough space" waits for the user to free some, so the copy can carry on where it stopped.
    private var waitingForSpace: Pick? = null
    private var sheetShownAt: TimeMark? = null
    // Set from the moment copying ends until the result shows: the draft may already be saved, so Cancel can no
    // longer take the pick back.
    private var landing = false
    private var canFreeUpSpace = true

    override fun onIntent(intent: ImportIntent) {
        when (intent) {
            is ImportIntent.Picked -> startImport(intent.items)
            ImportIntent.PickerUnavailable -> if (state.value == ImportUiState.Idle) updateState { ImportUiState.NoPicker }
            ImportIntent.Cancel -> cancelImport()
            ImportIntent.Continue -> continueToEditor()
            ImportIntent.Close -> closeResult()
            ImportIntent.AppResumed -> retryIfSpaceWasFreed()
            ImportIntent.FreeUpSpaceUnavailable -> hideFreeUpSpace()
            ImportIntent.EditorOpened -> if (state.value is ImportUiState.OpenEditor) reset()
        }
    }

    private fun startImport(picked: List<MediaOrigin>) {
        if (picked.isEmpty() || state.value != ImportUiState.Idle) return
        val project = createVideoProject()
        val itemCount = min(picked.size, MAX_ITEMS_PER_PICK)
        // Over the limit, the sheet shows at once even for a quick copy, so the user learns some were left out.
        val overLimit = picked.size > MAX_ITEMS_PER_PICK
        if (overLimit) sheetShownAt = TimeSource.Monotonic.markNow()
        updateState { ImportUiState.Copying(overLimit, CopyProgressUi.startingAt(1, itemCount), limitIf(overLimit)) }
        importJob = viewModelScope.launch {
            val showSheetLater = launch {
                delay(SHEET_DELAY)
                showCopyingSheet()
            }
            val batch = importMedia.prepare(picked, project.id)
            copyWhenThereIsSpace(Pick(project, batch))
            showSheetLater.cancel()
        }
    }

    private fun showCopyingSheet() {
        if ((state.value as? ImportUiState.Copying)?.sheetShown != false) return
        sheetShownAt = TimeSource.Monotonic.markNow()
        updateState { if (it is ImportUiState.Copying) it.copy(sheetShown = true) else it }
    }

    private suspend fun copyWhenThereIsSpace(pick: Pick) {
        when (val space = importMedia.checkSpace(pick.batch)) {
            SpaceCheck.Enough -> copy(pick)
            is SpaceCheck.NotEnough -> waitForSpace(pick, space)
        }
    }

    private suspend fun copy(pick: Pick) {
        val tracker = CopyProgressTracker()
        // Called on the copying thread; the tracker keeps it to 10 sheet updates a second.
        val run = importMedia.copyRemaining(pick.batch) { progress ->
            val shown = tracker.publish(progress) ?: return@copyRemaining
            updateState { if (it is ImportUiState.Copying) it.copy(progress = shown) else it }
        }
        when (run) {
            is CopyRun.Finished -> finish(pick.project, run.batch)
            is CopyRun.OutOfSpace -> waitForSpace(Pick(pick.project, run.batch), run.space)
        }
    }

    private fun waitForSpace(pick: Pick, space: SpaceCheck.NotEnough) {
        waitingForSpace = pick
        // The sheet is up from here on, so a quick copy after the retry still keeps it up for MIN_SHEET_TIME.
        if (sheetShownAt == null) sheetShownAt = TimeSource.Monotonic.markNow()
        updateState { ImportUiState.NotEnoughSpace(space.neededBytes, space.freeBytes, canFreeUpSpace) }
    }

    private suspend fun finish(project: Project, batch: ImportBatch) {
        landing = true
        // The copy is done, so a sheet that is up may now show a full bar while it waits out MIN_SHEET_TIME.
        updateState { if (it is ImportUiState.Copying) it.copy(progress = CopyProgressUi.finished(batch.items.size)) else it }
        val notAdded = notAddedRows(batch)
        val added = batch.added
        if (added.isEmpty()) return showResult(ImportUiState.NoneAdded(notAdded))
        val saved = saveGalleryProject(project, added)
        if (saved is Outcome.Failure) {
            // The repository has logged why. The spec has no state for a failed save, so this reads as nothing added.
            importMedia.discard(batch)
            return showResult(ImportUiState.NoneAdded(persistentListOf()))
        }
        val limit = limitIf(batch.isOverLimit)
        val result = if (notAdded.isEmpty() && limit == null) {
            ImportUiState.OpenEditor(project.id)
        } else {
            ImportUiState.SomeNotAdded(project.id, added.size, batch.pickedCount, notAdded, limit)
        }
        showResult(result)
    }

    private suspend fun showResult(result: ImportUiState) {
        // Once up, the sheet stays at least MIN_SHEET_TIME, so it never flashes (like ContentLoadingProgressBar).
        sheetShownAt?.let { shownAt -> delay(MIN_SHEET_TIME - shownAt.elapsedNow()) }
        updateState { result }
    }

    private fun cancelImport() {
        if (state.value !is ImportUiState.Copying || landing) return
        // ImportMediaUseCase deletes this pick's copies as the job stops; no project was saved yet.
        reset()
    }

    private fun continueToEditor() {
        val result = state.value as? ImportUiState.SomeNotAdded ?: return
        updateState { ImportUiState.OpenEditor(result.projectId) }
    }

    private fun closeResult() {
        when (state.value) {
            is ImportUiState.NotEnoughSpace -> discardWaitingCopies()
            is ImportUiState.NoneAdded, ImportUiState.NoPicker -> Unit
            ImportUiState.Idle, is ImportUiState.Copying, is ImportUiState.SomeNotAdded, is ImportUiState.OpenEditor -> return
        }
        reset()
    }

    private fun discardWaitingCopies() {
        val pick = waitingForSpace ?: return
        // If the app closes before this finishes, the next start's leftover cleanup deletes the rest: the
        // project was never saved.
        viewModelScope.launch { importMedia.discard(pick.batch) }
    }

    private fun retryIfSpaceWasFreed() {
        val pick = waitingForSpace ?: return
        if (state.value !is ImportUiState.NotEnoughSpace || importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            when (val space = importMedia.checkSpace(pick.batch)) {
                SpaceCheck.Enough -> {
                    waitingForSpace = null
                    updateState { ImportUiState.Copying(sheetShown = true, startingProgress(pick.batch), limitIf(pick.batch.isOverLimit)) }
                    copy(pick)
                }
                is SpaceCheck.NotEnough -> updateState { ImportUiState.NotEnoughSpace(space.neededBytes, space.freeBytes, canFreeUpSpace) }
            }
        }
    }

    private fun hideFreeUpSpace() {
        canFreeUpSpace = false
        updateState { if (it is ImportUiState.NotEnoughSpace) it.copy(canFreeUpSpace = false) else it }
    }

    private fun reset() {
        importJob?.cancel()
        importJob = null
        waitingForSpace = null
        sheetShownAt = null
        landing = false
        updateState { ImportUiState.Idle }
    }

    private fun limitIf(overLimit: Boolean): Int? = if (overLimit) MAX_ITEMS_PER_PICK else null

    private fun startingProgress(batch: ImportBatch): CopyProgressUi {
        val nextItemNumber = batch.items.indexOfFirst { it.outcome == null } + 1
        return CopyProgressUi.startingAt(nextItemNumber.coerceAtLeast(1), batch.items.size)
    }

    private fun notAddedRows(batch: ImportBatch) = batch.items.mapNotNull { item ->
        val outcome = item.outcome as? ItemOutcome.NotAdded ?: return@mapNotNull null
        NotAddedItemUi(item.file?.displayName, isVideo = item.kind == MediaKind.VIDEO, outcome.reason)
    }.toImmutableList()

    /** A pick and the new, not yet saved project its copies are for. */
    private class Pick(val project: Project, val batch: ImportBatch)

    private companion object {
        val SHEET_DELAY = 300.milliseconds
        val MIN_SHEET_TIME = 500.milliseconds
    }
}
