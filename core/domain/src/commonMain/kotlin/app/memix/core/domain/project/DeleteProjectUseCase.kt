package app.memix.core.domain.project

import app.memix.core.domain.Outcome
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaPaths

/**
 * Deletes a saved draft for good, with its media folder and thumbnail: an editor left with no clips (P1-07). The
 * drafts list deletes with an undo window instead (`DeleteDraftUseCase`). If the folder can't be deleted now, the next
 * start's `LeftoverMediaCleaner` deletes it.
 */
class DeleteProjectUseCase(private val repository: ProjectRepository, private val mediaFiles: MediaFiles) {
    suspend operator fun invoke(id: String): Outcome<Unit> {
        val deleted = repository.delete(id)
        if (deleted is Outcome.Success) mediaFiles.deleteFolder(MediaPaths.projectFolder(id))
        return deleted
    }
}
