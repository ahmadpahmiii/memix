package app.memix.core.domain.project

import app.memix.core.domain.AppError
import app.memix.core.domain.EditorTool
import app.memix.core.model.project.Project

/** What an editor shows from its [ProjectEditSession]. Immutable; each change is a new state. */
class ProjectEditState internal constructor(
    internal val history: ProjectHistory,
    /**
     * Why the newest change isn't saved: [AppError.StorageFull], or [AppError.Unexpected]. Null while saving
     * works. The change stays in memory and the next change tries again.
     */
    val saveFailure: AppError?,
) {
    val project: Project get() = history.current
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    /** The tool given to [ProjectEditSession.commit] for the edit [ProjectEditSession.undo] would take back. */
    val undoTool: EditorTool? get() = history.undoTool

    /** The tool of the edit [ProjectEditSession.redo] would put back. */
    val redoTool: EditorTool? get() = history.redoTool

    internal fun withHistory(history: ProjectHistory) = ProjectEditState(history, saveFailure)

    internal fun withSaveFailure(saveFailure: AppError?) = ProjectEditState(history, saveFailure)
}
