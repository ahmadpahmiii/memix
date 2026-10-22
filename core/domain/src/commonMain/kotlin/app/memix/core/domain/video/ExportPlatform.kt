package app.memix.core.domain.video

/*
 * What an export needs from the phone besides the engine (P1-12): a place to remember settings, a way to keep running
 * while the user leaves the app, and why the last run ended. Implemented in :core:data and :platform:services.
 */

/**
 * The export's settings and records kept on the phone, app-wide (spec P1-12 → Kept). Main-safe: every call does its
 * disk work off the main thread.
 */
interface ExportPreferences {
    /** The last resolution and frame rate exported, or null before the first export. */
    suspend fun lastChoice(): ExportChoice?

    suspend fun saveLastChoice(choice: ExportChoice)

    /** Whether Memix has asked for the notification permission at an export (asked once, ever). */
    suspend fun notificationPermissionAsked(): Boolean

    suspend fun markNotificationPermissionAsked()

    /** This phone's measured export speed for [group], or null before its first export there. */
    suspend fun secondsPerMegapixelSecond(group: SpeedGroup): Double?

    suspend fun saveSecondsPerMegapixelSecond(group: SpeedGroup, value: Double)

    /** The export running now, or one a stopped run left behind; null when none. */
    suspend fun unfinishedExport(): UnfinishedExport?

    /** Records the running export (about once a second), or clears the record with null when it ends. */
    suspend fun saveUnfinishedExport(export: UnfinishedExport?)
}

/** An export that was running: its draft and how far it got, whole percent. */
data class UnfinishedExport(val projectId: String, val percent: Int)

/**
 * Keeps an export running while the user leaves the app, and tells them how it goes (Android: a foreground service
 * and its notification). Main thread only.
 */
interface ExportForeground {
    /**
     * Starts keeping the export alive, from the user's tap while the app is visible. [controls] receive the
     * notification's Cancel and Android's own stop. False when Android refused (the export doesn't start then).
     */
    fun start(controls: ExportControls): Boolean

    /** The progress, at most once a second. [secondsLeft] is null while unknown. */
    fun showProgress(percent: Int, secondsLeft: Long?)

    /** The export ended: stops keeping it alive and leaves [notice] for the user (nothing for a cancel). */
    fun finish(notice: ExportNotice?)
}

/** What the notification can ask of a running export. */
interface ExportControls {
    /** The notification's Cancel. */
    fun cancel()

    /** Android is stopping the export (its time limit); it must end within seconds. */
    fun stoppedBySystem()
}

/** What a finished export leaves in the notification shade. */
sealed interface ExportNotice {
    /** In the gallery; [lengthLabel] like "0:08", [resolutionLabel] like "1080p". */
    data class Saved(val lengthLabel: String, val resolutionLabel: String) : ExportNotice

    data object Failed : ExportNotice

    data object StoppedBySystem : ExportNotice
}

/** Why the app's previous run ended, as far as the phone can tell. */
interface AppExitReasons {
    /** True when the user stopped the app (Android's Task Manager), false for anything else, null when unknown. */
    suspend fun lastRunStoppedByUser(): Boolean?
}
