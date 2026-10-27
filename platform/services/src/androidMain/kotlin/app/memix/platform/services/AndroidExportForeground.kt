package app.memix.platform.services

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.memix.core.domain.Logger
import app.memix.core.domain.video.ExportControls
import app.memix.core.domain.video.ExportForeground
import app.memix.core.domain.video.ExportNotice
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Keeps an export running while the user leaves Memix (spec P1-12 → Notification; research P1-12-encoder-facts.md →
 * 3, 5): [ExportForegroundService] in the foreground with the progress notification, and one notification for how it
 * ended. Without the notification permission (Android 13+) the service still runs and shows only in Android's Task
 * Manager; nothing here depends on the notification being seen. Main thread only.
 */
internal class AndroidExportForeground(private val context: Context, private val logger: Logger) : ExportForeground {
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private var controls: ExportControls? = null
    private var running = false

    /** The running export's controls, for the service's Cancel and Android's time limit. */
    fun controls(): ExportControls? = controls

    override fun start(controls: ExportControls): Boolean {
        createChannel()
        notifications.cancel(RESULT_ID)
        this.controls = controls
        return try {
            // Started from the user's tap while Memix is on screen, as Android 12+ requires.
            context.startForegroundService(Intent(context, ExportForegroundService::class.java))
            running = true
            true
        } catch (e: IllegalStateException) {
            refused(e)
        } catch (e: SecurityException) {
            refused(e)
        }
    }

    override fun showProgress(percent: Int, secondsLeft: Long?) {
        if (running) notifications.notify(PROGRESS_ID, progressNotification(percent, secondsLeft))
    }

    override fun finish(notice: ExportNotice?) {
        running = false
        controls = null
        context.stopService(Intent(context, ExportForegroundService::class.java))
        // The screen already says how it ended when Memix is in front.
        if (notice != null && !isInFront()) notifications.notify(RESULT_ID, resultNotification(notice))
    }

    private fun isInFront(): Boolean {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    /** The notification the service starts with and every update replaces. */
    fun progressNotification(percent: Int, secondsLeft: Long?): Notification {
        val percentText = context.getString(R.string.notif_percent, percent)
        val text = secondsLeft?.let { context.getString(R.string.notif_exporting_text, percentText, durationText(it)) } ?: percentText
        val cancel = PendingIntent.getService(
            context,
            CANCEL_REQUEST,
            Intent(context, ExportForegroundService::class.java).setAction(ExportForegroundService.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = baseBuilder()
            .setContentTitle(context.getString(R.string.notif_exporting_title))
            .setContentText(text)
            .setProgress(PERCENT, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // A notification with an action shows at once on Android 12+, instead of up to 10 s later.
            .addAction(Notification.Action.Builder(null, context.getString(R.string.notif_cancel), cancel).build())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return builder.build()
    }

    private fun resultNotification(notice: ExportNotice): Notification {
        val (title, text) = when (notice) {
            is ExportNotice.Saved ->
                context.getString(R.string.notif_done_title) to context.getString(R.string.notif_done_text, notice.lengthLabel, notice.resolutionLabel)
            ExportNotice.Failed -> context.getString(R.string.notif_failed_title) to context.getString(R.string.notif_failed_text)
            ExportNotice.StoppedBySystem -> context.getString(R.string.notif_stopped_title) to context.getString(R.string.notif_stopped_text)
        }
        return baseBuilder().setContentTitle(title).setContentText(text).setAutoCancel(true).build()
    }

    // Tapping opens Memix where it was: the Exporting screen covers the editor until the user leaves it.
    private fun baseBuilder(): Notification.Builder {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open = PendingIntent.getActivity(context, OPEN_REQUEST, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_export)
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_PROGRESS)
    }

    private fun durationText(seconds: Long): String =
        if (seconds < SECONDS_PER_MINUTE) {
            context.getString(R.string.notif_seconds, roundedSeconds(seconds))
        } else {
            context.getString(R.string.notif_minutes, ((seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE).toInt())
        }

    // Nearest 5 s under a minute (spec P1-12 → Estimate); never "0 s".
    private fun roundedSeconds(seconds: Long): Int = maxOf(FIVE_SECONDS, ((seconds + 2) / FIVE_SECONDS * FIVE_SECONDS).toInt())

    // Low importance: no sound (spec → Notification). Creating it again is a no-op.
    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_exports), NotificationManager.IMPORTANCE_LOW)
        notifications.createNotificationChannel(channel)
    }

    // Android 12+ refuses a foreground service started from the background; 15+ also when the time budget is used.
    private fun refused(e: Exception): Boolean {
        logger.error(TAG, "The export service couldn't start", e)
        controls = null
        return false
    }

    companion object {
        const val PROGRESS_ID = 4_101
        private const val RESULT_ID = 4_102
        private const val CHANNEL_ID = "exports"
        private const val CANCEL_REQUEST = 1
        private const val OPEN_REQUEST = 2
        private const val PERCENT = 100
        private const val SECONDS_PER_MINUTE = 60L
        private const val FIVE_SECONDS = 5
        private const val TAG = "MemixExport"
    }
}

/**
 * The foreground service an export runs under (Android 10+). It does no work itself: the export runs on the engine's
 * thread; this keeps Memix's process alive and carries the notification and its Cancel. Type mediaProcessing on
 * Android 15+, through [Service.startForeground] directly (androidx ServiceCompat 1.19.1 drops that type), dataSync on
 * 10–14.
 */
class ExportForegroundService : Service(), KoinComponent {
    private val foreground: ExportForeground by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val export = foreground as AndroidExportForeground
        val controls = export.controls()
        if (controls == null) {
            // Nothing is exporting (a late Cancel tap, or Android restarting the service): stop at once.
            startTyped(export.progressNotification(0, null))
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_CANCEL) {
            controls.cancel()
            return START_NOT_STICKY
        }
        startTyped(export.progressNotification(0, null))
        return START_NOT_STICKY
    }

    // Android 15+: the mediaProcessing budget (6 h a day) ran out. The export ends and the service stops within seconds.
    override fun onTimeout(startId: Int, fgsType: Int) {
        (foreground as AndroidExportForeground).controls()?.stoppedBySystem()
        stopSelf()
    }

    private fun startTyped(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        try {
            startForeground(AndroidExportForeground.PROGRESS_ID, notification, type)
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+): Android stopped it before it began.
            (foreground as AndroidExportForeground).controls()?.stoppedBySystem()
            stopSelf()
        }
    }

    companion object {
        const val ACTION_CANCEL = "app.memix.export.CANCEL"
    }
}
