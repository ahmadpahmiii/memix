package app.memix.core.model.project

/**
 * The fixed rules of keyframes (P4-02; spec docs/ux/specs/P4-02b-keyframes.md, design/tokens.json → keyframes). Never read through
 * MemixTheme. Part of every saved draft's look, like [OverlayCatalog]: changing a number after Phase 4 ships changes old drafts, so
 * it needs a schema bump (CLAUDE.md rule 6).
 */
object KeyframeCatalog {
    /** An item can hold this many keyframes; at the limit a new one is refused and an existing one can still be updated. */
    const val MAX_PER_ITEM = 50

    /** Two keyframes are at least one frame at 30 fps apart. */
    const val MIN_SPACING_US = 33_333L

    /** A playhead within this of a keyframe is "on" it: half a frame at 30 fps. */
    const val ON_KEYFRAME_US = 16_667L

    /** A first edit this close to the item's start (or closer) makes one keyframe, not a start keyframe plus one (spec → Auto-keying). */
    const val AUTO_START_WITHIN_US = 100_000L

    val DEFAULT_EASING = Easing.SMOOTH

    /** A keyed item's center may leave the frame by half a frame on each side, so it can slide in from outside. */
    val CENTER_RANGE: ClosedFloatingPointRange<Float> = -0.5f..1.5f

    /** A keyed item can fade to nothing (a still overlay stops at [OverlayCatalog.OPACITY_RANGE]'s start). */
    val OPACITY_RANGE: ClosedFloatingPointRange<Float> = 0f..1f

    /** The same ceiling as the Volume panel (`MAX_VOLUME` in the domain's SoundEdits): 200%. */
    val VOLUME_RANGE: ClosedFloatingPointRange<Float> = 0f..2f

    /** Ten full spins either way; the angle is never wrapped, so a runaway twist needs a bound. */
    const val ROTATION_LIMIT_DEGREES = 3_600f

    /**
     * The cubic Bézier of [easing] as (x1, y1, x2, y2) with the end points fixed at (0, 0) and (1, 1), the CSS easing functions
     * (MDN `<easing-function>`); null for [Easing.LINEAR] and [Easing.HOLD], which aren't curves. The array is shared: don't change it.
     */
    fun controlPoints(easing: Easing): FloatArray? = when (easing) {
        Easing.SMOOTH -> SMOOTH_POINTS
        Easing.EASE_IN -> EASE_IN_POINTS
        Easing.EASE_OUT -> EASE_OUT_POINTS
        Easing.LINEAR, Easing.HOLD -> null
    }

    // Shared, never changed: the audio thread asks for them once per sample.
    private val SMOOTH_POINTS = floatArrayOf(0.42f, 0f, 0.58f, 1f)
    private val EASE_IN_POINTS = floatArrayOf(0.42f, 0f, 1f, 1f)
    private val EASE_OUT_POINTS = floatArrayOf(0f, 0f, 0.58f, 1f)

    /** The scale range a keyed item's look stays in; the same as its static one. */
    fun scaleRangeOf(item: TimelineItem): ClosedFloatingPointRange<Float> = when (item) {
        is MediaClip -> OverlayCatalog.SCALE_RANGE
        is TextItem -> CaptionCatalog.MIN_SCALE..CaptionCatalog.MAX_SCALE
        is StickerItem -> PhotoCatalog.STICKER_SCALE_RANGE
        is AudioClip, is EffectItem -> 1f..1f
    }
}
