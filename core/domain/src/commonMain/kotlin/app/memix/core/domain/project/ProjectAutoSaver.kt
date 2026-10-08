package app.memix.core.domain.project

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.model.project.Project
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Saves the newest version of a session's project [AUTOSAVE_DELAY] after the last change, and at once on
 * [saveNow] or [close].
 *
 * Every write runs on [scope], which must outlive the editor (the app's scope) so the save started by
 * [close] still finishes after the editor is gone. Writes run one at a time, so an older version can never
 * land after a newer one. A failed write keeps the project waiting: the next change, [saveNow] or [close]
 * tries again, and [onSaveFailureChanged] reports the failure, then `null` once a write works.
 */
internal class ProjectAutoSaver(
    private val saveProject: SaveProjectUseCase,
    private val scope: CoroutineScope,
    private val onSaveFailureChanged: (AppError?) -> Unit,
) {
    // The newest version to save; null until the first change. Handed over from the main thread.
    private val newest = MutableStateFlow<Project?>(null)
    private val writeLock = Mutex()

    // What this session last put in the database. Read and written only while holding writeLock.
    private var lastWritten: Project? = null

    private val waitForPause: Job = scope.launch {
        newest.filterNotNull().collectLatest {
            // A newer change cancels this wait and starts a new one, so the write follows the last change.
            delay(AUTOSAVE_DELAY)
            // A write that has started finishes even if a change arrives meanwhile; the next wait starts after it.
            withContext(NonCancellable) { writeNewest() }
        }
    }

    /** Takes the newest version; cheap enough for the main thread. Encoding and writing happen later, off it. */
    fun onProjectChanged(project: Project) {
        newest.value = project
    }

    fun saveNow(): Job = scope.launch { writeNewest() }

    fun close() {
        waitForPause.cancel()
        scope.launch { writeNewest() }
    }

    private suspend fun writeNewest() = writeLock.withLock {
        val project = newest.value
        if (project == null || project === lastWritten) return@withLock
        when (val outcome = saveProject(project)) {
            is Outcome.Success -> {
                lastWritten = project
                onSaveFailureChanged(null)
            }
            is Outcome.Failure -> onSaveFailureChanged(outcome.error)
        }
    }

    companion object {
        /** CLAUDE.md rule 6: auto-save 500 ms after the last change. */
        val AUTOSAVE_DELAY = 500.milliseconds
    }
}
