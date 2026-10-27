package app.memix.core.domain.project

import app.memix.core.domain.Outcome
import app.memix.core.model.project.Project
import kotlin.time.Clock

/** Saves a draft and marks it as edited now, which is the order the drafts list uses. */
class SaveProjectUseCase(private val repository: ProjectRepository, private val clock: Clock) {
    suspend operator fun invoke(project: Project): Outcome<Unit> =
        repository.save(project.copy(updatedAtEpochUs = clock.nowEpochUs()))
}
