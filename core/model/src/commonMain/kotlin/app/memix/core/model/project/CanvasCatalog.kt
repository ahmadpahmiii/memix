package app.memix.core.model.project

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The video canvas's fixed rules, shared by every engine and both editors (spec docs/ux/specs/P1-11-canvas.md →
 * Ratio change, Rendering rules; design/tokens.json → canvas). Like [CaptionCatalog], these numbers are part of every
 * saved draft's look: changing one after P1 changes old drafts, so it needs a schema bump and a compensating migration
 * (CLAUDE.md rule 6).
 */
object CanvasCatalog {
    /** The preset ratios in the panel's order, each with its canvas size. The shorter side is always 1080 px. */
    val presets: List<Pair<CanvasRatio, PixelSize>> = listOf(
        CanvasRatio.RATIO_9_16 to PixelSize(1080, 1920),
        CanvasRatio.RATIO_1_1 to PixelSize(1080, 1080),
        CanvasRatio.RATIO_4_5 to PixelSize(1080, 1350),
        CanvasRatio.RATIO_3_4 to PixelSize(1080, 1440),
        CanvasRatio.RATIO_16_9 to PixelSize(1920, 1080),
    )

    /** The video canvas's default background, the color a removed background picture goes back to (the photo editor's is [PhotoCatalog.BACKGROUND]). */
    val VIDEO_BACKGROUND = ArgbColor(0xFF000000)

    /** A custom ratio's terms are whole numbers in this range. */
    val CUSTOM_TERMS: IntRange = 1..99

    /** A custom ratio's long side is at most this many times its short side (X's accepted range for video). */
    const val MAX_LONG_TO_SHORT = 2.39f

    /** A custom canvas's shorter side is about this many pixels. */
    const val SHORT_SIDE_TARGET_PX = 1080

    /** The blur background's Gaussian sigma, as a fraction of the output's shorter side (32 px at 1080p). */
    const val BLUR_SIGMA_OF_SHORT_SIDE = 0.03f

    /** On a ratio change, a layer moved in sits this far inside the edge, as a fraction of the shorter side. */
    const val LAYER_MARGIN_OF_SHORT_SIDE = 0.02f

    /** A clip within this fraction of the canvas's shape counts as the same shape: no background shows around it. */
    const val SAME_SHAPE_TOLERANCE = 0.01f

    /** [ratio]'s canvas size; null for [CanvasRatio.CUSTOM], whose size comes from its terms ([customSize]). */
    fun presetSize(ratio: CanvasRatio): PixelSize? = presets.firstOrNull { it.first == ratio }?.second

    /**
     * The canvas of the custom ratio [terms]: reduced to lowest terms w:h, it is `w·k × h·k`, where k is the even whole
     * number nearest 1080 ÷ min(w, h). Both sides come out even (encoders need that) and the ratio stays exact.
     */
    fun customSize(terms: RatioTerms): PixelSize {
        val reduced = terms.lowest()
        val k = max(EVEN, (SHORT_SIDE_TARGET_PX.toFloat() / min(reduced.width, reduced.height) / EVEN).roundToInt() * EVEN)
        return PixelSize(reduced.width * k, reduced.height * k)
    }

    /** Whether [terms] are a ratio a custom canvas may have: both in [CUSTOM_TERMS], the long side within the limit. */
    fun allowsCustom(terms: RatioTerms): Boolean =
        terms.width in CUSTOM_TERMS && terms.height in CUSTOM_TERMS && terms.longToShort() <= MAX_LONG_TO_SHORT + LIMIT_TOLERANCE

    private const val EVEN = 2

    // 2.39 isn't exact in a float; 239:100 itself is allowed.
    private const val LIMIT_TOLERANCE = 1e-4f
}

/** A ratio as two whole numbers, width to height ("9:16"). */
data class RatioTerms(val width: Int, val height: Int) {
    /** The same ratio in lowest terms (10:16 → 5:8). */
    fun lowest(): RatioTerms {
        val divisor = greatestCommonDivisor(width, height).coerceAtLeast(1)
        return RatioTerms(width / divisor, height / divisor)
    }

    /** The long side divided by the short side. */
    fun longToShort(): Float = max(width, height).toFloat() / min(width, height).coerceAtLeast(1)

    /** "9:16": digits and a colon in every language, never translated. */
    override fun toString(): String = "$width:$height"

    companion object {
        /** [size]'s ratio in lowest terms (1080 × 1728 → 5:8). */
        fun of(size: PixelSize): RatioTerms = RatioTerms(size.widthPx, size.heightPx).lowest()
    }
}

private tailrec fun greatestCommonDivisor(a: Int, b: Int): Int = if (b == 0) a else greatestCommonDivisor(b, a % b)
