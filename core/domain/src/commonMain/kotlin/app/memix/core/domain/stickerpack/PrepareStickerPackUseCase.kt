package app.memix.core.domain.stickerpack

import app.memix.core.domain.media.CopyResult
import app.memix.core.model.stickerpack.PackSticker
import app.memix.core.model.stickerpack.StickerPack
import app.memix.core.model.stickerpack.StickerPackLimits
import kotlin.time.Clock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Makes the files WhatsApp needs from the stickers the user picked and saves the pack (spec docs/ux/specs/P2-09-whatsapp-sticker-pack.md
 * -> Rules). Every sticker is encoded first and the pack is written only when all of them fit, so a pack on disk is always complete
 * and a cancelled run changes nothing. Editing an existing pack ([PackRequest.existing]) keeps its id and raises its version.
 * Cancellable; main-safe. A request outside 3 to 30 stickers is [PackPreparation.Failed].
 */
class PrepareStickerPackUseCase(
    private val packs: StickerPackRepository,
    private val encoder: StickerPackEncoder,
    private val clock: Clock,
) {
    /** [onProgress] gets (stickers done, total) after each sticker, on the working thread. */
    suspend operator fun invoke(request: PackRequest, onProgress: (done: Int, total: Int) -> Unit): PackPreparation {
        val picked = request.stickers
        if (picked.size !in StickerPackLimits.MIN_STICKERS..StickerPackLimits.MAX_STICKERS) return PackPreparation.Failed
        val encoded = ArrayList<ByteArray>(picked.size)
        val tooDetailed = ArrayList<String>()
        val missing = ArrayList<String>()
        picked.forEachIndexed { index, sticker ->
            currentCoroutineContext().ensureActive()
            when (val result = encoder.encodeSticker(sticker.encodeFrom)) {
                is StickerEncoding.Encoded -> encoded += result.webp
                StickerEncoding.TooDetailed -> tooDetailed += sticker.sourcePath
                StickerEncoding.Unreadable -> missing += sticker.sourcePath
            }
            onProgress(index + 1, picked.size)
        }
        if (missing.isNotEmpty()) return PackPreparation.Missing(missing)
        if (tooDetailed.isNotEmpty()) return PackPreparation.TooDetailed(tooDetailed)
        val tray = encoder.encodeTray(picked.first().encodeFrom) ?: return PackPreparation.Missing(listOf(picked.first().sourcePath))
        return save(request, encoded, tray)
    }

    private suspend fun save(request: PackRequest, encoded: List<ByteArray>, tray: ByteArray): PackPreparation {
        val existing = request.existing
        val id = existing?.id ?: StickerPackStore.newId()
        val version = (existing?.imageDataVersion ?: 0L) + 1
        val files = LinkedHashMap<String, ByteArray>()
        val stickers = request.stickers.mapIndexed { index, sticker ->
            val file = StickerPackStore.stickerFileName(index, version)
            files[file] = encoded[index]
            PackSticker(file, sticker.sourcePath, sticker.emojis.clean())
        }
        val trayFile = StickerPackStore.trayFileName(version)
        files[trayFile] = tray
        val pack = StickerPack(
            id = id,
            name = request.name.trim().take(StickerPackLimits.NAME_MAX_CHARS),
            imageDataVersion = version,
            trayFile = trayFile,
            stickers = stickers,
            createdAtEpochUs = existing?.createdAtEpochUs ?: (clock.now().toEpochMilliseconds() * MICROS_PER_MILLI),
            addedToWhatsAppAtEpochUs = existing?.addedToWhatsAppAtEpochUs,
        )
        return when (packs.save(pack, files)) {
            CopyResult.COPIED -> PackPreparation.Ready(pack)
            CopyResult.STORAGE_FULL -> PackPreparation.NoSpace
            CopyResult.UNREADABLE -> PackPreparation.Failed
        }
    }

    // 1 to 3 emojis, the default when none were chosen.
    private fun List<String>.clean(): List<String> =
        distinct().take(StickerPackLimits.MAX_EMOJIS).ifEmpty { listOf(StickerPackLimits.DEFAULT_EMOJI) }

    private companion object {
        const val MICROS_PER_MILLI = 1_000L
    }
}

/** What to prepare: the pack being edited (null for a new one), the name, and the stickers in pack order with their emojis. */
class PackRequest(val existing: StickerPack?, val name: String, val stickers: List<PackStickerRequest>)

/**
 * A My stickers file ([sourcePath], the sticker's identity in the pack) and the emojis it is tagged with. [encodeFrom] is the file the
 * encoder reads: the same path, or the pack's own prepared copy when the sticker was deleted from My stickers meanwhile.
 */
class PackStickerRequest(val sourcePath: String, val emojis: List<String>, val encodeFrom: String = sourcePath)

sealed interface PackPreparation {
    /** The pack is saved and WhatsApp can read it. */
    data class Ready(val pack: StickerPack) : PackPreparation

    /** These My stickers files stay over 100 KB even at the lowest quality; nothing was saved. */
    data class TooDetailed(val sourcePaths: List<String>) : PackPreparation

    /** These files are gone or can't be read; nothing was saved. */
    data class Missing(val sourcePaths: List<String>) : PackPreparation

    data object NoSpace : PackPreparation

    data object Failed : PackPreparation
}
