package app.memix.core.data.settings

import app.memix.core.domain.video.ExportChoice
import app.memix.core.domain.video.ExportPreferences
import app.memix.core.domain.video.ExportResolution
import app.memix.core.domain.video.SpeedGroup
import app.memix.core.domain.video.UnfinishedExport
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [ExportPreferences] in the app's key-value settings (multiplatform-settings: SharedPreferences on Android,
 * NSUserDefaults on iOS). The first read loads the file, so every call runs on [ioDispatcher].
 */
internal class SettingsExportPreferences(
    private val settings: Settings,
    private val ioDispatcher: CoroutineDispatcher,
) : ExportPreferences {
    override suspend fun lastChoice(): ExportChoice? = withContext(ioDispatcher) {
        val resolution = settings.getStringOrNull(LAST_RESOLUTION)?.let { name -> ExportResolution.entries.firstOrNull { it.name == name } }
        val frameRate = settings.getIntOrNull(LAST_FRAME_RATE)
        if (resolution != null && frameRate != null) ExportChoice(resolution, frameRate) else null
    }

    override suspend fun saveLastChoice(choice: ExportChoice) = withContext(ioDispatcher) {
        settings.putString(LAST_RESOLUTION, choice.resolution.name)
        settings.putInt(LAST_FRAME_RATE, choice.frameRate)
    }

    override suspend fun notificationPermissionAsked(): Boolean = withContext(ioDispatcher) { settings.getBoolean(PERMISSION_ASKED, false) }

    override suspend fun markNotificationPermissionAsked() = withContext(ioDispatcher) { settings.putBoolean(PERMISSION_ASKED, true) }

    override suspend fun secondsPerMegapixelSecond(group: SpeedGroup): Double? =
        withContext(ioDispatcher) { settings.getDoubleOrNull(SPEED_PREFIX + group.name) }

    override suspend fun saveSecondsPerMegapixelSecond(group: SpeedGroup, value: Double) =
        withContext(ioDispatcher) { settings.putDouble(SPEED_PREFIX + group.name, value) }

    override suspend fun unfinishedExport(): UnfinishedExport? = withContext(ioDispatcher) {
        val projectId = settings.getStringOrNull(UNFINISHED_PROJECT) ?: return@withContext null
        UnfinishedExport(projectId, settings.getInt(UNFINISHED_PERCENT, 0))
    }

    override suspend fun saveUnfinishedExport(export: UnfinishedExport?) = withContext(ioDispatcher) {
        if (export == null) {
            settings.remove(UNFINISHED_PROJECT)
            settings.remove(UNFINISHED_PERCENT)
        } else {
            settings.putString(UNFINISHED_PROJECT, export.projectId)
            settings.putInt(UNFINISHED_PERCENT, export.percent)
        }
    }

    // Keys are saved on phones: never rename them.
    private companion object {
        const val LAST_RESOLUTION = "export.lastResolution"
        const val LAST_FRAME_RATE = "export.lastFrameRate"
        const val PERMISSION_ASKED = "export.notificationPermissionAsked"
        const val SPEED_PREFIX = "export.secondsPerMegapixelSecond."
        const val UNFINISHED_PROJECT = "export.unfinished.projectId"
        const val UNFINISHED_PERCENT = "export.unfinished.percent"
    }
}
