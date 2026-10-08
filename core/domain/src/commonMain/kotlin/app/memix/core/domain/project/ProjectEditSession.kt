package app.memix.core.domain.project

import app.memix.core.domain.AppError
import app.memix.core.model.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One editor's working copy of a project, shared by the video and photo editors. It applies edits, keeps
 * [ProjectHistory.MAX_UNDO_STEPS] steps of undo and redo in memory, and saves every change by itself:
 * 500 ms after the last one, and at once on [saveNow] and [close].
 *
 * Start one with [StartEditSessionUseCase]. The editor's ViewModel copies [state] into its own UiState and
 * registers the session with `addCloseable`, so closing the editor saves the newest change. Call [commit],
 * [undo] and [redo] from the main thread; they only swap references, and saving happens off it.
 *
 * If the process dies, only the changes of the last 500 ms (and a write still in progress) are lost; the
 * saved draft is never half-written. The undo history isn't saved.
 */
class ProjectEditSession internal constructor(
    initial: Project,
    saveProject: SaveProjectUseCase,
    saveScope: CoroutineScope,
) : AutoCloseable {
    private val mutableState = MutableStateFlow(ProjectEditState(ProjectHistory(initial), saveFailure = null))
    val state: StateFlow<ProjectEditState> = mutableState.asStateFlow()

    private val autoSaver = ProjectAutoSaver(saveProject, saveScope, onSaveFailureChanged = ::showSaveFailure)

    /**
     * Applies one user action as one undo step. [edit] gets the current project and returns the edited copy,
     * built with new lists, never by changing the one it got. A result equal to the current project adds no
     * step. If [edit] throws, nothing changes.
     *
     * A drag, trim or pinch is one step: keep its in-between positions in UI state and commit once, when the
     * finger lifts (mobile-performance skill), so 60 frames of dragging are one undo, not 60.
     */
    fun commit(edit: (Project) -> Project) = changeHistory { history -> history.record(edit(history.current)) }

    /** Goes back one step. Does nothing when [ProjectEditState.canUndo] is false. */
    fun undo() = changeHistory(ProjectHistory::undo)

    /** Goes forward one undone step. Does nothing when [ProjectEditState.canRedo] is false. */
    fun redo() = changeHistory(ProjectHistory::redo)

    /**
     * Saves the newest change now instead of 500 ms after it. Call it when the app goes to the background
     * (ON_STOP): the system can end a background app without warning. Join the job to wait for the write.
     */
    fun saveNow(): Job = autoSaver.saveNow()

    /** Saves the newest change, then stops saving. The write finishes even after the editor is gone. */
    override fun close() = autoSaver.close()

    private fun changeHistory(change: (ProjectHistory) -> ProjectHistory) {
        val history = mutableState.value.history
        val changed = change(history)
        if (changed === history) return
        // Edits only come from the main thread. Saving changes saveFailure from another thread, so the
        // update keeps whatever it set.
        mutableState.update { it.withHistory(changed) }
        autoSaver.onProjectChanged(changed.current)
    }

    private fun showSaveFailure(failure: AppError?) {
        mutableState.update { if (it.saveFailure == failure) it else it.withSaveFailure(failure) }
    }
}
