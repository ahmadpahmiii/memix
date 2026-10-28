package app.memix.core.domain.project

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.AppError
import app.memix.core.domain.EditorTool
import app.memix.core.domain.ProjectSource
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
 *
 * A project that only exists in memory ([newSource] set: a blank photo canvas) isn't saved until a committed step leaves
 * something in it, and that step logs `project_create` with [newSource] (spec P2-01 → Blank canvas).
 */
class ProjectEditSession internal constructor(
    initial: Project,
    saveProject: SaveProjectUseCase,
    deleteProject: DeleteProjectUseCase,
    private val analytics: Analytics,
    saveScope: CoroutineScope,
    private var newSource: ProjectSource? = null,
) : AutoCloseable {
    private val mutableState = MutableStateFlow(ProjectEditState(ProjectHistory(initial), saveFailure = null))
    val state: StateFlow<ProjectEditState> = mutableState.asStateFlow()

    private val autoSaver = ProjectAutoSaver(
        saveProject, deleteProject, saveScope,
        waitsForFirstContent = newSource != null,
        onSaveFailureChanged = ::showSaveFailure,
    )

    /**
     * Applies one user action as one undo step. [edit] gets the current project and returns the edited copy,
     * built with new lists, never by changing the one it got. A result equal to the current project adds no
     * step. If [edit] throws, nothing changes.
     *
     * [tool] names the step: the editor turns it into words for "Undo: Split" through [ProjectEditState.undoTool]
     * and [ProjectEditState.redoTool]. A recorded step logs `tool_use` for [tool] once, unless [tool] has no event
     * value (its own event counts it); an edit that changes nothing logs nothing (PRD analytics, PM 8 Oct 2026).
     * Returns whether a step was recorded. [font] goes with `tool_use` for the caption tools that name one
     * (`add_text`, `text_font`), [value] for the canvas tools (`canvas_ratio`, `canvas_fit`, `canvas_background`).
     *
     * A drag, trim or pinch is one step: keep its in-between positions in UI state and commit once, when the
     * finger lifts (mobile-performance skill), so 60 frames of dragging are one undo, not 60.
     */
    fun commit(tool: EditorTool, font: String? = null, value: String? = null, edit: (Project) -> Project): Boolean {
        val recorded = changeHistory { history -> history.record(tool, edit(history.current)) }
        if (recorded) {
            logProjectCreateOnFirstContent()
            logToolUse(tool, font, value)
        }
        return recorded
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
     * Saves the newest change, then stops saving; the write finishes even after the editor is gone. A project with
     * nothing left in it ([isEmptyDraft]: a video with no clip, a photo with no layer) is deleted instead of saved, so
     * leaving it doesn't keep an empty draft. Nothing is written after this.
     */
    override fun close() {
        val project = mutableState.value.project
        if (project.isEmptyDraft()) autoSaver.closeDeletingDraft(project.id) else autoSaver.close()
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

    // A project made in memory counts as created when it first has something in it, as one saved from the gallery does.
    private fun logProjectCreateOnFirstContent() {
        val source = newSource ?: return
        val project = mutableState.value.project
        if (project.isEmptyDraft()) return
        newSource = null
        analytics.log(AnalyticsEvent.ProjectCreate(editor = project.type, source = source))
    }

    // PRD tool_use: every tool, undo and redo included, counts only when it changes the project (PM, 8 Oct 2026).
    // A step without an event value is counted by its own event (a landed sound: sound_add).
    private fun logToolUse(tool: EditorTool, font: String? = null, value: String? = null) {
        if (tool.eventValue == null) return
        analytics.log(AnalyticsEvent.ToolUse(editor = mutableState.value.project.type, tool = tool, font = font, value = value))
    }

    private fun showSaveFailure(failure: AppError?) {
        mutableState.update { if (it.saveFailure == failure) it else it.withSaveFailure(failure) }
    }
}
