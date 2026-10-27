package app.memix.core.domain.project

import app.memix.core.model.project.ProjectSummary
import kotlinx.coroutines.flow.Flow

/** The drafts list: every saved project, last edited first (the newer one first on a tie), updated after each change. */
class ObserveProjectSummariesUseCase(private val repository: ProjectRepository) {
    operator fun invoke(): Flow<List<ProjectSummary>> = repository.observeSummaries()
}
