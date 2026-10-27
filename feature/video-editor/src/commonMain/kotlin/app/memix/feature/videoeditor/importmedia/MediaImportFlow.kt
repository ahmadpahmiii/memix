package app.memix.feature.videoeditor.importmedia

import app.memix.core.domain.AppError
import app.memix.core.domain.media.CopyRun
import app.memix.core.domain.media.ImportBatch
import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.domain.media.ItemOutcome
import app.memix.core.domain.media.MAX_ITEMS_PER_PICK
import app.memix.core.domain.media.SpaceCheck
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One pick's way into a project (spec P1-02 → States): copies what the user picked, shows the import sheet when copying
 * takes a while, waits for space when the phone is full, hands the copies to an [ImportLanding] once they're in, and
 * shows the result. Used by Create → Video meme ([MediaImportViewModel]) and by the editor's Add media and Replace
 * (P1-16), which differ only in what landing means.
 *
 * Runs in its owner's [scope], so copying carries on as long as the owner lives. Main thread only.
 */
internal class MediaImportFlow(
    private val importMedia: ImportMediaUseCase,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = mutableState.asStateFlow()

    private var importJob: Job? = null
    // Kept while "Not enough space" waits for the user to free some, so the copy can carry on where it stopped.
    private var waitingForSpace: Pick? = null
    private var sheetShownAt: TimeMark? = null
    // Set from the moment copying ends until the result shows: the media may already be in the project, so Cancel
    // can no longer take the pick back.
    private var landing = false
    private var canFreeUpSpace = true

    /**
     * Starts copying [picked] into [projectId]'s media folder; [lander] puts the copies into the project. Does nothing
     * when [picked] is empty or another pick is still running.
     */
    fun start(picked: List<MediaOrigin>, projectId: String, lander: ImportLanding) {
        if (picked.isEmpty() || state.value != ImportUiState.Idle) return
        val itemCount = min(picked.size, MAX_ITEMS_PER_PICK)
        // Over the limit, the sheet shows at once even for a quick copy, so the user learns some were left out.
        val overLimit = picked.size > MAX_ITEMS_PER_PICK
        if (overLimit) sheetShownAt = TimeSource.Monotonic.markNow()
        updateState { ImportUiState.Copying(overLimit, CopyProgressUi.startingAt(1, itemCount), limitIf(overLimit)) }
        importJob = scope.launch {
            val showSheetLater = launch {
                delay(SHEET_DELAY)
                showCopyingSheet()
            }
            val batch = importMedia.prepare(picked, projectId)
            copyWhenThereIsSpace(Pick(batch, lander))
            showSheetLater.cancel()
        }
    }

    /** Everything but [ImportIntent.Picked], which the owner turns into [start]. */
    fun onIntent(intent: ImportIntent) {
        when (intent) {
            is ImportIntent.Picked -> Unit
            ImportIntent.PickerUnavailable -> if (state.value == ImportUiState.Idle) updateState { ImportUiState.NoPicker }
            ImportIntent.Cancel -> cancelImport()
            ImportIntent.Continue -> continueAfterPartialResult()
            ImportIntent.Close -> closeResult()
            ImportIntent.AppResumed -> retryIfSpaceWasFreed()
            ImportIntent.FreeUpSpaceUnavailable -> hideFreeUpSpace()
            ImportIntent.Finish -> if (state.value is ImportUiState.Done) reset()
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
            is CopyRun.Finished -> finish(pick.copy(batch = run.batch))
            is CopyRun.OutOfSpace -> waitForSpace(pick.copy(batch = run.batch), run.space)
        }
    }

    private fun waitForSpace(pick: Pick, space: SpaceCheck.NotEnough) {
        waitingForSpace = pick
        // The sheet is up from here on, so a quick copy after the retry still keeps it up for MIN_SHEET_TIME.
        if (sheetShownAt == null) sheetShownAt = TimeSource.Monotonic.markNow()
        updateState { ImportUiState.NotEnoughSpace(space.neededBytes, space.freeBytes, canFreeUpSpace) }
    }

    private suspend fun finish(pick: Pick) {
        landing = true
        // The copy is done, so a sheet that is up may now show a full bar while it waits out MIN_SHEET_TIME.
        updateState { if (it is ImportUiState.Copying) it.copy(progress = CopyProgressUi.finished(pick.batch.items.size)) else it }
        if (pick.batch.added.isEmpty()) return showResult(ImportUiState.NoneAdded(notAddedRows(pick.batch)))
        land(pick.copy(copied = true))
    }

    private suspend fun land(pick: Pick) {
        when (val result = pick.lander.land(pick.batch.added)) {
            LandingResult.Landed -> showResult(resultOfLanded(pick))
            LandingResult.Declined -> {
                importMedia.discard(pick.batch)
                showResult(ImportUiState.Idle)
                // This job ends here, so only the pick's bookkeeping is cleared.
                landing = false
                sheetShownAt = null
            }
            is LandingResult.Failed -> onLandingFailed(pick, result.error)
        }
    }

    private fun resultOfLanded(pick: Pick): ImportUiState {
        val notAdded = notAddedRows(pick.batch)
        val limit = limitIf(pick.batch.isOverLimit)
        if (notAdded.isNotEmpty() || limit != null) {
            return ImportUiState.SomeNotAdded(pick.batch.projectId, pick.batch.added.size, pick.batch.pickedCount, notAdded, limit)
        }
        return ImportUiState.Done(pick.batch.projectId, sheetShown = sheetShownAt != null)
    }

    private suspend fun onLandingFailed(pick: Pick, error: AppError) {
        if (error != AppError.StorageFull) {
            // The lander has logged why. Rare; the spec keeps "Couldn't add your media" with no rows for it.
            importMedia.discard(pick.batch)
            return showResult(ImportUiState.NoneAdded(persistentListOf()))
        }
        // The files are fine and the phone is full: wait for the headroom like a copy that ran out, keeping the
        // copies, then only land again (spec P1-02 → States, "Ran out of space saving the new project").
        waitForSpace(pick, importMedia.spaceToSaveAgain())
    }

    private suspend fun showResult(result: ImportUiState) {
        // Once up, the sheet stays at least MIN_SHEET_TIME, so it never flashes (like ContentLoadingProgressBar).
        sheetShownAt?.let { shownAt -> delay(MIN_SHEET_TIME - shownAt.elapsedNow()) }
        updateState { result }
    }

    private fun cancelImport() {
        if (state.value !is ImportUiState.Copying || landing) return
        // ImportMediaUseCase deletes this pick's copies as the job stops; nothing was landed yet.
        reset()
    }

    private fun continueAfterPartialResult() {
        val result = state.value as? ImportUiState.SomeNotAdded ?: return
        updateState { ImportUiState.Done(result.projectId, sheetShown = true) }
    }

    private fun closeResult() {
        when (state.value) {
            // While a retry runs (the space check, or the landing after a failed one, which may already have
            // landed), Close waits: its result shows in a moment.
            is ImportUiState.NotEnoughSpace -> if (importJob?.isActive == true) return else discardWaitingCopies()
            is ImportUiState.NoneAdded, ImportUiState.NoPicker -> Unit
            ImportUiState.Idle, is ImportUiState.Copying, is ImportUiState.SomeNotAdded, is ImportUiState.Done -> return
        }
        reset()
    }

    private fun discardWaitingCopies() {
        val pick = waitingForSpace ?: return
        // If the app closes before this finishes, the next start's leftover cleanup deletes the rest: no saved
        // project uses them.
        scope.launch { importMedia.discard(pick.batch) }
    }

    private fun retryIfSpaceWasFreed() {
        val pick = waitingForSpace ?: return
        if (state.value !is ImportUiState.NotEnoughSpace || importJob?.isActive == true) return
        importJob = scope.launch {
            when (val space = importMedia.checkSpace(pick.batch)) {
                SpaceCheck.Enough -> {
                    waitingForSpace = null
                    carryOn(pick)
                }
                is SpaceCheck.NotEnough -> updateState { ImportUiState.NotEnoughSpace(space.neededBytes, space.freeBytes, canFreeUpSpace) }
            }
        }
    }

    // A pick that ran out while copying copies the rest; one whose landing failed only lands again, with the "Not
    // enough space" sheet still up until the result shows.
    private suspend fun carryOn(pick: Pick) {
        if (pick.copied) return land(pick)
        updateState { ImportUiState.Copying(sheetShown = true, startingProgress(pick.batch), limitIf(pick.batch.isOverLimit)) }
        copy(pick)
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

    private fun updateState(transform: (ImportUiState) -> ImportUiState) = mutableState.update(transform)

    private fun limitIf(overLimit: Boolean): Int? = if (overLimit) MAX_ITEMS_PER_PICK else null

    private fun startingProgress(batch: ImportBatch): CopyProgressUi {
        val nextItemNumber = batch.items.indexOfFirst { it.outcome == null } + 1
        return CopyProgressUi.startingAt(nextItemNumber.coerceAtLeast(1), batch.items.size)
    }

    private fun notAddedRows(batch: ImportBatch) = batch.items.mapNotNull { item ->
        val outcome = item.outcome as? ItemOutcome.NotAdded ?: return@mapNotNull null
        NotAddedItemUi(item.file?.displayName, isVideo = item.kind == MediaKind.VIDEO, outcome.reason)
    }.toImmutableList()

    /** A pick and where its copies go. [copied]: every item has been copied and checked. */
    private data class Pick(val batch: ImportBatch, val lander: ImportLanding, val copied: Boolean = false)

    private companion object {
        val SHEET_DELAY = 300.milliseconds
        val MIN_SHEET_TIME = 500.milliseconds
    }
}

/** What an import's owner does with the copies once every item is copied and checked. */
internal fun interface ImportLanding {
    /**
     * Puts [added] (at least one, in pick order) into the project. Called once per pick; again only after a
     * [LandingResult.Failed] with [AppError.StorageFull], once the user has freed space.
     */
    suspend fun land(added: List<MediaRef>): LandingResult
}

/** How landing went. */
internal sealed interface LandingResult {
    /** The media is in the project. */
    data object Landed : LandingResult

    /** The owner didn't use the media and has said why (Replace: the video is too short); the copies are deleted. */
    data object Declined : LandingResult

    /** Saving failed: "Not enough space" for [AppError.StorageFull] (then lands again), else "Couldn't add your media". */
    data class Failed(val error: AppError) : LandingResult
}
