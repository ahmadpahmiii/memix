package app.memix.core.model.project

/**
 * The meme formats' fixed numbers (spec docs/ux/specs/P2-02-meme-formats.md; design/tokens.json → photo.formats), in one
 * place. Every size is a share of S, the canvas's shorter side (1080 px at 1x export, 2160 px at 2x), so a format scales
 * exactly with the export size and never re-flows. Like [PhotoCatalog] and [CaptionCatalog], these are part of every saved
 * draft's look: changing one after the closed test (about 25 Jan 2027) needs a schema bump and a compensating migration
 * (CLAUDE.md rule 6).
 */
object PhotoFormatCatalog {
    /** A format's slot is filled when a visible text (or bubble) has its center this close to the slot's center, in canvas fractions. */
    const val SLOT_TAKEN_WITHIN = 0.1f

    /** A photo is "where the format put it" within these: its center this close to the expected one (a fraction of the canvas), its scale within this fraction. */
    const val AS_PLACED_CENTER_TOLERANCE = 0.005f
    const val AS_PLACED_SCALE_TOLERANCE = 0.01f

    // Classic: two text slots, this far from the top and the bottom edge (a caption of up to two lines sits inside).
    const val CLASSIC_SLOT_EDGE_DISTANCE_OF_SHORT_SIDE = 0.14f

    // Caption bar.
    const val BAR_HEIGHT_OF_SHORT_SIDE = 0.25f
    val BAR_COLOR = ArgbColor(0xFFFFFFFF)
    const val BAR_SLOT_CENTER_Y_OF_SHORT_SIDE = 0.125f

    /** The caption bar's slot ignores the last-style rule: white text on the white bar would vanish. */
    val BAR_SLOT_STYLE = CaptionStyle(fontId = "poppins", color = ArgbColor(0xFF000000), outlineColor = null)

    // Demotivational: a black poster, a white frame inset from the left, right and top, a text zone below it.
    val POSTER_COLOR = ArgbColor(0xFF000000)
    val FRAME_COLOR = ArgbColor(0xFFFFFFFF)
    const val FRAME_MARGIN_OF_SHORT_SIDE = 0.10f
    const val FRAME_BOTTOM_FROM_CANVAS_BOTTOM_OF_SHORT_SIDE = 0.40f
    const val FRAME_STROKE_OF_SHORT_SIDE = 0.005f

    /** The picture area is the frame inset this much more (a 0.015 gap plus the stroke). */
    const val FRAME_PICTURE_INSET_OF_SHORT_SIDE = 0.02f
    const val TITLE_BELOW_FRAME_OF_SHORT_SIDE = 0.09f
    const val SUBTITLE_BELOW_FRAME_OF_SHORT_SIDE = 0.21f
    val TITLE_SLOT_STYLE = CaptionStyle(fontId = "rozha", color = ArgbColor(0xFFFFFFFF), outlineColor = null)
    val SUBTITLE_SLOT_STYLE = CaptionStyle(fontId = "poppins", color = ArgbColor(0xFFFFFFFF), outlineColor = null)
    const val SUBTITLE_SLOT_SCALE = 0.45f

    // Panels.
    const val PANELS_MIN_CELLS = 2
    const val PANELS_MAX_CELLS = 6
    const val PANELS_GUTTER_OF_SHORT_SIDE = 0.012f
    const val PANELS_MARGIN_OF_SHORT_SIDE = 0.012f

    /** Each side of a divider keeps at least this share of its split's extent. */
    const val DIVIDER_MIN_SIDE_OF_EXTENT = 0.15f

    /** A drag snaps to the divider's even position within this share of the extent. */
    const val DIVIDER_SNAP_TO_EVEN_WITHIN = 0.03f

    /** A Divider-panel step and a keyboard arrow move a divider this share of its extent. */
    const val DIVIDER_STEP_OF_EXTENT = 0.02f
    val CELL_ZOOM_RANGE: ClosedFloatingPointRange<Float> = 1f..5f

    /** The cell Position panel's size steps. */
    val CELL_ZOOM_STEPS: List<Float> = listOf(1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f, 5f)

    /** A cell's Move arrow moves the picture this share of the cell's width or height. */
    const val CELL_MOVE_STEP_OF_CELL = 0.01f

    // Bubbles.
    /** The words of a bubble are laid out at this share of the caption size (4.8% of S) and wrap at [BUBBLE_WRAP_OF_SHORT_SIDE]. */
    const val BUBBLE_TEXT_SCALE_OF_CAPTION = 0.6f
    const val BUBBLE_WRAP_OF_SHORT_SIDE = 0.55f

    /** Padding around the words, in em (the bubble's font size). */
    const val BUBBLE_PADDING_X_EM = 0.45f
    const val BUBBLE_PADDING_Y_EM = 0.35f
    const val BUBBLE_MIN_WIDTH_OF_SHORT_SIDE = 0.25f
    const val BUBBLE_MIN_HEIGHT_OF_SHORT_SIDE = 0.15f
    const val SPEECH_CORNER_OF_SHORTER_SIDE = 0.35f
    val BUBBLE_DEFAULT_FILL = ArgbColor(0xFFFFFFFF)
    val BUBBLE_STROKE_COLOR = ArgbColor(0xFF000000)
    const val BUBBLE_STROKE_OF_SHORT_SIDE = 0.006f

    /** The tail is this share of the body's shorter side wide at its base. */
    const val TAIL_BASE_OF_SHORTER_SIDE = 0.25f

    /** The tail's tip stays within this many body widths and heights of the body's center. */
    const val TAIL_TIP_LIMIT_OF_BODY = 2f

    /** The default tail tip for a bubble in the top half and in the bottom half of the canvas (toward the canvas center). */
    const val TAIL_DEFAULT_X = -0.25f
    const val TAIL_DEFAULT_Y_TOP_HALF = 0.85f
    const val TAIL_DEFAULT_Y_BOTTOM_HALF = -0.85f

    /** A Position-panel Tail step moves the tip this share of the canvas (of its width or height). */
    const val TAIL_STEP_OF_CANVAS = 0.01f

    /** A thought bubble's dot chain is at full size at this reach (boundary to tip) as a share of S, and scales down below it (floor [THOUGHT_MIN_SCALE]). */
    const val THOUGHT_FULL_REACH_OF_SHORT_SIDE = 0.40f
    const val THOUGHT_MIN_SCALE = 0.40f

    /** Under [THOUGHT_MIN_SCALE] of the full reach, down to this share, two dots are drawn; under it, one. */
    const val THOUGHT_TWO_DOTS_FROM = 0.25f
    const val THOUGHT_DEFAULT_REACH_OF_SHORT_SIDE = 0.45f

    /** The three circles of a thought bubble's trail, as shares of S, big to small. */
    val THOUGHT_DOT_RADII_OF_SHORT_SIDE: List<Float> = listOf(0.08f, 0.052f, 0.032f)

    /** A new bubble lands on the first free of these `centerY` slots (top, bottom, middle), like text. */
    val BUBBLE_LANDING_SLOTS: List<Float> = PhotoCatalog.TEXT_LANDING_SLOTS

    /** The bubble's own look until the user changes it: Bubbly, black, no outline. */
    val BUBBLE_DEFAULT_STYLE = CaptionStyle(fontId = "baloo2", color = ArgbColor(0xFF000000), outlineColor = null)

    /** A bubble's scale runs like text: 25% to 500%. */
    val BUBBLE_SCALE_RANGE: ClosedFloatingPointRange<Float> = CaptionCatalog.MIN_SCALE..CaptionCatalog.MAX_SCALE
}
