package app.memix.core.domain.project

import app.memix.core.domain.AppError
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

    internal fun withHistory(history: ProjectHistory) = ProjectEditState(history, saveFailure)

    internal fun withSaveFailure(saveFailure: AppError?) = ProjectEditState(history, saveFailure)
}
