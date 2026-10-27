package app.memix.core.domain.drafts

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.media.LeftoverMediaCleaner
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaPaths
import app.memix.core.domain.project.ProjectRepository
import app.memix.core.domain.project.RemovedDraft
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Deletes a draft from the drafts list with a way back (spec P1-14 → Delete): the row goes at once, its saved row
 * stays in memory and its media folder on disk for the undo window, then the folder is deleted. [undo] puts the row
 * back exactly as it was (same id, times, name and thumbnail), so it returns to its place. Never through
 * `SaveProjectUseCase`, which would mark it edited.
 *
 * One delete waits at a time: a second delete ends the first one's window at once. The window and the folder delete
 * run in [scope], the app's, so leaving the screen or rotating can't strand a folder; if the app dies first, the next
 * start's [LeftoverMediaCleaner] deletes the folder of a draft that's no longer saved. A Koin single; call from any
 * thread.
 */
class DeleteDraftUseCase(
    private val repository: ProjectRepository,
    private val mediaFiles: MediaFiles,
    private val leftoverCleaner: LeftoverMediaCleaner,
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()

    // Guarded by lock.
    private var waiting: RemovedDraft? = null
    private var window: Job? = null

    private val mutableUndoable = MutableStateFlow<UndoableDelete?>(null)

    /** The delete that can still be undone, or null; the drafts screen shows its toast while it's set. */
    val undoable: StateFlow<UndoableDelete?> = mutableUndoable.asStateFlow()

    /** Deletes [draftId]'s row now and its media folder after [undoWindow]. Fails with NotFound for an unknown id. */
    suspend operator fun invoke(draftId: String, undoWindow: Duration): Outcome<Unit> {
        // The cleanup at start deletes folders of drafts that aren't saved, which this one isn't during its window.
        leftoverCleaner.awaitDone()
        return lock.withLock {
            window?.cancel()
            deleteWaitingFolderLocked()
            when (val removed = repository.remove(draftId)) {
                is Outcome.Failure -> removed
                is Outcome.Success -> {
                    waiting = removed.value
                    mutableUndoable.value = UndoableDelete(draftId)
                    window = scope.launch {
                        delay(undoWindow)
                        endWindow(removed.value)
                    }
                    Outcome.Success(Unit)
                }
            }
        }
    }

    /** Puts [draftId] back if its delete can still be undone; fails with NotFound once the window has ended. */
    suspend fun undo(draftId: String): Outcome<Unit> = lock.withLock {
        val removed = waiting?.takeIf { it.summary.id == draftId } ?: return@withLock Outcome.Failure(AppError.NotFound)
        val restored = repository.putBack(removed)
        if (restored is Outcome.Success) {
            window?.cancel()
            waiting = null
            mutableUndoable.value = null
        }
        restored
    }

    // A window cancelled meanwhile finds another draft waiting, or none, and leaves it alone.
    private suspend fun endWindow(removed: RemovedDraft) = withContext(NonCancellable) {
        lock.withLock { if (waiting === removed) deleteWaitingFolderLocked() }
    }

    private suspend fun deleteWaitingFolderLocked() {
        val removed = waiting ?: return
        waiting = null
        mutableUndoable.value = null
        mediaFiles.deleteFolder(MediaPaths.projectFolder(removed.summary.id))
    }
}

/** A deleted draft whose delete can still be undone. */
data class UndoableDelete(val draftId: String)
