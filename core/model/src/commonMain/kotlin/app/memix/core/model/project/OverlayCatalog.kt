package app.memix.core.model.project

/**
 * The fixed rules of overlay layers (P4-01: video and photo clips floating over the main video; spec
 * docs/ux/specs/P4-01b-overlay-layers.md, design/tokens.json → overlay). Never read through MemixTheme. Part of every saved draft's
 * look, like [PhotoCatalog]: changing a number after Phase 4 ships changes old drafts, so it needs a schema bump (CLAUDE.md rule 6).
 */
object OverlayCatalog {
    /** An overlay's scale runs 10% to 500% of the picture fitted whole inside the canvas: the same numbers as `PhotoCatalog.IMAGE_SCALE_RANGE` (copied, so a photo change doesn't move overlays). */
    val SCALE_RANGE: ClosedFloatingPointRange<Float> = 0.1f..5f

    /** An overlay at 0% would be invisible but still selectable; delete exists for that. The same numbers as `PhotoCatalog.OPACITY_RANGE`. */
    val OPACITY_RANGE: ClosedFloatingPointRange<Float> = 0.1f..1f

    /** The Position panel's Size steps (spec P4-01b → Position panel): the photo layers' ladder, 100% being the overlay fitted inside the canvas. */
    val SIZE_STEPS: List<Float> = listOf(0.1f, 0.15f, 0.25f, 0.33f, 0.5f, 0.67f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f, 5f)

    /** A slot is taken when an overlay showing at the playhead has its center within this fraction of the canvas of it. */
    const val SLOT_TAKEN_WITHIN = 0.1f

    /**
     * Where a new overlay's center lands, first free slot first: the center, lower right, upper right, lower left, upper left
     * (fractions of the canvas); all taken: the center (spec P4-01b → Landing).
     */
    val LANDING_SLOTS: List<Pair<Float, Float>> = listOf(0.5f to 0.5f, 0.7f to 0.7f, 0.7f to 0.3f, 0.3f to 0.7f, 0.3f to 0.3f)

    /** On a ratio change an overlay is shrunk to fit inside the canvas, but not below this scale (built with P4-01b). */
    const val RATIO_CHANGE_SHRINK_FLOOR = 0.1f

    /** A new overlay lands at half the size of the fitted picture, centered, so the main video stays visible around it. */
    const val LANDING_SCALE = 0.5f

    /** A new photo overlay shows this long (the same as a photo on the main track). */
    const val PHOTO_LANDING_US = 3_000_000L

    /** An overlay landing with less than this left in the video moves back so it ends at the end of the video instead of flashing by. */
    const val MIN_LANDING_US = 1_000_000L

    /** An overlay moved back to end at the end of the video is the smaller of its own length and this. */
    const val END_LANDING_MAX_US = 3_000_000L
}
