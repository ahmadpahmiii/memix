package app.memix.core.domain.stickerpack

/**
 * The phone's WhatsApp apps, seen from the sticker feature. Implemented in `:platform:services`. Adding a pack is a system screen
 * the app opens and gets control back from (a Compose hook in `:platform:services`); this is the part that needs no screen.
 */
interface WhatsAppStickers {
    /** False where packs can't be made (iOS until P7-09): the Stickers sheet then shows no WhatsApp row. */
    val isSupported: Boolean

    /**
     * Asks WhatsApp and WhatsApp Business, through their own check, whether pack [packId] is added. Main-safe. WhatsApp documents no
     * result code for the add screen, so this is how the app finds out.
     */
    suspend fun check(packId: String): WhatsAppCheck
}

enum class WhatsAppCheck {
    /** Neither WhatsApp nor WhatsApp Business is installed. */
    NOT_INSTALLED,

    /** A WhatsApp is installed but is too old to have stickers. */
    TOO_OLD,

    /** Installed and the pack isn't in it (yet): the user may have tapped Cancel. */
    NOT_ADDED,

    /** At least one installed WhatsApp has the pack. */
    ADDED,
}
