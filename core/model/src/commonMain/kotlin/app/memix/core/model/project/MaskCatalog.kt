package app.memix.core.model.project

/**
 * The fixed rules of shape masks (P4-03b; spec docs/ux/specs/P4-03b-shape-masks-and-blend.md → Mask: how it draws, design/tokens.json →
 * blendMask). Content values, never read through MemixTheme. Part of every saved draft's look: changing a number after Phase 4 ships
 * changes old drafts, so it needs a schema bump (CLAUDE.md rule 6). The Android shader and the CPU mirror (`MaskMath`) read these.
 */
object MaskCatalog {
    /** The `tool_use` id of the spec. */
    const val ID = "mask"

    /** The place a new mask starts at: the middle of the clip's box. */
    const val CENTER = 0.5f

    /** The default size of a Rectangle: 70% of the box's width by 70% of its height. */
    const val RECTANGLE_DEFAULT = 0.7f

    /** The default size of a Circle: 70% of the box's shorter side, so it is round. */
    const val CIRCLE_DEFAULT = 0.7f

    /** The default width of a Mirror band: 40% of the box's shorter side. */
    const val MIRROR_DEFAULT_WIDTH = 0.4f

    /** The smallest and the biggest a mask's width or height gets, as shares of its reference side (spec → Gestures: 5% to 200%). */
    const val SIZE_MIN = 0.05f
    const val SIZE_MAX = 2f
    val SIZE_RANGE: ClosedFloatingPointRange<Float> = SIZE_MIN..SIZE_MAX

    /** One Width, Height or Move tap: 2% of the clip's box (spec → Position tab). */
    const val STEP = 0.02f

    /** Edge softness and Rounded corners move in 10% steps. */
    const val SLIDER_STEP = 0.1f

    /** Edge softness 1 makes the edge this share of the box's shorter side wide (spec → Mask: how it draws). */
    const val FEATHER_SHARE = 0.25f

    /** A sharp edge still fades over this many pixels, so it isn't jagged. */
    const val MIN_EDGE_PX = 1f

    /** A rectangle's corner radius at roundness 1, as a share of its shorter half-side. */
    const val CORNER_RADIUS_SHARE = 1f

    /** On the stage a mask snaps its rotation to quarter turns within this many degrees. */
    const val ROTATION_SNAP_DEGREES = 4f

    fun defaultWidth(shape: MaskShape): Float = when (shape) {
        MaskShape.RECTANGLE -> RECTANGLE_DEFAULT
        MaskShape.CIRCLE -> CIRCLE_DEFAULT
        MaskShape.LINEAR -> 1f
        MaskShape.MIRROR -> MIRROR_DEFAULT_WIDTH
    }

    fun defaultHeight(shape: MaskShape): Float = when (shape) {
        MaskShape.RECTANGLE -> RECTANGLE_DEFAULT
        MaskShape.CIRCLE -> CIRCLE_DEFAULT
        MaskShape.LINEAR, MaskShape.MIRROR -> 1f
    }

    /** A new [shape] mask at its defaults, keeping [softness] and [invert] (spec → Interactions: replacing a mask). */
    fun starting(shape: MaskShape, softness: Float = 0f, invert: Boolean = false): ClipMask =
        ClipMask(shape, softness = softness.coerceIn(0f, 1f), invert = invert)

    /** [mask] with every number held to its range. */
    fun clean(mask: ClipMask): ClipMask = mask.copy(
        centerX = mask.centerX.coerceIn(0f, 1f),
        centerY = mask.centerY.coerceIn(0f, 1f),
        width = mask.width.coerceIn(SIZE_RANGE),
        height = mask.height.coerceIn(SIZE_RANGE),
        rotationDegrees = normalizedDegrees(mask.rotationDegrees),
        softness = mask.softness.coerceIn(0f, 1f),
        cornerRoundness = mask.cornerRoundness.coerceIn(0f, 1f),
    )

    /** Whether [shape] has a Width (Rectangle, Circle, Mirror) and a Height (Rectangle, Circle) to change. */
    fun hasWidth(shape: MaskShape): Boolean = shape != MaskShape.LINEAR
    fun hasHeight(shape: MaskShape): Boolean = shape == MaskShape.RECTANGLE || shape == MaskShape.CIRCLE
}
