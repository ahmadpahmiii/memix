package app.memix.core.domain.drafts

import app.memix.core.domain.AppError
import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.domain.media.LeftoverMediaCleaner
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaPaths
import app.memix.core.domain.media.mediaCopyPaths
import app.memix.core.domain.media.withMediaCopyPaths
import app.memix.core.domain.project.ProjectRepository
import app.memix.core.domain.project.nowEpochUs
import app.memix.core.model.project.Project
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Makes a copy of a draft with its own media folder (spec P1-14 → Duplicate, PM must-knows 4 to 6):
 * - a new id, created now, edited at the original's time plus 1 µs, so it sits directly above its original until it's
 *   edited; named by [copyName]; no `project_create` (a copy isn't a new meme).
 * - every file in the original's own folder (its media copies and its thumbnail) is linked into `media/<copy id>/`
 *   where the platform allows, else copied, and every path in the copy points into the new folder, so deleting either
 *   draft leaves the other whole. Shared files outside the folder (bundled sounds) stay shared.
 * - a file that's missing is skipped and its clip keeps pointing into the new folder, so the copy opens in the same
 *   missing-media state as the original.
 * - the row is saved only once every file is in place; anything that stops it first deletes the new folder (a kill
 *   leaves it to the next start's [LeftoverMediaCleaner]).
 *
 * Copying, not linking, needs space: the files that must be copied plus [ImportMediaUseCase.HEADROOM_BYTES].
 * [onProgress] gets 0 to 1 across the bytes copied, on the copying thread; links report nothing.
 */
class DuplicateDraftUseCase(
    private val repository: ProjectRepository,
    private val mediaFiles: MediaFiles,
    private val leftoverCleaner: LeftoverMediaCleaner,
    private val clock: Clock,
    private val logger: Logger,
) {
    suspend operator fun invoke(
        draftId: String,
        withCopySuffix: suspend (name: String, number: Int) -> String,
        onProgress: (Float) -> Unit,
    ): DuplicateOutcome {
        // The launch cleanup deletes folders without a saved draft, which the copy's folder is until it's saved.
        leftoverCleaner.awaitDone()
        val original = when (val loaded = repository.get(draftId)) {
            is Outcome.Failure -> return DuplicateOutcome.Failed(loaded.error)
            is Outcome.Success -> loaded.value
        }
        val takenNames = repository.observeSummaries().first().map { it.name }.toSet()
        val originalThumbnail = (repository.thumbnailPath(draftId) as? Outcome.Success)?.value
        val copyId = Uuid.random().toString()
        try {
            val files = filesToBring(original, originalThumbnail, copyId)
            val unlinked = files.filterNot { linkOrLog(it) }
            val space = copyAll(unlinked, onProgress)
            if (space != null) {
                deleteCopyFolder(copyId)
                return space
            }
            val copy = original.withMediaCopyPaths { path -> if (MediaPaths.isInFolderOf(original.id, path)) MediaPaths.inFolderOf(copyId, path) else path }
                .copy(
                    id = copyId,
                    name = copyName(original.name, takenNames, withCopySuffix),
                    createdAtEpochUs = clock.nowEpochUs(),
                    updatedAtEpochUs = original.updatedAtEpochUs + 1,
                )
            val thumbnail = originalThumbnail?.let { MediaPaths.inFolderOf(copyId, it) }?.takeIf { mediaFiles.sizeOf(it) != null }
            return save(copy, thumbnail)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { deleteCopyFolder(copyId) }
            throw e
        }
    }

    private suspend fun save(copy: Project, thumbnail: String?): DuplicateOutcome =
        when (val saved = repository.saveWithThumbnail(copy, thumbnail)) {
            is Outcome.Success -> DuplicateOutcome.Duplicated(copy.id)
            is Outcome.Failure -> {
                deleteCopyFolder(copy.id)
                if (saved.error == AppError.StorageFull) {
                    DuplicateOutcome.NotEnoughSpace(ImportMediaUseCase.HEADROOM_BYTES, mediaFiles.allocatableBytes())
                } else {
                    DuplicateOutcome.Failed(saved.error)
                }
            }
        }

    // Only the files the original owns: its folder's copies and thumbnail. Catalog files outside it stay shared.
    private fun filesToBring(original: Project, thumbnail: String?, copyId: String): List<FileToBring> =
        (original.mediaCopyPaths() + listOfNotNull(thumbnail))
            .filter { MediaPaths.isInFolderOf(original.id, it) }
            .distinct()
            .map { FileToBring(source = it, destination = MediaPaths.inFolderOf(copyId, it)) }

    // Logs which way each file went, for the device check of hard links (PM must-know 6). File names are random ids.
    private suspend fun linkOrLog(file: FileToBring): Boolean {
        val linked = mediaFiles.link(file.source, file.destination)
        logger.debug(TAG, "Duplicate: ${file.source.substringAfterLast('/')} ${if (linked) "linked" else "needs a copy"}")
        return linked
    }

    /** Copies [files]; null when done, or the space it still needs. A missing file is skipped. */
    private suspend fun copyAll(files: List<FileToBring>, onProgress: (Float) -> Unit): DuplicateOutcome.NotEnoughSpace? {
        val sized = files.mapNotNull { file -> mediaFiles.sizeOf(file.source)?.let { size -> file to size } }
        if (sized.isEmpty()) return null
        val totalBytes = sized.sumOf { it.second }
        val neededBytes = totalBytes + ImportMediaUseCase.HEADROOM_BYTES
        if (mediaFiles.allocatableBytes() < neededBytes || !mediaFiles.reserveBytes(neededBytes)) {
            return DuplicateOutcome.NotEnoughSpace(neededBytes, mediaFiles.allocatableBytes())
        }
        var doneBytes = 0L
        for ((file, sizeBytes) in sized) {
            val copied = mediaFiles.copyFile(file.source, file.destination) { bytes ->
                onProgress(((doneBytes + bytes).toFloat() / totalBytes).coerceIn(0f, 1f))
            }
            when (copied) {
                CopyResult.COPIED -> logger.debug(TAG, "Duplicate: ${file.source.substringAfterLast('/')} copied")
                // Gone since it was measured: skipped like a missing file.
                CopyResult.UNREADABLE -> logger.debug(TAG, "Duplicate: ${file.source.substringAfterLast('/')} unreadable, skipped")
                CopyResult.STORAGE_FULL ->
                    return DuplicateOutcome.NotEnoughSpace(totalBytes - doneBytes + ImportMediaUseCase.HEADROOM_BYTES, mediaFiles.allocatableBytes())
            }
            doneBytes += sizeBytes
        }
        return null
    }

    private suspend fun deleteCopyFolder(copyId: String) = mediaFiles.deleteFolder(MediaPaths.projectFolder(copyId))

    private class FileToBring(val source: String, val destination: String)

    private companion object {
        const val TAG = "Drafts"
    }
}

/** How [DuplicateDraftUseCase] ended, when it wasn't cancelled. */
sealed interface DuplicateOutcome {
    data class Duplicated(val copyId: String) : DuplicateOutcome

    /** Nothing was kept. [neededBytes] includes the headroom; [freeBytes] is what the app could use now. */
    data class NotEnoughSpace(val neededBytes: Long, val freeBytes: Long) : DuplicateOutcome

    /** The original can't be read (damaged, or from a newer app: ProjectUnreadable) or the save failed. */
    data class Failed(val error: AppError) : DuplicateOutcome
}
