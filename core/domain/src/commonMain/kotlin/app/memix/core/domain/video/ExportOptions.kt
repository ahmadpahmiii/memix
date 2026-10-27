package app.memix.core.domain.video

import app.memix.core.model.project.PixelSize

/*
 * What an export can be set to, and what this phone can do for one canvas (spec docs/ux/specs/P1-12-export.md →
 * Settings rules). Plain data: the engine fills [ExportCapabilities], the editor's sheet reads it.
 */

/** An export resolution: the name is the canvas's shorter side in pixels (a 9:16 "1080p" is 1080 × 1920). */
enum class ExportResolution(val shortSidePx: Int, val label: String) {
    P480(480, "480p"),
    P720(720, "720p"),
    P1080(1080, "1080p"),
    P2K(1440, "2K"),
    P4K(2160, "4K"),
}

/** The frame rates the sheet offers, in its order. */
val EXPORT_FRAME_RATES: List<Int> = listOf(24, 30, 60)

/** The video codec of an export. The names are shown as they are, never translated. */
enum class VideoCodec(val label: String) {
    H264("H.264"),
    HEVC("HEVC"),
}

/** How one resolution at one frame rate exports on this phone for one canvas. */
data class ExportOption(
    val codec: VideoCodec,
    /** The frame size, in the canvas's orientation, already aligned to the encoder (the shape moves under 1%). */
    val frameSize: PixelSize,
    /** The video bitrate asked for, bits per second, clamped to the encoder's range. */
    val videoBitrateBps: Int,
)

/** One resolution and frame rate. */
data class ExportChoice(val resolution: ExportResolution, val frameRate: Int)

/**
 * What this phone can export for one canvas (spec → What the phone can do): an [ExportOption] for every choice it can
 * do, and the resolutions it can do at the standard 16:9 size, which tells "the phone can't" from "this shape can't".
 */
data class ExportCapabilities(
    val options: Map<ExportChoice, ExportOption>,
    val resolutionsAtStandardShape: Set<ExportResolution>,
    /** False when the engine writes a temporary file and copies it into the gallery, which needs twice the space. */
    val writesStraightToGallery: Boolean = true,
) {
    fun optionFor(choice: ExportChoice): ExportOption? = options[choice]

    /** Whether [resolution] works at some frame rate for this canvas. */
    fun canExport(resolution: ExportResolution): Boolean = EXPORT_FRAME_RATES.any { optionFor(ExportChoice(resolution, it)) != null }

    /** Whether [frameRate] works at some resolution for this canvas. */
    fun canExport(frameRate: Int): Boolean = ExportResolution.entries.any { optionFor(ExportChoice(it, frameRate)) != null }

    /**
     * The choice to show for [wanted]: itself when it's possible, else the nearest possible one below it (resolution
     * first, then frame rate); null when nothing is possible for this canvas.
     */
    fun nearestAtOrBelow(wanted: ExportChoice): ExportChoice? {
        if (optionFor(wanted) != null) return wanted
        val resolutions = ExportResolution.entries.filter { it <= wanted.resolution }.reversed()
        val rates = EXPORT_FRAME_RATES.filter { it <= wanted.frameRate }.reversed()
        for (resolution in resolutions) {
            for (rate in rates) if (optionFor(ExportChoice(resolution, rate)) != null) return ExportChoice(resolution, rate)
        }
        return allPossible().firstOrNull()
    }

    /** The highest resolution that works for this canvas at any frame rate, or null. */
    fun highestResolution(): ExportResolution? = ExportResolution.entries.lastOrNull(::canExport)

    private fun allPossible(): List<ExportChoice> =
        ExportResolution.entries.flatMap { resolution -> EXPORT_FRAME_RATES.map { ExportChoice(resolution, it) } }.filter { optionFor(it) != null }

    companion object {
        /** Before the check has finished: every choice acts as possible (spec → Not ready yet). */
        val Unknown = ExportCapabilities(emptyMap(), ExportResolution.entries.toSet())
    }
}

/** What a tap on a resolution or frame rate in the sheet does (spec → What the phone can do, the table). */
sealed interface ChoiceResult {
    /** [choice] is selected; [steppedDown] is true when the other setting was lowered to make it work (a toast says so). */
    data class Selected(val choice: ExportChoice, val steppedDown: Boolean) : ChoiceResult

    /** The phone can't export this resolution at all (it fails at the standard 16:9 size too). */
    data object PhoneCant : ChoiceResult

    /** This resolution works at 16:9 on this phone, but not at this canvas's shape; [highest] is the best that does. */
    data class ShapeCant(val highest: ExportResolution?) : ChoiceResult
}

/**
 * Applies a tap on resolution [resolution] (the frame rate stays when it can, else steps down) to [current]. When the
 * check hasn't finished ([ExportCapabilities.Unknown]), every choice is selected as tapped.
 */
fun ExportCapabilities.tapResolution(current: ExportChoice, resolution: ExportResolution): ChoiceResult {
    if (this == ExportCapabilities.Unknown) return ChoiceResult.Selected(current.copy(resolution = resolution), steppedDown = false)
    if (!canExport(resolution)) {
        return if (resolution in resolutionsAtStandardShape) ChoiceResult.ShapeCant(highestResolution()) else ChoiceResult.PhoneCant
    }
    val wanted = ExportChoice(resolution, current.frameRate)
    if (optionFor(wanted) != null) return ChoiceResult.Selected(wanted, steppedDown = false)
    val rate = EXPORT_FRAME_RATES.filter { optionFor(ExportChoice(resolution, it)) != null }.let { rates ->
        rates.lastOrNull { it < current.frameRate } ?: rates.first()
    }
    return ChoiceResult.Selected(ExportChoice(resolution, rate), steppedDown = true)
}

/** Applies a tap on frame rate [frameRate] to [current]: the resolution steps down when it must. */
fun ExportCapabilities.tapFrameRate(current: ExportChoice, frameRate: Int): ChoiceResult {
    if (this == ExportCapabilities.Unknown) return ChoiceResult.Selected(current.copy(frameRate = frameRate), steppedDown = false)
    if (!canExport(frameRate)) return ChoiceResult.PhoneCant
    val wanted = ExportChoice(current.resolution, frameRate)
    if (optionFor(wanted) != null) return ChoiceResult.Selected(wanted, steppedDown = false)
    val resolution = ExportResolution.entries.filter { optionFor(ExportChoice(it, frameRate)) != null }.let { resolutions ->
        resolutions.lastOrNull { it < current.resolution } ?: resolutions.first()
    }
    return ChoiceResult.Selected(ExportChoice(resolution, frameRate), steppedDown = true)
}
