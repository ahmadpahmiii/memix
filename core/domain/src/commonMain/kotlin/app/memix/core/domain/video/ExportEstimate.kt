package app.memix.core.domain.video

import app.memix.core.domain.media.ImportMediaUseCase
import kotlin.math.roundToLong

/*
 * The export's bitrates and the sheet's estimate (spec docs/ux/specs/P1-12-export.md → Estimate, Free space).
 */

/**
 * The video bitrate an export asks for, bits per second, before the encoder clamps it: YouTube's recommended SDR
 * upload rates; 24 fps uses the 30 fps rate.
 */
fun requestedVideoBitrateBps(resolution: ExportResolution, frameRate: Int): Int {
    val (standard, high) = when (resolution) {
        ExportResolution.P480 -> 2_500_000 to 4_000_000
        ExportResolution.P720 -> 5_000_000 to 7_500_000
        ExportResolution.P1080 -> 8_000_000 to 12_000_000
        ExportResolution.P2K -> 16_000_000 to 24_000_000
        ExportResolution.P4K -> 40_000_000 to 60_000_000
    }
    return if (frameRate > STANDARD_MAX_FPS) high else standard
}

/** The AAC audio bitrate every export asks for. */
const val EXPORT_AUDIO_BITRATE_BPS = 128_000

/** The sheet's facts: a size the file never goes over, and about how long the export takes. */
data class ExportEstimate(val maxSizeBytes: Long, val seconds: Long)

/**
 * The estimate for [lengthUs] of video at [option] and [resolution]. The size is an upper bound: (video + audio) ×
 * length × 1.05 for the container and short overshoots. The time comes from this phone's measured speed for the
 * resolution's group ([secondsPerMegapixelSecond], learned after each export), or before the first export from the
 * video's length × the group's factor.
 */
fun estimateExport(
    option: ExportOption,
    resolution: ExportResolution,
    lengthUs: Long,
    secondsPerMegapixelSecond: Double?,
): ExportEstimate {
    val lengthSeconds = lengthUs / MICROS_PER_SECOND.toDouble()
    val bits = (option.videoBitrateBps.toLong() + EXPORT_AUDIO_BITRATE_BPS) * lengthSeconds * CONTAINER_ALLOWANCE
    val megapixels = option.frameSize.widthPx.toDouble() * option.frameSize.heightPx / PIXELS_PER_MEGAPIXEL
    val seconds = if (secondsPerMegapixelSecond != null) {
        secondsPerMegapixelSecond * megapixels * lengthSeconds
    } else {
        lengthSeconds * firstExportFactor(resolution)
    }
    return ExportEstimate(maxSizeBytes = (bits / BITS_PER_BYTE).roundToLong(), seconds = seconds.roundToLong())
}

/**
 * The free space an export needs: the size estimate plus the import's 100 MB headroom, twice the estimate when the
 * engine writes a temporary file and copies it into the gallery ([ExportCapabilities.writesStraightToGallery] false).
 */
fun exportSpaceNeededBytes(estimate: ExportEstimate, writesStraightToGallery: Boolean): Long {
    val copies = if (writesStraightToGallery) 1 else 2
    return estimate.maxSizeBytes * copies + EXPORT_HEADROOM_BYTES
}

/** Which learned speed an export of [resolution] uses: encoders speed up or slow down by size class. */
enum class SpeedGroup { UP_TO_1080P, TWO_K, FOUR_K }

fun speedGroupOf(resolution: ExportResolution): SpeedGroup = when (resolution) {
    ExportResolution.P480, ExportResolution.P720, ExportResolution.P1080 -> SpeedGroup.UP_TO_1080P
    ExportResolution.P2K -> SpeedGroup.TWO_K
    ExportResolution.P4K -> SpeedGroup.FOUR_K
}

/** This phone's speed from one finished export: seconds it took per megapixel of frame per second of video. */
fun measuredSecondsPerMegapixelSecond(tookMs: Long, option: ExportOption, lengthUs: Long): Double? {
    val megapixels = option.frameSize.widthPx.toDouble() * option.frameSize.heightPx / PIXELS_PER_MEGAPIXEL
    val lengthSeconds = lengthUs / MICROS_PER_SECOND.toDouble()
    if (megapixels <= 0 || lengthSeconds <= 0) return null
    return tookMs / MILLIS_PER_SECOND / (megapixels * lengthSeconds)
}

// First-run guesses, replaced by this phone's measurements (spec → Estimate, Assumption).
private fun firstExportFactor(resolution: ExportResolution): Double = when (speedGroupOf(resolution)) {
    SpeedGroup.UP_TO_1080P -> 1.0
    SpeedGroup.TWO_K -> 2.0
    SpeedGroup.FOUR_K -> 4.0
}

/** P1-02's headroom, so the next import fits too. */
const val EXPORT_HEADROOM_BYTES = ImportMediaUseCase.HEADROOM_BYTES

private const val STANDARD_MAX_FPS = 30
private const val CONTAINER_ALLOWANCE = 1.05
private const val BITS_PER_BYTE = 8.0
private const val MICROS_PER_SECOND = 1_000_000L
private const val MILLIS_PER_SECOND = 1_000.0
private const val PIXELS_PER_MEGAPIXEL = 1_000_000.0
