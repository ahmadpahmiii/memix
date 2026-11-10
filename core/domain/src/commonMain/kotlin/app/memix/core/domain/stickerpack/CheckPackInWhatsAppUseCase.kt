package app.memix.core.domain.stickerpack

import kotlin.time.Clock

/** Asks WhatsApp whether the pack is added, and remembers a yes, so the pack reads "Update in WhatsApp" from then on. Main-safe. */
class CheckPackInWhatsAppUseCase(
    private val whatsApp: WhatsAppStickers,
    private val packs: StickerPackRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(packId: String): WhatsAppCheck {
        val check = whatsApp.check(packId)
        if (check == WhatsAppCheck.ADDED) packs.markAdded(packId, clock.now().toEpochMilliseconds() * MICROS_PER_MILLI)
        return check
    }

    private companion object {
        const val MICROS_PER_MILLI = 1_000L
    }
}
