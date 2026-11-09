package app.memix.platform.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.memix.core.domain.stickerpack.WhatsAppCheck
import app.memix.core.domain.stickerpack.WhatsAppStickers

// TODO(P7-09): WhatsApp's iOS flow is a pasteboard hand-off (WAStickers); until then packs are unsupported on iOS and the Stickers sheet shows no row.
@Composable
actual fun rememberWhatsAppPackLauncher(onReturned: () -> Unit): WhatsAppPackLauncher =
    remember {
        object : WhatsAppPackLauncher {
            override fun launch(packId: String, packName: String): Boolean = false
        }
    }

internal class NoWhatsAppStickers : WhatsAppStickers {
    override val isSupported: Boolean = false

    override suspend fun check(packId: String): WhatsAppCheck = WhatsAppCheck.NOT_INSTALLED
}
