package app.memix.platform.services

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.memix.core.domain.share.Sharer
import app.memix.core.domain.video.AppExitReasons
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The Android share sheet for an exported video (spec P1-12 → Share): `ACTION_SEND` with `video/mp4`, the gallery
 * item's content URI and read permission for the chosen app, in a chooser with the given title.
 */
internal class AndroidSharer(private val context: Context) : Sharer {
    override fun shareVideo(location: String, chooserTitle: String): Boolean {
        val uri = Uri.parse(location)
        val send = Intent(Intent.ACTION_SEND)
            .setType(VIDEO_MP4)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the grant through the chooser to the app the user picks.
        send.clipData = ClipData.newRawUri(null, uri)
        val chooser = Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private companion object {
        const val VIDEO_MP4 = "video/mp4"
    }
}

/**
 * Why Memix's last process ended, from Android's exit records (API 30+; Android 10 has none, so null there). A stop in
 * the Task Manager is `REASON_USER_REQUESTED` (developer.android.com → FGS → handle user stopping).
 */
internal class AndroidAppExitReasons(private val context: Context, private val ioDispatcher: CoroutineDispatcher) : AppExitReasons {
    override suspend fun lastRunStoppedByUser(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return withContext(ioDispatcher) {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val last = activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return@withContext null
            last.reason == ApplicationExitInfo.REASON_USER_REQUESTED
        }
    }
}
