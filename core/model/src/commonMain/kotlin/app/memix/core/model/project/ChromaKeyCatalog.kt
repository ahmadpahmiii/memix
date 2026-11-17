package app.memix.core.model.project

/**
 * The fixed rules of green screen keying (P4-04; spec docs/ux/specs/P4-04-green-screen.md → How it draws, design/tokens.json →
 * chromaKey). Never read through MemixTheme: these are content values. Part of every saved draft's look, like [OverlayCatalog]:
 * changing a number after Phase 4 ships changes old drafts, so it needs a schema bump (CLAUDE.md rule 6). The Android shader and the
 * CPU mirror (`ChromaKeyMath` in :core:domain) both read these numbers; the reference renders pin them.
 */
object ChromaKeyCatalog {
    /** The `tool_use` id of the spec (the effect's id in the declarative sense). */
    const val ID = "chroma_key"

    /** The presets of the Color to remove row: a studio green and a studio blue, as opaque 0xAARRGGBB. */
    const val GREEN_ARGB = 0xFF00B140.toInt()
    const val BLUE_ARGB = 0xFF0047BB.toInt()

    const val DEFAULT_STRENGTH = 0.5f
    const val DEFAULT_SOFTNESS = 0.25f

    /** Strength and Edge softness move in 10% steps (keyboard, TalkBack and the slider's rail). */
    const val SLIDER_STEP = 0.1f

    /** The default key: Green, 50% strength, 25% edge softness. */
    val DEFAULT = ChromaKey(GREEN_ARGB, DEFAULT_STRENGTH, DEFAULT_SOFTNESS)

    /** Tolerance t = [TOLERANCE_BASE] + [TOLERANCE_PER_STRENGTH] x strength: the chroma distance that is removed entirely. */
    const val TOLERANCE_BASE = 0.05f
    const val TOLERANCE_PER_STRENGTH = 0.20f

    /** Feather f = [FEATHER_BASE] + [FEATHER_PER_SOFTNESS] x softness: the further chroma distance over which alpha rises from 0 to 1. */
    const val FEATHER_BASE = 0.02f
    const val FEATHER_PER_SOFTNESS = 0.08f

    /**
     * Tolerance plus feather never reaches past this chroma distance (white is 0.328 from the green key), so the subject stays opaque
     * at 100% strength and 100% softness; the feather shrinks to fit, but never below [FEATHER_BASE].
     */
    const val MAX_REACH = 0.30f

    /** Spill removal: the chroma along the key's direction is reduced by this share x (1 - alpha), where the pixel isn't fully removed. */
    const val SPILL_REDUCTION = 0.6f

    /**
     * Below this alpha a pixel's color is neutralized fully along the key's direction (rising to [SPILL_REDUCTION] at it), so the
     * green of removed pixels never bleeds into an edge when the clip is scaled. Not in the spec's table: it only changes pixels that
     * are nearly invisible (Media3 scales and blends straight, not premultiplied, alpha).
     */
    const val BLEED_GUARD_ALPHA = 0.25f

    /** The Pick takes the average of a square of this many source pixels per side around the touch. */
    const val SAMPLE_WINDOW_PX = 9

    /** "Nothing found" shows when less than this share of the visible frame is keyed (alpha under 0.5). */
    const val NOTHING_FOUND_SHARE = 0.01f

    /** The tolerance (chroma distance removed entirely) of [strength], 0 to 1. */
    fun toleranceOf(strength: Float): Float = TOLERANCE_BASE + TOLERANCE_PER_STRENGTH * strength.coerceIn(0f, 1f)

    /** The feather width of [softness] at [strength], both 0 to 1: `f = 0.02 + 0.08 x softness`, cut to [MAX_REACH] minus the tolerance. */
    fun featherOf(strength: Float, softness: Float): Float {
        val wanted = FEATHER_BASE + FEATHER_PER_SOFTNESS * softness.coerceIn(0f, 1f)
        return minOf(wanted, maxOf(FEATHER_BASE, MAX_REACH - toleranceOf(strength)))
    }

    /** A key of [colorArgb] (made opaque) at [strength] and [softness], both held to 0..1 and to the 10% grid's range. */
    fun keyOf(colorArgb: Int, strength: Float, softness: Float): ChromaKey =
        ChromaKey(colorArgb or OPAQUE, strength.coerceIn(0f, 1f), softness.coerceIn(0f, 1f))

    private const val OPAQUE = 0xFF000000.toInt()
}
