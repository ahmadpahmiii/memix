package app.memix.core.domain.project

import app.memix.core.domain.Outcome

/** Deletes a saved draft. */
class DeleteProjectUseCase(private val repository: ProjectRepository) {
    // TODO(P1-14): also delete the draft's cached media copies (P1-02) and thumbnail.
    suspend operator fun invoke(id: String): Outcome<Unit> = repository.delete(id)
}
