package app.memix.core.model.project

/**
 * The "Memix" mark on free exports (spec docs/ux/specs/P1-13-watermark.md; design/tokens.json → watermark). Content, not
 * chrome: drawn into the exported file by each engine's one text renderer as the top-most overlay, never in the editor
 * preview and never saved in a draft, so changing a value needs no schema bump and affects only new exports.
 *
 * Everything is sized from the output's shorter side, so the mark looks the same at 480p, 1080p and 4K and in every canvas
 * shape. The margins are measured to the glyphs' drawn bounds, not the font's line box.
 */
object WatermarkCatalog {
    /** Never translated, never uppercased. */
    const val TEXT = "Memix"

    /** The wordmark face, one of [CaptionCatalog.fonts] (already bundled). */
    const val FONT_ID = "anton"

    /** Font size as a fraction of the output's shorter side: 43 px at 1080p. */
    const val FONT_SIZE_OF_SHORT_SIDE = 0.04f

    /**
     * The gap from the left edge to the ink box, as a fraction of the shorter side (owner, 8 Oct 2026: 3%). Top and left
     * are separate so the owner's phase-end test on TikTok can move the top with this one value (spec → Question for
     * the owner: B is about 0.13).
     */
    const val MARGIN_LEFT_OF_SHORT_SIDE = 0.03f

    /** The gap from the top edge to the ink box, as a fraction of the shorter side. */
    const val MARGIN_TOP_OF_SHORT_SIDE = 0.03f

    /** White at 80% opacity, 0xAARRGGBB. */
    const val FILL_ARGB = 0xCCFFFFFFL

    /** Black at 50% opacity, 0xAARRGGBB; drawn with no offset. */
    const val SHADOW_ARGB = 0x80000000L

    /** The shadow's blur radius as a fraction of the font size. */
    const val SHADOW_BLUR_OF_FONT_SIZE = 0.15f

    /** The font size, in pixels, for an output whose shorter side is [shortSidePx]. */
    fun fontSizePx(shortSidePx: Int): Float = shortSidePx * FONT_SIZE_OF_SHORT_SIDE

    /** Where the ink box's left edge goes, in pixels from the left edge of the frame. */
    fun marginLeftPx(shortSidePx: Int): Float = shortSidePx * MARGIN_LEFT_OF_SHORT_SIDE

    /** Where the ink box's top edge goes, in pixels from the top edge of the frame. */
    fun marginTopPx(shortSidePx: Int): Float = shortSidePx * MARGIN_TOP_OF_SHORT_SIDE
}
