package app.memix.core.domain.drafts

import app.memix.core.domain.Outcome
import app.memix.core.domain.project.ProjectRepository

/**
 * Renames a draft without marking it as edited: a name isn't an edit to the meme, so the row keeps its place in the
 * list (PM, 20 Oct 2026). Writes the name into the project and its row through the repository, never through
 * `SaveProjectUseCase`, which would stamp the edit time. [typedName] is cleaned first ([cleanDraftName]); an empty
 * name makes the draft unnamed. An unchanged name writes nothing.
 *
 * Fails like [ProjectRepository.get] (a draft from a newer app is ProjectUnreadable) or with StorageFull.
 */
class RenameDraftUseCase(private val repository: ProjectRepository) {
    suspend operator fun invoke(draftId: String, typedName: String): Outcome<Unit> {
        val name = cleanDraftName(typedName)
        val project = when (val loaded = repository.get(draftId)) {
            is Outcome.Failure -> return loaded
            is Outcome.Success -> loaded.value
        }
        if (project.name == name) return Outcome.Success(Unit)
        return repository.save(project.copy(name = name))
    }
}
