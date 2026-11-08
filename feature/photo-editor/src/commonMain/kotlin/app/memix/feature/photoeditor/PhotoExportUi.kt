package app.memix.feature.photoeditor

import androidx.compose.runtime.Immutable
import app.memix.core.domain.photo.PhotoExportAction
import app.memix.core.domain.photo.PhotoExportSettings
import app.memix.core.domain.video.ExportError
import app.memix.core.model.project.PixelSize

/**
 * The Export sheet (spec docs/ux/specs/P2-08-photo-export.md → Export sheet): what was chosen, what that makes (the output size
 * and a size guess), whether the phone has the room, and what is happening now. Nothing here changes the draft.
 */
@Immutable
data class PhotoExportSheetUi(
    val settings: PhotoExportSettings,
    /** The file's pixels, the canvas scaled to the chosen shorter side. */
    val output: PixelSize,
    /** About how big the file will be (spec → Estimate). */
    val estimateBytes: Long,
    /** How much more room Save needs, rounded up to 10 MB; null when there is enough. */
    val saveShortBytes: Long? = null,
    /** The total Save needs free, which Free up space asks the storage screen for (never the missing part). */
    val saveNeededBytes: Long = 0L,
    /** Whether Copy's single cache file doesn't fit. */
    val copyShort: Boolean = false,
    /** A picture's file is missing: it is left out of the file. */
    val missingPicture: Boolean = false,
    /** Saving or copying right now: the tapped button reads "Saving…" or "Copying…" and everything is inert. */
    val working: PhotoExportAction? = null,
    /** The last attempt's failure, kept until the next tap or setting change. */
    val failure: ExportError? = null,
)

sealed interface PhotoExportIntent {
    /** The sheet's Format segments: 0 is JPG, 1 is PNG. */
    data class Format(val index: Int) : PhotoExportIntent

    /** The Quality slider moved (live, in steps of 5). */
    data class Quality(val quality: Int) : PhotoExportIntent

    /** The Quality slider was let go: the free-space check runs again. */
    data object QualityReleased : PhotoExportIntent

    /** The Size segments: 0 is 1080 px, 1 is 2160 px. */
    data class Size(val index: Int) : PhotoExportIntent

    data object Save : PhotoExportIntent

    data object Copy : PhotoExportIntent

    /** Close, the scrim or back: cancels the work if there is any. */
    data object Close : PhotoExportIntent

    /** Back from the storage screen or another app: the free space may have changed. */
    data object RecheckSpace : PhotoExportIntent
}
