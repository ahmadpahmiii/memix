package app.memix.core.domain.project

import app.memix.core.model.project.ProjectSummary
import kotlinx.coroutines.flow.Flow

/** The drafts list: every saved project, last edited first, updated after each save or delete. */
class ObserveProjectSummariesUseCase(private val repository: ProjectRepository) {
    operator fun invoke(): Flow<List<ProjectSummary>> = repository.observeSummaries()
}
