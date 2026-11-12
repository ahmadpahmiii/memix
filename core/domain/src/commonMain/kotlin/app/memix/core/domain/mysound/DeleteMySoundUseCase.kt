package app.memix.core.domain.mysound

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.media.MediaFiles
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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

/** How long a deleted My sound can be brought back: the one window the Undo toast and the file's deletion share (never shorter than a user's "Time to take action", as P1-14). */
val MY_SOUND_UNDO_WINDOW: Duration = 8.seconds

/**
 * Deletes a My sound with a way back (spec P3-07 → Details): the row goes at once, its file stays on disk for the undo
 * window, then the file is deleted. [undo] puts the row back as it was, so it returns to its place. Drafts that used the
 * sound are untouched: each owns its own copy.
 *
 * One delete waits at a time: a second delete ends the first one's window at once. The window runs in [scope], the app's,
 * so leaving the screen can't strand a file; if the app dies first, the next start's [LeftoverMySoundsCleaner] deletes it.
 * A Koin single; call from any thread.
 */
class DeleteMySoundUseCase(
    private val repository: MySoundsRepository,
    private val mediaFiles: MediaFiles,
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()

    // Guarded by lock.
    private var waiting: MySound? = null
    private var window: Job? = null

    private val mutableUndoable = MutableStateFlow<MySound?>(null)

    /** The delete that can still be undone, or null. */
    val undoable: StateFlow<MySound?> = mutableUndoable.asStateFlow()

    /** Deletes [id]'s row now and its file after [undoWindow]; returns the sound that went. Fails with NotFound for an unknown id. */
    suspend operator fun invoke(id: String, undoWindow: Duration): Outcome<MySound> = lock.withLock {
        window?.cancel()
        deleteWaitingFileLocked()
        val removed = repository.remove(id) ?: return@withLock Outcome.Failure(AppError.NotFound)
        waiting = removed
        mutableUndoable.value = removed
        window = scope.launch {
            delay(undoWindow)
            endWindow(removed)
        }
        Outcome.Success(removed)
    }

    /** Puts [id] back if its delete can still be undone; fails with NotFound once the window has ended. */
    suspend fun undo(id: String): Outcome<Unit> = lock.withLock {
        val removed = waiting?.takeIf { it.id == id } ?: return@withLock Outcome.Failure(AppError.NotFound)
        repository.putBack(removed)
        window?.cancel()
        waiting = null
        mutableUndoable.value = null
        Outcome.Success(Unit)
    }

    // A window cancelled meanwhile finds another sound waiting, or none, and leaves it alone.
    private suspend fun endWindow(removed: MySound) = withContext(NonCancellable) {
        lock.withLock { if (waiting === removed) deleteWaitingFileLocked() }
    }

    private suspend fun deleteWaitingFileLocked() {
        val removed = waiting ?: return
        waiting = null
        mutableUndoable.value = null
        mediaFiles.delete(listOf(removed.path))
    }

}
