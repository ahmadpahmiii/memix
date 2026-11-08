package app.memix.platform.services

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.core.content.FileProvider
import app.memix.core.domain.photo.PictureClipboard
import java.io.File

/**
 * Copies a picture as an image (spec docs/ux/specs/P2-08-photo-export.md → Copy): the cache file's content URI from the app's
 * FileProvider in a `ClipData`, so a chat app that pastes reads the file through Android's grant, not the app's storage.
 * The FileProvider and its path list are in the app's manifest (`:androidApp`), authority `<package>.photocopies`.
 *
 * Android 13 and up shows its own preview of what was copied, so the app adds no confirmation there ([confirmsCopyItself]).
 */
internal class AndroidPictureClipboard(private val context: Context) : PictureClipboard {
    override val confirmsCopyItself: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun copyPicture(path: String, mimeType: String): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", File(path))
            clipboard.setPrimaryClip(ClipData(ClipDescription(CLIP_LABEL, arrayOf(mimeType)), ClipData.Item(uri)))
            true
        } catch (e: IllegalArgumentException) {
            // A path outside the provider's list, or a file that is gone.
            false
        } catch (e: SecurityException) {
            false
        }
    }

    private companion object {
        const val AUTHORITY_SUFFIX = ".photocopies"
        const val CLIP_LABEL = "Memix picture"
    }
}
