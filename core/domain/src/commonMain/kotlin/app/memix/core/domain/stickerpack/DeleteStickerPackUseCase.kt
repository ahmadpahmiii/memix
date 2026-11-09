package app.memix.core.domain.stickerpack

/**
 * Deleting a pack in three steps, so the Undo window works (spec docs/ux/specs/P2-09-whatsapp-sticker-pack.md -> Packs screen): [hide]
 * at once, then [undo] or [finish] when the window ends. WhatsApp keeps its own copy of a pack it already has, so a delete here never
 * removes it from WhatsApp.
 */
class DeleteStickerPackUseCase(private val packs: StickerPackRepository) {
    suspend fun hide(packId: String) = packs.setRemoved(packId, removed = true)

    suspend fun undo(packId: String) = packs.setRemoved(packId, removed = false)

    suspend fun finish(packId: String) = packs.erase(packId)

    /** Clears what an earlier run left behind (a delete whose window never ended, a save cut short). Call before anything is written. */
    suspend fun cleanUp() = packs.eraseLeftovers()
}
