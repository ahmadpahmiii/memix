package app.memix.core.domain.project

import app.memix.core.domain.Outcome
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectSummary
import kotlinx.coroutines.flow.Flow

/**
 * Drafts saved on this phone. Implemented in :core:data.
 *
 * Every function is safe to call from the main thread: the work runs on an I/O dispatcher.
 */
interface ProjectRepository {
    /** Inserts the project or replaces the saved copy with the same id. Fails with StorageFull when the disk is full. */
    suspend fun save(project: Project): Outcome<Unit>

    /**
     * Loads a project, migrated to [Project.CURRENT_SCHEMA_VERSION].
     * Fails with NotFound for an unknown id and ProjectUnreadable when the saved copy can't be read.
     */
    suspend fun get(id: String): Outcome<Project>

    /** Deleting an id that isn't saved succeeds. */
    suspend fun delete(id: String): Outcome<Unit>

    /**
     * Every saved project, last edited first. Emits again after each save or delete.
     * The flow fails only if the database itself can't be read.
     */
    fun observeSummaries(): Flow<List<ProjectSummary>>
}
