package app.memix.core.model.project

/**
 * The caption look's fixed content, shared by every engine and both editors (spec docs/ux/specs/P1-10-text.md →
 * Caption rendering; design/tokens.json → caption). These numbers are part of every saved draft's look: changing one
 * after P1 changes old drafts, so it needs a schema bump and a compensating migration (CLAUDE.md rule 6).
 */
object CaptionCatalog {
    /** The five caption fonts, in the Style panel's order. Ids are saved in drafts and never renamed. */
    val fonts: List<CaptionFont> = listOf(
        // Anton has no Devanagari: every Devanagari cluster is drawn in Teko Bold (P1-17's file) instead.
        CaptionFont("anton", fileName = "anton_regular.ttf", devanagariFallbackFileName = "teko_bold.ttf"),
        CaptionFont("poppins", fileName = "poppins_bold.ttf"),
        CaptionFont("baloo2", fileName = "baloo2_extrabold.ttf"),
        CaptionFont("kalam", fileName = "kalam_bold.ttf"),
        CaptionFont("rozha", fileName = "rozha_one_regular.ttf"),
    )

    /** Classic: the font a draft's unknown font id is drawn in (a newer build's draft). */
    val classic: CaptionFont = fonts.first()

    /** [fontId]'s font, or [classic] when this build doesn't bundle it. */
    fun fontOf(fontId: String): CaptionFont = fonts.firstOrNull { it.id == fontId } ?: classic

    /** The ten caption colors for text and outline, in palette order. Content, never interface colors. */
    val palette: List<ArgbColor> = listOf(
        ArgbColor(0xFFFFFFFF), // White
        ArgbColor(0xFF000000), // Black
        ArgbColor(0xFFFFD60A), // Yellow
        ArgbColor(0xFFFF9500), // Orange
        ArgbColor(0xFFFF3B30), // Red
        ArgbColor(0xFFFF6BC1), // Pink
        ArgbColor(0xFFAF52DE), // Purple
        ArgbColor(0xFF0A84FF), // Blue
        ArgbColor(0xFF64D2FF), // Light blue
        ArgbColor(0xFF30D158), // Green
    )

    /** Classic, white, black outline: the meme look that reads over any video. */
    val defaultStyle = CaptionStyle(fontId = classic.id, color = palette[0], outlineColor = palette[1])

    /** Font size at scale 1, as a fraction of the canvas's shorter side (86 px on 1080 × 1920). */
    const val FONT_SIZE_OF_SHORT_SIDE = 0.08f

    /** Lines wrap at this fraction of the canvas's shorter side, at scale 1. */
    const val WRAP_WIDTH_OF_SHORT_SIDE = 0.9f

    /** The outline's stroke width is the font size divided by this (round joins, drawn under the fill). */
    const val OUTLINE_WIDTH_DIVISOR = 12f

    const val MIN_SCALE = 0.25f
    const val MAX_SCALE = 5f

    /** The most a caption holds, counted as user-perceived characters (grapheme clusters). */
    const val MAX_CHARACTERS = 250
}

/**
 * A bundled caption font: [fileName] in the design system's font resources, and for Classic the font that draws
 * Devanagari ([devanagariFallbackFileName]). Anything none of them has falls back to the system font.
 */
data class CaptionFont(val id: String, val fileName: String, val devanagariFallbackFileName: String? = null)
