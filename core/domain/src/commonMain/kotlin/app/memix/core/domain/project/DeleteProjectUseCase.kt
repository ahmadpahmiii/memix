package app.memix.core.domain.project

import app.memix.core.domain.Outcome

/** Deletes a saved draft. */
class DeleteProjectUseCase(private val repository: ProjectRepository) {
    // TODO(P1-14): also delete the draft's thumbnail and its media folder (MediaFiles.deleteFolder(MediaPaths.projectFolder(id)))
    // once undo of a delete is settled. Until then the launch cleanup (LeftoverMediaCleaner) removes the folder at the next start.
    suspend operator fun invoke(id: String): Outcome<Unit> = repository.delete(id)
}
