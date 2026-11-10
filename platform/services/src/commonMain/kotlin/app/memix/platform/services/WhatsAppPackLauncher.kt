package app.memix.platform.services

import androidx.compose.runtime.Composable

/** Opens WhatsApp's own screen that adds a sticker pack (spec docs/ux/specs/P2-09-whatsapp-sticker-pack.md -> Adding). */
interface WhatsAppPackLauncher {
    /**
     * Asks WhatsApp to add the pack [packId], named [packName] on its confirm screen. With both WhatsApp and WhatsApp Business installed
     * Android shows its own chooser. Returns false when no WhatsApp can take it; otherwise the screen opens and the `onReturned` given
     * to [rememberWhatsAppPackLauncher] runs when the user is back in the app, whatever they chose there.
     */
    fun launch(packId: String, packName: String): Boolean
}

/**
 * Registers WhatsApp's add screen. [onReturned] runs when the user comes back from it: WhatsApp documents no result code, so the
 * caller asks `WhatsAppStickers.check` to learn what happened.
 */
@Composable
expect fun rememberWhatsAppPackLauncher(onReturned: () -> Unit): WhatsAppPackLauncher
