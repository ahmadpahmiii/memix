package app.memix.core.domain.photo

import app.memix.core.domain.project.layers
import app.memix.core.domain.video.EXPORT_HEADROOM_BYTES
import app.memix.core.model.project.PhotoExportCatalog
import app.memix.core.model.project.PhotoExportFormat
import app.memix.core.model.project.PanelsFormat
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import kotlin.math.roundToLong

/*
 * The sheet's size guess and the space a picture needs (spec docs/ux/specs/P2-08-photo-export.md → Estimate, Free space).
 */

/**
 * Whether there is anything to export (spec P2-08 → Export button): a visible layer, or a Panels cell with a photo in it. A
 * canvas with only hidden layers, or a format with nothing in it, makes an empty picture, so Export explains instead.
 */
fun Project.hasPhotoContent(): Boolean =
    layers().any { !it.hidden } || (photo?.format as? PanelsFormat)?.cells?.any { it.source != null } == true

/**
 * About how big the file is: the output's megapixels times the table's rate, or, once this phone has exported in the
 * same format and quality band, times that measured rate ([measuredMbPerMegapixel]). "About", not an upper bound: a
 * meme's JPG or PNG can land well under or a little over.
 */
fun estimatePhotoBytes(format: PhotoExportFormat, quality: Int, output: PixelSize, measuredMbPerMegapixel: Double?): Long {
    val megapixels = output.widthPx.toDouble() * output.heightPx / PIXELS_PER_MEGAPIXEL
    val rate = measuredMbPerMegapixel ?: PhotoExportCatalog.estimateMbPerMegapixel(format, quality)
    return (megapixels * rate * BYTES_PER_MB).roundToLong()
}

/** This export's rate, to learn from: megabytes per megapixel of output. Null for an empty picture. */
fun measuredMbPerMegapixel(sizeBytes: Long, output: PixelSize): Double? {
    val megapixels = output.widthPx.toDouble() * output.heightPx / PIXELS_PER_MEGAPIXEL
    if (megapixels <= 0.0) return null
    return sizeBytes / BYTES_PER_MB / megapixels
}

/** Save writes through a temporary copy in some cases, so it needs twice the estimate plus P1-02's headroom. */
fun photoSaveSpaceNeededBytes(estimateBytes: Long): Long = estimateBytes * SAVE_COPIES + EXPORT_HEADROOM_BYTES

/** Copy only needs the cache for one file: the estimate plus the headroom. */
fun photoCopySpaceNeededBytes(estimateBytes: Long): Long = estimateBytes + EXPORT_HEADROOM_BYTES

private const val SAVE_COPIES = 2
private const val PIXELS_PER_MEGAPIXEL = 1_000_000.0
private const val BYTES_PER_MB = 1_000_000.0
