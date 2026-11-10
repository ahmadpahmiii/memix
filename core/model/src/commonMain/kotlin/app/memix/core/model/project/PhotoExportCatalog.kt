package app.memix.core.model.project

import kotlin.math.roundToInt

/** The file formats a photo leaves the editor in (spec docs/ux/specs/P2-08-photo-export.md). The names are shown as they are and never translated. */
enum class PhotoExportFormat(val label: String, val mimeType: String, val fileExtension: String, val eventValue: String) {
    JPG("JPG", "image/jpeg", "jpg", "jpg"),
    PNG("PNG", "image/png", "png", "png"),
}

/**
 * The photo export's fixed rules (design/tokens.json → photo.export): which sizes and qualities the sheet offers, what they
 * start at, and the size guess shown before saving. Behavior, not chrome. The remembered choices live on the phone, never in
 * a draft, so changing a value here needs no schema bump.
 */
object PhotoExportCatalog {
    /** First use of the sheet: JPG, because chats recompress anyway and its files are a fraction of a PNG's (delegated, 5 Nov 2026). */
    val DEFAULT_FORMAT = PhotoExportFormat.JPG

    /** The canvas's shorter side, in pixels, that an export offers: 1x and 2x of the canvas size. */
    val SHORT_SIDES_PX: List<Int> = listOf(1080, 2160)
    const val DEFAULT_SHORT_SIDE_PX = 1080

    const val QUALITY_MIN = 50
    const val QUALITY_MAX = 100
    const val QUALITY_STEP = 5
    const val DEFAULT_QUALITY = 90

    /** The Pictures sub-folder the gallery shows as "Memix". */
    const val ALBUM_PATH = "Pictures/Memix"

    /** Estimated megabytes per megapixel of output for a PNG; a meme's flat colors and text compress well, photos do not. */
    private const val PNG_MB_PER_MEGAPIXEL = 2.2

    /** Estimated megabytes per megapixel of output for a JPG, by quality (an assumption until this phone has exported once). */
    private val jpgMbPerMegapixel: Map<Int, Double> = mapOf(
        50 to 0.25, 55 to 0.27, 60 to 0.30, 65 to 0.33, 70 to 0.35, 75 to 0.40,
        80 to 0.45, 85 to 0.55, 90 to 0.65, 95 to 0.95, 100 to 1.8,
    )

    /** [quality] pulled onto the slider's grid and range: whatever was remembered, the sheet shows a value it can show. */
    fun snappedQuality(quality: Int): Int {
        val steps = ((quality.coerceIn(QUALITY_MIN, QUALITY_MAX) - QUALITY_MIN).toFloat() / QUALITY_STEP).roundToInt()
        return QUALITY_MIN + steps * QUALITY_STEP
    }

    /**
     * Which learned size a JPG at [quality] counts toward: qualities whose table rates sit close share a band (50 to 65,
     * 70 to 80, 85 to 90, 95, 100), so one export teaches the neighbours. PNG has no quality and one band.
     */
    fun qualityBandOf(format: PhotoExportFormat, quality: Int): Int = when {
        format == PhotoExportFormat.PNG -> 0
        quality <= 65 -> 1
        quality <= 80 -> 2
        quality <= 90 -> 3
        quality < QUALITY_MAX -> 4
        else -> 5
    }

    /** The table's guess in megabytes per megapixel of output for [format] at [quality] (ignored for PNG). */
    fun estimateMbPerMegapixel(format: PhotoExportFormat, quality: Int): Double = when (format) {
        PhotoExportFormat.PNG -> PNG_MB_PER_MEGAPIXEL
        PhotoExportFormat.JPG -> jpgMbPerMegapixel.getValue(snappedQuality(quality))
    }

    /**
     * The output's pixel size: [canvas] scaled so its shorter side is exactly [shortSidePx], the longer side by the canvas
     * ratio, rounded. A square canvas gives a square.
     */
    fun outputSize(canvas: Canvas, shortSidePx: Int): PixelSize {
        val canvasShort = minOf(canvas.widthPx, canvas.heightPx)
        fun scaled(side: Int) = if (side == canvasShort) shortSidePx else (shortSidePx.toLong() * side / canvasShort.toDouble()).roundToInt()
        return PixelSize(scaled(canvas.widthPx), scaled(canvas.heightPx))
    }
}
