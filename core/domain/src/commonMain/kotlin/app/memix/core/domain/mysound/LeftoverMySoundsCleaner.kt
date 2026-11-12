package app.memix.core.domain.mysound

import app.memix.core.domain.Logger
import app.memix.core.domain.media.MediaFiles
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Cleans My sounds at start (P3-07): deletes what the folder holds that no row points at (a conversion cut short, `.part`, and
 * the file of a sound deleted just before the app stopped, whose Undo window never ended), and drops rows whose file is gone
 * (a restored backup keeps the rows, not the files). Starts when it's created, in [scope], once
 * per app start (a Koin single created at start-up); an import waits for it ([awaitDone]) so it never meets its own file.
 */
class LeftoverMySoundsCleaner(
    private val mediaFiles: MediaFiles,
    private val repository: MySoundsRepository,
    private val logger: Logger,
    scope: CoroutineScope,
) {
    private val cleanup: Job = scope.launch { deleteLeftovers() }

    /** Returns once the cleanup has finished, whether or not it worked. */
    suspend fun awaitDone() = cleanup.join()

    private suspend fun deleteLeftovers() {
        try {
            mediaFiles.deleteUnfinishedCopies(MySoundPaths.FOLDER)
            val files = mediaFiles.listFiles(MySoundPaths.FOLDER).toSet()
            val used = repository.allPaths()
            val unused = files - used
            if (unused.isNotEmpty()) mediaFiles.delete(unused.toList())
            // A restored backup brings the rows but not the files (mysounds is excluded from backup): drop rows with no file.
            val ghosts = used - files
            if (ghosts.isNotEmpty()) repository.removeByPaths(ghosts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Leftovers only cost space, and the next start tries again.
            logger.error(TAG, "My sounds cleanup failed: ${e::class.simpleName}")
        }
    }

    private companion object {
        const val TAG = "MySounds"
    }
}
