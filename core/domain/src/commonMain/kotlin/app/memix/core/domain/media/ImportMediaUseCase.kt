package app.memix.core.domain.media

import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Brings picked videos and photos into a project's media folder: the app's own copy of each, checked and measured.
 * Used by the first import (Create → Video meme) and, from P1-16, by "Add media" in the editor. It doesn't touch
 * the project itself; the caller places [ImportBatch.added] on the timeline.
 *
 * Steps, in order: [prepare] the batch, [checkSpace], then [copyRemaining]. After running out of space, check
 * again and copy the rest of the same batch; or [discard] it.
 */
class ImportMediaUseCase(
    private val mediaFiles: MediaFiles,
    private val inspector: MediaInspector,
    private val leftoverCleaner: LeftoverMediaCleaner,
) {
    /** Looks up each picked item, in the order given, keeping the first [MAX_ITEMS_PER_PICK]. Copies nothing. */
    suspend fun prepare(picked: List<MediaOrigin>, projectId: String): ImportBatch {
        val items = picked.take(MAX_ITEMS_PER_PICK).map { origin -> ImportItem(origin, mediaFiles.describe(origin)) }
        return ImportBatch(projectId, items, pickedCount = picked.size)
    }

    /**
     * Checks that the items not copied yet fit, with [HEADROOM_BYTES] to spare, and asks the system to free other
     * apps' cache files for them if needed. Unknown sizes count as zero, so a copy can still run out of space.
     */
    suspend fun checkSpace(batch: ImportBatch): SpaceCheck {
        val neededBytes = bytesStillNeeded(batch)
        val freeBytes = mediaFiles.allocatableBytes()
        return when {
            freeBytes < neededBytes -> SpaceCheck.NotEnough(neededBytes, freeBytes)
            mediaFiles.reserveBytes(neededBytes) -> SpaceCheck.Enough
            else -> SpaceCheck.NotEnough(neededBytes, mediaFiles.allocatableBytes())
        }
    }

    /**
     * After a new project's first save failed because the phone is full: the space Memix waits for before saving
     * again ([HEADROOM_BYTES]; the copies are already on disk), and what's free now. Check with [checkSpace] on the
     * same batch when the user is back.
     */
    suspend fun spaceToSaveAgain(): SpaceCheck.NotEnough = SpaceCheck.NotEnough(HEADROOM_BYTES, mediaFiles.allocatableBytes())

    /**
     * Copies and checks every item that has no outcome yet, one at a time in pick order, reporting progress on the
     * copying thread. Stops early, keeping what's done, when the phone runs out of space. Cancelling deletes every
     * copy of the batch, earlier runs' included, so a cancelled pick leaves nothing behind.
     */
    suspend fun copyRemaining(batch: ImportBatch, onProgress: (ImportProgress) -> Unit): CopyRun {
        // The launch cleanup deletes folders of unsaved projects, which this batch's folder is until it lands.
        leftoverCleaner.awaitDone()
        var current = batch
        try {
            for (index in batch.items.indices) {
                if (current.items[index].outcome != null) continue
                val outcome = importItem(current, index, onProgress)
                    ?: return CopyRun.OutOfSpace(current, spaceLeftFor(current))
                current = current.withOutcome(index, outcome)
            }
            return CopyRun.Finished(current)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { discard(current) }
            throw e
        }
    }

    /** Deletes the batch's copies: the user cancelled or closed, or nothing of it will be used. */
    suspend fun discard(batch: ImportBatch) = mediaFiles.delete(batch.added.mapNotNull { it.cachedCopyPath })

    // Null means the phone ran out of space; the unfinished copy is already gone.
    private suspend fun importItem(batch: ImportBatch, index: Int, onProgress: (ImportProgress) -> Unit): ItemOutcome? {
        val item = batch.items[index]
        val file = item.file ?: return ItemOutcome.NotAdded(ImportFailure.UNREADABLE)
        val kind = file.kind ?: return ItemOutcome.NotAdded(ImportFailure.UNSUPPORTED)
        val path = MediaPaths.newCopy(batch.projectId, kind, file.extension)
        try {
            onProgress(batch.progressAt(index, copiedInItem = 0))
            val copyResult = mediaFiles.copy(item.origin, path) { copied -> onProgress(batch.progressAt(index, copied)) }
            return when (copyResult) {
                CopyResult.COPIED -> checkCopy(item.origin, kind, path)
                CopyResult.UNREADABLE -> ItemOutcome.NotAdded(ImportFailure.UNREADABLE)
                CopyResult.STORAGE_FULL -> null
            }
        } catch (e: CancellationException) {
            // A copy can be complete under its real name when Cancel lands, and it isn't in the batch yet, so the
            // batch's own cleanup wouldn't find it. Deleting a path that was never written does nothing.
            withContext(NonCancellable) { mediaFiles.delete(listOf(path)) }
            throw e
        }
    }

    private suspend fun checkCopy(origin: MediaOrigin, kind: MediaKind, path: String): ItemOutcome {
        val facts = inspector.inspect(path, kind)
        if (facts == null) {
            mediaFiles.delete(listOf(path))
            return ItemOutcome.NotAdded(ImportFailure.UNSUPPORTED)
        }
        val media = MediaRef(
            origin = origin,
            kind = kind,
            cachedCopyPath = path,
            durationUs = facts.durationUs,
            pixelSize = facts.pixelSize,
            hasAudio = facts.hasAudio,
        )
        return ItemOutcome.Added(media)
    }

    private suspend fun spaceLeftFor(batch: ImportBatch) = SpaceCheck.NotEnough(bytesStillNeeded(batch), mediaFiles.allocatableBytes())

    // The known sizes of the items not copied yet, plus the headroom.
    private fun bytesStillNeeded(batch: ImportBatch): Long =
        batch.items.filter { it.outcome == null }.sumOf { it.file?.sizeBytes ?: 0L } + HEADROOM_BYTES

    companion object {
        /** Kept free on top of the media itself, so an import never fills the phone to the last byte (spec P1-02). */
        const val HEADROOM_BYTES = 100_000_000L
    }
}

/** Whether the rest of a batch fits on the phone. */
sealed interface SpaceCheck {
    data object Enough : SpaceCheck

    /** [neededBytes] includes the headroom; [freeBytes] is what the app could use now. */
    data class NotEnough(val neededBytes: Long, val freeBytes: Long) : SpaceCheck
}

/** How [ImportMediaUseCase.copyRemaining] ended. */
sealed interface CopyRun {
    val batch: ImportBatch

    /** Every item has an outcome. */
    data class Finished(override val batch: ImportBatch) : CopyRun

    /** Stopped at the first item that didn't fit; the items before it keep their outcomes for the retry. */
    data class OutOfSpace(override val batch: ImportBatch, val space: SpaceCheck.NotEnough) : CopyRun
}
