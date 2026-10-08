package app.memix.core.domain.media

import app.memix.core.domain.Logger
import app.memix.core.domain.project.ProjectRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Deletes media copies no saved project can use: unfinished copies, and the folders of projects that were never
 * saved (Android stopped the app mid-import, or before the first save) or whose draft was deleted.
 *
 * Starts when it's created, in [scope], once per app start (a Koin single created at start-up). Imports wait for
 * it ([awaitDone]) before copying, so it never touches a folder an import of this run is filling.
 */
class LeftoverMediaCleaner(
    private val mediaFiles: MediaFiles,
    private val projectRepository: ProjectRepository,
    private val logger: Logger,
    scope: CoroutineScope,
) {
    private val cleanup: Job = scope.launch { deleteLeftovers() }

    /** Returns once the cleanup has finished, whether or not it worked. */
    suspend fun awaitDone() = cleanup.join()

    private suspend fun deleteLeftovers() {
        try {
            mediaFiles.deleteUnfinishedCopies(MediaPaths.ROOT)
            val projectFolders = mediaFiles.listFolders(MediaPaths.ROOT)
            // Most starts find nothing to check, so the database isn't opened for it.
            if (projectFolders.isEmpty()) return
            val savedProjectIds = projectRepository.observeSummaries().first().map { it.id }.toSet()
            val unsaved = projectFolders.filterNot { it in savedProjectIds }
            unsaved.forEach { projectId -> mediaFiles.deleteFolder(MediaPaths.projectFolder(projectId)) }
            if (unsaved.isNotEmpty()) logger.debug(TAG, "Deleted the media of ${unsaved.size} unsaved or deleted projects")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Leftovers only cost space, and the next start tries again; an import mustn't fail because of them.
            logger.error(TAG, "Leftover media cleanup failed", e)
        }
    }

    private companion object {
        const val TAG = "MediaImport"
    }
}
