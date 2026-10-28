package app.memix.core.domain.project

import app.memix.core.domain.Analytics
import app.memix.core.domain.ProjectSource
import app.memix.core.model.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * Starts editing [Project]s in either editor: a new one from [CreateVideoProjectUseCase] or [CreatePhotoProjectUseCase],
 * or a draft from [OpenVideoDraftUseCase] or [OpenPhotoDraftUseCase]. The session undoes, redoes and saves by itself (see [ProjectEditSession]).
 */
class StartEditSessionUseCase(
    private val saveProject: SaveProjectUseCase,
    private val deleteProject: DeleteProjectUseCase,
    private val analytics: Analytics,
    /** Outlives every screen, so the save that closing an editor starts still finishes. */
    private val appScope: CoroutineScope,
) {
    /**
     * [newSource] is set for a project that only exists in memory, not saved yet (a blank photo canvas): it is saved
     * from its first committed step that leaves something in it, which logs `project_create` with that source.
     */
    operator fun invoke(project: Project, newSource: ProjectSource? = null): ProjectEditSession =
        ProjectEditSession(project, saveProject, deleteProject, analytics, appScope, newSource)
}
