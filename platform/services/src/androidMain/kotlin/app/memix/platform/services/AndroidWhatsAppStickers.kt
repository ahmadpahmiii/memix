package app.memix.platform.services

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import app.memix.core.domain.Logger
import app.memix.core.domain.stickerpack.WhatsAppCheck
import app.memix.core.domain.stickerpack.WhatsAppStickers
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Asks the installed WhatsApp apps whether a pack is added, through the check provider each of them offers (WhatsApp's Android README,
 * "Check if pack is added"). A pack counts as added when any installed WhatsApp has it. The manifest's `<queries>` lets Android 11+ see both packages.
 */
internal class AndroidWhatsAppStickers(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : WhatsAppStickers {
    override val isSupported: Boolean = true

    override suspend fun check(packId: String): WhatsAppCheck = withContext(ioDispatcher) {
        val installed = WHATSAPP_PACKAGES.filter(::isInstalled)
        if (installed.isEmpty()) return@withContext WhatsAppCheck.NOT_INSTALLED
        val answers = installed.map { isWhitelisted(it, packId) }
        when {
            answers.any { it == true } -> WhatsAppCheck.ADDED
            // No answer from any of them: no check provider (too old), or a query it refused.
            answers.all { it == null } -> WhatsAppCheck.TOO_OLD
            else -> WhatsAppCheck.NOT_ADDED
        }
    }

    private fun isInstalled(packageName: String): Boolean = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0)
        }
        info.enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    // 1 -> true, 0 -> false, no answer -> null.
    private fun isWhitelisted(packageName: String, packId: String): Boolean? {
        val checkAuthority = "$packageName.provider.sticker_whitelist_check"
        if (context.packageManager.resolveContentProvider(checkAuthority, 0) == null) return null
        val uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(checkAuthority)
            .appendPath("is_whitelisted")
            .appendQueryParameter("authority", StickerPackAuthority.of(context))
            .appendQueryParameter("identifier", packId)
            .build()
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow("result")) == 1 else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // A WhatsApp that refuses or fails the query counts as "no answer"; the user can still tap Add again.
            logger.error(TAG, "WhatsApp's check failed for $packageName", e)
            null
        }
    }

    private companion object {
        const val TAG = "MemixStickerPacks"
        val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
