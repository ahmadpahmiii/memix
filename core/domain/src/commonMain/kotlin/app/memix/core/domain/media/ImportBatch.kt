package app.memix.core.domain.media

import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef

/** The most items one pick adds (TikTok's photo-mode maximum; spec P1-02). There's no limit per project. */
const val MAX_ITEMS_PER_PICK = 35

/**
 * One pick on its way into a project: the items in the order the picker returned them, at most
 * [MAX_ITEMS_PER_PICK], and how each one went. Immutable; [ImportMediaUseCase] returns updated copies.
 */
data class ImportBatch(
    /** The project whose media folder the copies go into. */
    val projectId: String,
    val items: List<ImportItem>,
    /** How many the user picked: more than [items] when the pick was over the limit and the rest were left out. */
    val pickedCount: Int,
) {
    val isOverLimit: Boolean get() = pickedCount > items.size

    /** The media that went in, in pick order. */
    val added: List<MediaRef> get() = items.mapNotNull { (it.outcome as? ItemOutcome.Added)?.media }

    internal fun withOutcome(index: Int, outcome: ItemOutcome): ImportBatch =
        copy(items = items.mapIndexed { i, item -> if (i == index) item.copy(outcome = outcome) else item })
}

data class ImportItem(
    val origin: MediaOrigin,
    /** What the platform could tell before copying; null when the item couldn't be opened at all. */
    val file: PickedFile?,
    /** Null until the item has been copied and checked. */
    val outcome: ItemOutcome? = null,
) {
    val kind: MediaKind? get() = file?.kind
}

sealed interface ItemOutcome {
    /** Copied and checked; [media] points at the copy and carries the measured duration and size. */
    data class Added(val media: MediaRef) : ItemOutcome

    data class NotAdded(val reason: ImportFailure) : ItemOutcome
}

/** Why an item didn't go in. The import sheet shows one line of copy per reason. */
enum class ImportFailure {
    /** Couldn't be opened or read: deleted, access lost, or a cloud-only item that didn't download. */
    UNREADABLE,

    /** Read, but it isn't a video or photo, has no playable video track, or the picture doesn't decode. */
    UNSUPPORTED,
}

/** Where a running copy is, for the import sheet. Sent many times a second from the copying thread. */
data class ImportProgress(
    /** The item being copied now, from 1; never more than [itemCount]. */
    val itemNumber: Int,
    val itemCount: Int,
    /** 0 to 1: by bytes when every size is known, otherwise each item counts as an equal share. */
    val fraction: Float,
    /** Bytes done by the sizes the sources reported, items already handled included; with [totalBytes], the time left. */
    val completedBytes: Long,
    /** Null when any item's size is unknown. */
    val totalBytes: Long?,
    val currentItemCopiedBytes: Long,
    val currentItemSizeKnown: Boolean,
)

/**
 * Progress while the item at [index] has [copiedInItem] bytes copied. Items that already have an outcome count as
 * done in full, whether or not they went in, so the bar never moves back.
 */
internal fun ImportBatch.progressAt(index: Int, copiedInItem: Long): ImportProgress {
    val sizes = items.map { it.file?.sizeBytes }
    val currentSize = sizes[index]
    val currentDone = if (currentSize == null) copiedInItem else copiedInItem.coerceAtMost(currentSize)
    val doneBefore = items.indices.filter { items[it].outcome != null && it != index }.sumOf { sizes[it] ?: 0L }
    val completedBytes = doneBefore + currentDone
    val totalBytes = if (sizes.all { it != null }) sizes.sumOf { it ?: 0L } else null
    val fraction = if (totalBytes != null && totalBytes > 0) {
        completedBytes.toFloat() / totalBytes
    } else {
        val itemsDone = items.count { it.outcome != null }
        val currentShare = if (currentSize != null && currentSize > 0) currentDone.toFloat() / currentSize else 0f
        (itemsDone + currentShare) / items.size
    }
    return ImportProgress(
        itemNumber = index + 1,
        itemCount = items.size,
        fraction = fraction.coerceIn(0f, 1f),
        completedBytes = completedBytes,
        totalBytes = totalBytes,
        currentItemCopiedBytes = copiedInItem,
        currentItemSizeKnown = currentSize != null,
    )
}
