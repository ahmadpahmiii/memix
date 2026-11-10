package app.memix.core.data.stickerpack

import app.memix.core.domain.Logger
import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.stickerpack.StickerPackRepository
import app.memix.core.domain.stickerpack.StickerPackStore
import app.memix.core.model.stickerpack.StickerPack
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Packs as files, one folder each (see [StickerPackStore]): the Android ContentProvider reads the same `pack.json` files without the
 * app's dependency graph, because WhatsApp can start it before the app's own start-up has run. Writes go through [MediaFiles], which
 * renames a finished temporary file into place, so a reader never sees half a file.
 */
internal class FileStickerPackRepository(private val files: MediaFiles, private val logger: Logger) : StickerPackRepository {
    private val json = Json { ignoreUnknownKeys = true }

    // Every change to a pack reads `pack.json` and writes it back; one at a time, so a late "added" mark can't put an older pack file over a fresh save.
    private val writes = Mutex()

    override suspend fun list(): List<StickerPack> =
        files.listFolders(StickerPackStore.ROOT)
            .mapNotNull { id -> read(id) }
            .filterNot { it.removed }
            .sortedWith(compareByDescending<StickerPack> { it.createdAtEpochUs }.thenBy { it.id })

    override suspend fun save(pack: StickerPack, files: Map<String, ByteArray>): CopyResult = writes.withLock {
        val written = ArrayList<String>()
        for ((name, bytes) in files) {
            val path = StickerPackStore.pathOf(pack.id, name)
            val result = this.files.write(bytes, path)
            if (result != CopyResult.COPIED) {
                // The old pack file still points at the old version's files, so only this run's new files go.
                this.files.delete(written + path)
                return@withLock result
            }
            written += path
        }
        val result = write(pack)
        if (result != CopyResult.COPIED) {
            this.files.delete(written)
            return@withLock result
        }
        deleteOtherVersions(pack)
        CopyResult.COPIED
    }

    override suspend fun setRemoved(id: String, removed: Boolean) = writes.withLock {
        val pack = read(id) ?: return@withLock
        write(pack.copy(removed = removed))
        Unit
    }

    override suspend fun markAdded(id: String, atEpochUs: Long) = writes.withLock {
        val pack = read(id) ?: return@withLock
        // Only the first time: later checks (every time the packs open) would rewrite the pack file for nothing.
        if (pack.addedToWhatsAppAtEpochUs != null) return@withLock
        write(pack.copy(addedToWhatsAppAtEpochUs = atEpochUs))
        Unit
    }

    override suspend fun erase(id: String) = writes.withLock { files.deleteFolder(StickerPackStore.folderOf(id)) }

    override suspend fun eraseLeftovers() = writes.withLock {
        for (id in files.listFolders(StickerPackStore.ROOT)) {
            val pack = read(id)
            if (pack == null || pack.removed) files.deleteFolder(StickerPackStore.folderOf(id))
        }
    }

    private suspend fun write(pack: StickerPack): CopyResult =
        files.write(json.encodeToString(StickerPack.serializer(), pack).encodeToByteArray(), StickerPackStore.pathOf(pack.id, StickerPackStore.PACK_FILE))

    private suspend fun read(id: String): StickerPack? {
        val bytes = files.read(StickerPackStore.pathOf(id, StickerPackStore.PACK_FILE)) ?: return null
        return try {
            json.decodeFromString(StickerPack.serializer(), bytes.decodeToString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            // Only the class: the message quotes the pack's JSON, which holds the user's pack name.
            logger.error(TAG, "A pack file can't be read (${e::class.simpleName})")
            null
        } catch (e: IllegalArgumentException) {
            logger.error(TAG, "A pack file can't be read (${e::class.simpleName})")
            null
        }
    }

    // Files of older versions go once the new pack file is in place; WhatsApp reads whatever the pack file lists.
    private suspend fun deleteOtherVersions(pack: StickerPack) {
        val keep = pack.stickers.map { StickerPackStore.pathOf(pack.id, it.file) } +
            StickerPackStore.pathOf(pack.id, pack.trayFile) + StickerPackStore.pathOf(pack.id, StickerPackStore.PACK_FILE)
        val stale = files.listFiles(StickerPackStore.folderOf(pack.id)).filterNot { it in keep }
        if (stale.isNotEmpty()) files.delete(stale)
    }

    private companion object {
        const val TAG = "MemixStickerPacks"
    }
}
