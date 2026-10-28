package app.memix.core.model.project

/**
 * The photo editor's fixed rules, shared by every engine and both the editor and export (spec
 * docs/ux/specs/P2-01-photo-editor-shell.md; design/tokens.json → photo). Like [CaptionCatalog] and [CanvasCatalog],
 * these numbers are part of every saved draft's look: changing one after P2 ships changes old drafts, so it needs a
 * schema bump and a compensating migration (CLAUDE.md rule 6).
 */
object PhotoCatalog {
    /** A blank canvas: 1:1 at 1080 × 1080, white. Square posts everywhere without a crop; white shows the frame on the stage. */
    val BLANK_RATIO = CanvasRatio.RATIO_1_1
    val BLANK_SIZE = PixelSize(1080, 1080)

    /** The background of every photo project, blank or started from a photo. */
    val BACKGROUND = ArgbColor(0xFFFFFFFF)

    /** A photo within this fraction of a preset ratio's shape gets that preset as its canvas. */
    const val PRESET_TOLERANCE = 0.01f

    /** Otherwise the custom ratio with the fewest terms within this fraction of the photo's shape. */
    const val CUSTOM_TOLERANCE = 0.01f

    /** A photo more extreme than a custom canvas may be gets the widest allowed shape, sitting whole inside it. */
    val WIDEST_CANVAS = RatioTerms(31, 13)
    val TALLEST_CANVAS = RatioTerms(13, 31)

    /** An image layer's scale runs 10% to 500% of its size at scale 1 (the picture fitted inside the frame). */
    val IMAGE_SCALE_RANGE: ClosedFloatingPointRange<Float> = 0.1f..5f

    /** A layer at 0% would be invisible but still selectable; Hide exists for that. */
    val OPACITY_RANGE: ClosedFloatingPointRange<Float> = 0.1f..1f

    /** The keyboard and screen-reader step of the opacity slider, so 100% is reachable. */
    const val OPACITY_STEP = 0.1f

    /** The opacity slider snaps to 100% within this. */
    const val OPACITY_SNAP_TOLERANCE = 0.03f

    /** A duplicate sits this far right and down of its original, as a fraction of the canvas's shorter side. */
    const val DUPLICATE_OFFSET_OF_SHORT_SIDE = 0.03f

    /** On a ratio change an image moves straight in until it touches the edge: no gap beside a picture. */
    const val IMAGE_MARGIN_OF_SHORT_SIDE = 0f

    /** Images are decoded at the size they are drawn at, never above this on the long side. */
    const val MAX_DECODE_PX = 4096

    /**
     * The Position panel's size steps for layers, as scales: the text ladder's range widened down to 10% for images.
     * 100% is always one of them.
     */
    val IMAGE_SIZE_STEPS: List<Float> = listOf(
        0.1f, 0.15f, 0.25f, 0.33f, 0.5f, 0.67f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f, 5f,
    )

    /** The scale range of [layer]: images (and stickers, until P2-03 gives them their own) 10% to 500%, text 25% to 500%. */
    fun scaleRangeOf(layer: Layer): ClosedFloatingPointRange<Float> = when (layer) {
        is TextLayer -> CaptionCatalog.MIN_SCALE..CaptionCatalog.MAX_SCALE
        is ImageLayer, is StickerLayer -> IMAGE_SCALE_RANGE
    }

    /** How far inside the edge [layer] moves on a ratio change, as a fraction of the shorter side. */
    fun ratioChangeMarginOf(layer: Layer): Float = when (layer) {
        is ImageLayer -> IMAGE_MARGIN_OF_SHORT_SIDE
        is TextLayer, is StickerLayer -> CanvasCatalog.LAYER_MARGIN_OF_SHORT_SIDE
    }
}
