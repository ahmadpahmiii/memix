package app.memix.core.data.settings

import app.memix.core.domain.photo.PhotoExportPreferences
import app.memix.core.domain.photo.PhotoExportSettings
import app.memix.core.model.project.PhotoExportCatalog
import app.memix.core.model.project.PhotoExportFormat
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [PhotoExportPreferences] in the app's key-value settings, apart from video's keys (spec P2-08 → Defaults). Every call runs on [ioDispatcher]. */
internal class SettingsPhotoExportPreferences(
    private val settings: Settings,
    private val ioDispatcher: CoroutineDispatcher,
) : PhotoExportPreferences {
    override suspend fun lastSettings(): PhotoExportSettings? = withContext(ioDispatcher) {
        val format = settings.getStringOrNull(LAST_FORMAT)?.let { name -> PhotoExportFormat.entries.firstOrNull { it.name == name } }
        val quality = settings.getIntOrNull(LAST_QUALITY)
        val shortSide = settings.getIntOrNull(LAST_SHORT_SIDE)
        if (format != null && quality != null && shortSide != null) PhotoExportSettings(format, quality, shortSide) else null
    }

    override suspend fun saveLastSettings(settings: PhotoExportSettings) = withContext(ioDispatcher) {
        this@SettingsPhotoExportPreferences.settings.putString(LAST_FORMAT, settings.format.name)
        this@SettingsPhotoExportPreferences.settings.putInt(LAST_QUALITY, settings.quality)
        this@SettingsPhotoExportPreferences.settings.putInt(LAST_SHORT_SIDE, settings.shortSidePx)
    }

    override suspend fun measuredMbPerMegapixel(format: PhotoExportFormat, quality: Int): Double? =
        withContext(ioDispatcher) { settings.getDoubleOrNull(measuredKey(format, quality)) }

    override suspend fun saveMeasuredMbPerMegapixel(format: PhotoExportFormat, quality: Int, value: Double) =
        withContext(ioDispatcher) { settings.putDouble(measuredKey(format, quality), value) }

    private fun measuredKey(format: PhotoExportFormat, quality: Int) =
        "$MEASURED_PREFIX${format.name}.${PhotoExportCatalog.qualityBandOf(format, quality)}"

    // Keys are saved on phones: never rename them.
    private companion object {
        const val LAST_FORMAT = "photoExport.lastFormat"
        const val LAST_QUALITY = "photoExport.lastQuality"
        const val LAST_SHORT_SIDE = "photoExport.lastShortSide"
        const val MEASURED_PREFIX = "photoExport.mbPerMegapixel."
    }
}
