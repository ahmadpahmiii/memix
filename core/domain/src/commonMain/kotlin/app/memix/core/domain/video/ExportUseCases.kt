package app.memix.core.domain.video

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.media.MediaFiles
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.ProjectType

/** What this phone can export for a canvas ([VideoEngine.exportCapabilities]); warm it when the editor opens. */
class GetExportCapabilitiesUseCase(private val engine: VideoEngine) {
    suspend operator fun invoke(canvasSize: PixelSize): ExportCapabilities = engine.exportCapabilities(canvasSize)
}

/** The export sheet's remembered choice, speeds and free space (spec P1-12 → Settings rules). Main-safe. */
class ExportSheetFactsUseCase(private val preferences: ExportPreferences, private val mediaFiles: MediaFiles) {
    /** The last resolution and frame rate, or 1080p at 30 fps the first time. */
    suspend fun lastChoice(): ExportChoice = preferences.lastChoice() ?: DEFAULT_CHOICE

    suspend fun secondsPerMegapixelSecond(resolution: ExportResolution): Double? =
        preferences.secondsPerMegapixelSecond(speedGroupOf(resolution))

    /** Bytes the app could still write. */
    suspend fun freeBytes(): Long = mediaFiles.allocatableBytes()

    companion object {
        val DEFAULT_CHOICE = ExportChoice(ExportResolution.P1080, ExportSettings.DEFAULT_FRAME_RATE)
    }
}

/** The notification permission is asked once ever, at the first Export video (owner, 21 Oct 2026). Main-safe. */
class ExportNotificationPermissionUseCase(private val preferences: ExportPreferences) {
    /** True only the first time: it records the ask, so the next call is false whatever the answer was. */
    suspend fun shouldAskNow(): Boolean {
        if (preferences.notificationPermissionAsked()) return false
        preferences.markNotificationPermissionAsked()
        return true
    }
}

/**
 * At start-up, after a run that was stopped during an export (spec P1-12 → Memix stopped mid-export): deletes the
 * unfinished gallery file, counts it (`export_cancel` with the last percent when the user stopped Memix in the Task
 * Manager, else `export_fail` `process_death`) and returns it for the next-start toast; null when the last run ended
 * normally. Main-safe; call once per process.
 */
class RecoverUnfinishedExportUseCase(
    private val preferences: ExportPreferences,
    private val exitReasons: AppExitReasons,
    private val engine: VideoEngine,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(): UnfinishedExport? {
        val unfinished = preferences.unfinishedExport() ?: return null
        preferences.saveUnfinishedExport(null)
        engine.discardUnfinishedExports()
        // Only the video editor exports in P1 (the photo editor's export is P2).
        val event = if (exitReasons.lastRunStoppedByUser() == true) {
            AnalyticsEvent.ExportCancel(ProjectType.VIDEO, unfinished.percent)
        } else {
            AnalyticsEvent.ExportFail(ProjectType.VIDEO, ExportError.PROCESS_DEATH.eventValue)
        }
        analytics.log(event)
        return unfinished
    }
}
