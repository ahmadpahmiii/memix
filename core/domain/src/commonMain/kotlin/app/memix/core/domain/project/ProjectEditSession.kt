package app.memix.core.domain.project

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.AppError
import app.memix.core.domain.EditorTool
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
    deleteProject: DeleteProjectUseCase,
    private val analytics: Analytics,
    saveScope: CoroutineScope,
) : AutoCloseable {
    private val mutableState = MutableStateFlow(ProjectEditState(ProjectHistory(initial), saveFailure = null))
    val state: StateFlow<ProjectEditState> = mutableState.asStateFlow()

    private val autoSaver = ProjectAutoSaver(saveProject, deleteProject, saveScope, onSaveFailureChanged = ::showSaveFailure)

    /**
     * Applies one user action as one undo step. [edit] gets the current project and returns the edited copy,
     * built with new lists, never by changing the one it got. A result equal to the current project adds no
     * step. If [edit] throws, nothing changes.
     *
     * [editName] is a stable id the editor picks for the action (its `tool_use` id, such as "split"); the editor
     * turns it back into words for "Undo: Split" through [ProjectEditState.undoEditName] and
     * [ProjectEditState.redoEditName]. It is never shown as it is.
     *
     * A drag, trim or pinch is one step: keep its in-between positions in UI state and commit once, when the
     * finger lifts (mobile-performance skill), so 60 frames of dragging are one undo, not 60.
     */
    fun commit(editName: String, edit: (Project) -> Project) {
        changeHistory { history -> history.record(editName, edit(history.current)) }
    }

    /** Goes back one step. Does nothing when [ProjectEditState.canUndo] is false. */
    fun undo() {
        if (changeHistory(ProjectHistory::undo)) logToolUse(EditorTool.UNDO)
    }

    /** Goes forward one undone step. Does nothing when [ProjectEditState.canRedo] is false. */
    fun redo() {
        if (changeHistory(ProjectHistory::redo)) logToolUse(EditorTool.REDO)
    }

    /**
     * Saves the newest change now instead of 500 ms after it. Call it when the app goes to the background
     * (ON_STOP): the system can end a background app without warning. Join the job to wait for the write.
     */
    fun saveNow(): Job = autoSaver.saveNow()

    /**
     * Saves the newest change, then stops saving; the write finishes even after the editor is gone. A video
     * project with no clip left ([hasNoClips]) is deleted instead of saved, so leaving it doesn't keep an empty
     * draft. Nothing is written after this.
     */
    override fun close() {
        val project = mutableState.value.project
        if (project.hasNoClips()) autoSaver.closeDeletingDraft(project.id) else autoSaver.close()
    }

    /** Returns whether the history changed. */
    private fun changeHistory(change: (ProjectHistory) -> ProjectHistory): Boolean {
        val history = mutableState.value.history
        val changed = change(history)
        if (changed === history) return false
        // Edits only come from the main thread. Saving changes saveFailure from another thread, so the
        // update keeps whatever it set.
        mutableState.update { it.withHistory(changed) }
        autoSaver.onProjectChanged(changed.current)
        return true
    }

    // PRD tool_use: undo and redo count only when they change the project (PM, 8 Oct 2026).
    private fun logToolUse(tool: EditorTool) {
        analytics.log(AnalyticsEvent.ToolUse(editor = mutableState.value.project.type, tool = tool))
    }

    private fun showSaveFailure(failure: AppError?) {
        mutableState.update { if (it.saveFailure == failure) it else it.withSaveFailure(failure) }
    }
}
