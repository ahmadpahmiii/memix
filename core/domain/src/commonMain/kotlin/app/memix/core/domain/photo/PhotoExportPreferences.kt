package app.memix.core.domain.photo

import app.memix.core.model.project.PhotoExportFormat

/**
 * The photo export's remembered choices and learned sizes, app-wide and separate from video's (spec P2-08 → Defaults).
 * Never in a draft. Main-safe: every call does its disk work off the main thread.
 */
interface PhotoExportPreferences {
    /** The last format, quality and size exported, or null before the first export. */
    suspend fun lastSettings(): PhotoExportSettings?

    suspend fun saveLastSettings(settings: PhotoExportSettings)

    /** This phone's measured megabytes per megapixel for [format] at [quality]'s band, or null before the first export there. */
    suspend fun measuredMbPerMegapixel(format: PhotoExportFormat, quality: Int): Double?

    suspend fun saveMeasuredMbPerMegapixel(format: PhotoExportFormat, quality: Int, value: Double)
}
