package app.memix.core.domain.stickerpack

import app.memix.core.domain.media.CopyResult
import app.memix.core.model.stickerpack.StickerPack

/**
 * The packs on this phone (spec docs/ux/specs/P2-09-whatsapp-sticker-pack.md). Implemented in `:core:data` over [StickerPackStore]'s
 * folders. Every function runs its disk work off the main thread and reports failures in its result; none throws for a missing file.
 */
interface StickerPackRepository {
    /** The packs to show, newest first. A pack waiting out its delete is not in the list. */
    suspend fun list(): List<StickerPack>

    /**
     * Writes [files] (file name to bytes) into the pack's folder, then `pack.json` last, so a reader never sees a pack whose files are
     * missing; then deletes the files of older versions. When anything can't be written the pack on disk is unchanged.
     */
    suspend fun save(pack: StickerPack, files: Map<String, ByteArray>): CopyResult

    /** Hides pack [id] from the list and from WhatsApp ([removed] true) or shows it again (false): the Undo window of a delete. */
    suspend fun setRemoved(id: String, removed: Boolean)

    /** Records that the pack was first seen in WhatsApp at [atEpochUs]; a pack already marked is left as it is. */
    suspend fun markAdded(id: String, atEpochUs: Long)

    /** Deletes the pack's folder. */
    suspend fun erase(id: String)

    /** Deletes packs waiting out a delete and folders with no readable `pack.json` (a cancelled or interrupted save). */
    suspend fun eraseLeftovers()
}
