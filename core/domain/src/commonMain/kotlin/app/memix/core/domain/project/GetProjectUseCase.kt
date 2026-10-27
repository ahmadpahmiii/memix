package app.memix.core.domain.project

import app.memix.core.domain.Outcome
import app.memix.core.model.project.Project

/** Opens a saved draft. Fails with NotFound or ProjectUnreadable (see [ProjectRepository.get]). */
class GetProjectUseCase(private val repository: ProjectRepository) {
    suspend operator fun invoke(id: String): Outcome<Project> = repository.get(id)
}
