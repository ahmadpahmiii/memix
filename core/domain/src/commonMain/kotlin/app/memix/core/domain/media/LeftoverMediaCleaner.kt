package app.memix.core.domain.media

import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.ProjectRepository
import app.memix.core.domain.project.nowEpochUs
import app.memix.core.model.project.ProjectSummary
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Deletes media copies no saved draft uses (TECHNICAL_DESIGN → Media import → When copies are deleted):
 * - unfinished copies;
 * - the folders of projects that were never saved (Android stopped the app mid-import, or before the first save) or
 *   whose draft was deleted;
 * - inside a saved draft's folder, the copies the draft no longer uses: a clip that was replaced or deleted. Undo
 *   lives in memory only, so after a restart nothing can bring them back. A draft saved since this start is left for
 *   the next one, because an editor of this run may have it open, with an undo that still needs them.
 *
 * Starts when it's created, in [scope], once per app start (a Koin single created at start-up). Imports wait for
 * it ([awaitDone]) before copying, so it never touches a copy an import of this run is making.
 */
class LeftoverMediaCleaner(
    private val mediaFiles: MediaFiles,
    private val projectRepository: ProjectRepository,
    private val logger: Logger,
    clock: Clock,
    scope: CoroutineScope,
) {
    // Drafts saved from now on belong to this run.
    private val startedAtEpochUs = clock.nowEpochUs()
    private val cleanup: Job = scope.launch { deleteLeftovers() }

    /** Returns once the cleanup has finished, whether or not it worked. */
    suspend fun awaitDone() = cleanup.join()

    private suspend fun deleteLeftovers() {
        try {
            mediaFiles.deleteUnfinishedCopies(MediaPaths.ROOT)
            val projectFolders = mediaFiles.listFolders(MediaPaths.ROOT)
            // Most starts find nothing to check, so the database isn't opened for it.
            if (projectFolders.isEmpty()) return
            val savedDrafts = projectRepository.observeSummaries().first().associateBy { it.id }
            val unsaved = projectFolders.filterNot { it in savedDrafts }
            unsaved.forEach { projectId -> mediaFiles.deleteFolder(MediaPaths.projectFolder(projectId)) }
            if (unsaved.isNotEmpty()) logger.debug(TAG, "Deleted the media of ${unsaved.size} unsaved or deleted projects")
            val unusedCount = projectFolders.mapNotNull(savedDrafts::get).sumOf { deleteUnusedCopies(it) }
            if (unusedCount > 0) logger.debug(TAG, "Deleted $unusedCount media copies no draft uses")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Leftovers only cost space, and the next start tries again; an import mustn't fail because of them.
            logger.error(TAG, "Leftover media cleanup failed", e)
        }
    }

    /** Deletes the files in [draft]'s folder that the saved draft doesn't use; returns how many. */
    private suspend fun deleteUnusedCopies(draft: ProjectSummary): Int {
        // A draft that can't be read keeps all its media: a later version may read it.
        val project = (projectRepository.get(draft.id) as? Outcome.Success)?.value ?: return 0
        // Read after loading, so a save by an editor that opened it at start is seen too. Copies an editor adds later
        // can't be touched: imports wait for this cleanup before they copy anything.
        if (project.updatedAtEpochUs >= startedAtEpochUs) return 0
        val inUse = project.mediaCopyPaths() + listOfNotNull(draft.thumbnailPath)
        val unused = mediaFiles.listFiles(MediaPaths.projectFolder(draft.id)).filterNot { it in inUse }
        if (unused.isNotEmpty()) mediaFiles.delete(unused)
        return unused.size
    }

    private companion object {
        const val TAG = "MediaImport"
    }
}
