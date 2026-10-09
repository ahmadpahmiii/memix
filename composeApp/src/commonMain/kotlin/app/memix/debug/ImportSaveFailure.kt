package app.memix.debug

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.ProjectRepository
import app.memix.core.model.project.Project
import kotlin.concurrent.Volatile

/**
 * The P1-02 N2 hand check (debug and benchmark builds): `--ez memix.failImportSave true` makes the next import's
 * first save fail as if the phone were full. A real disk can't be filled between the end of the copy and the save
 * (they are milliseconds apart), so this is how the "Not enough space" sheet, the kept copies and the save on return
 * get checked (docs/qa/phase-1/engineer-hand-checks.md → P1-04). Only the import's save goes through it
 * (handCheckModule); the editor's auto-save never does.
 */
object ImportSaveFailure {
    @Volatile
    private var armed = false

    fun arm() {
        armed = true
    }

    /** [repository], except that its next save after [arm] fails with [AppError.StorageFull] and writes nothing. */
    internal fun around(repository: ProjectRepository): ProjectRepository = object : ProjectRepository by repository {
        override suspend fun save(project: Project): Outcome<Unit> {
            if (!armed) return repository.save(project)
            armed = false
            return Outcome.Failure(AppError.StorageFull)
        }
    }
}
