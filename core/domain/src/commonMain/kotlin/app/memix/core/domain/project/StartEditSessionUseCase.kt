package app.memix.core.domain.project

import app.memix.core.domain.Analytics
import app.memix.core.model.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * Starts editing [Project]s in either editor: a new one from [CreateVideoProjectUseCase] or a draft from
 * [OpenVideoDraftUseCase]. The session undoes, redoes and saves by itself (see [ProjectEditSession]).
 */
class StartEditSessionUseCase(
    private val saveProject: SaveProjectUseCase,
    private val deleteProject: DeleteProjectUseCase,
    private val analytics: Analytics,
    /** Outlives every screen, so the save that closing an editor starts still finishes. */
    private val appScope: CoroutineScope,
) {
    operator fun invoke(project: Project): ProjectEditSession =
        ProjectEditSession(project, saveProject, deleteProject, analytics, appScope)
}
