package app.memix.core.domain.stickerpack

import app.memix.core.model.stickerpack.StickerPack

/** The packs on this phone, newest first. Main-safe. */
class ListStickerPacksUseCase(private val packs: StickerPackRepository) {
    suspend operator fun invoke(): List<StickerPack> = packs.list()
}

/**
 * What the Stickers sheet's "WhatsApp packs" row needs: whether packs can be made on this phone and how many exist. Null where they
 * can't be made, so the row is not shown.
 */
class GetStickerPacksRowUseCase(private val packs: StickerPackRepository, private val whatsApp: WhatsAppStickers) {
    suspend operator fun invoke(): PacksRow? = if (whatsApp.isSupported) PacksRow(packs.list().size) else null
}

data class PacksRow(val packCount: Int)
