package app.memix.platform.services

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberWhatsAppPackLauncher(onReturned: () -> Unit): WhatsAppPackLauncher {
    val authority = StickerPackAuthority.of(LocalContext.current)
    val currentOnReturned by rememberUpdatedState(onReturned)
    // Started for a result because WhatsApp reads the caller's package to trust the authority. The result code itself says nothing
    // (WhatsApp documents none): the caller asks the check provider instead. The contract is remembered so the launcher registers once.
    val contract = remember { StartActivityForResult() }
    val activityLauncher = rememberLauncherForActivityResult(contract) { currentOnReturned() }
    return remember(activityLauncher, authority) {
        object : WhatsAppPackLauncher {
            override fun launch(packId: String, packName: String): Boolean {
                val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK")
                    .putExtra("sticker_pack_id", packId)
                    .putExtra("sticker_pack_authority", authority)
                    .putExtra("sticker_pack_name", packName)
                return try {
                    activityLauncher.launch(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    false
                }
            }
        }
    }
}
