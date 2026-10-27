package app.memix.core.domain.project

import app.memix.core.domain.Outcome
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectSummary
import kotlinx.coroutines.flow.Flow

/**
 * Drafts saved on this phone. Implemented in :core:data.
 *
 * Every function is safe to call from the main thread: the work runs on an I/O dispatcher.
 * No function changes a project's times: what is passed in is what is stored, so only [SaveProjectUseCase] marks a
 * draft as edited.
 */
interface ProjectRepository {
    /**
     * Inserts the project or replaces the saved copy with the same id, keeping its thumbnail. Fails with StorageFull
     * when the disk is full.
     */
    suspend fun save(project: Project): Outcome<Unit>

    /** Like [save], with [thumbnailPath] as the draft's thumbnail: a duplicate, which brings its own. */
    suspend fun saveWithThumbnail(project: Project, thumbnailPath: String?): Outcome<Unit>

    /**
     * Loads a project, migrated to [Project.CURRENT_SCHEMA_VERSION].
     * Fails with NotFound for an unknown id and ProjectUnreadable when the saved copy can't be read.
     */
    suspend fun get(id: String): Outcome<Project>

    /** Deleting an id that isn't saved succeeds. */
    suspend fun delete(id: String): Outcome<Unit>

    /**
     * Deletes the draft and returns its saved row as it was, without reading the project, so a draft from a newer
     * app can go too. [putBack] restores it exactly. Fails with NotFound for an unknown id.
     */
    suspend fun remove(id: String): Outcome<RemovedDraft>

    /** Saves a [remove]d draft back as it was: same id, times, name, thumbnail and project. */
    suspend fun putBack(draft: RemovedDraft): Outcome<Unit>

    /** The draft's thumbnail path, null when it has none. Fails with NotFound for an unknown id. */
    suspend fun thumbnailPath(id: String): Outcome<String?>

    /** Sets the draft's thumbnail without touching anything else; an unknown id changes nothing. */
    suspend fun setThumbnailPath(id: String, thumbnailPath: String): Outcome<Unit>

    /**
     * Every saved project, last edited first, and the newer one first when two were edited at the same time.
     * Emits again after each change. The flow fails only if the database itself can't be read.
     */
    fun observeSummaries(): Flow<List<ProjectSummary>>
}

/**
 * A deleted draft's saved row, held in memory while its delete can still be undone. [storedProject] is the project
 * in the repository's own stored form, opaque to everyone else.
 */
class RemovedDraft(val summary: ProjectSummary, val storedProject: String)
