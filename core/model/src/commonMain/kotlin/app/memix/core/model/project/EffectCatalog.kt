package app.memix.core.model.project

/** The six tabs of the Effects sheet, in the order they show (spec docs/ux/specs/P4-06-video-effects.md → The Effects sheet). */
enum class EffectTab { MOTION, GLITCH, LIGHT, COLOR, BLUR, RETRO }

/** The four fixed shapes of an effect's strength over its item's own span (spec P4-06 → Envelope). */
enum class EffectEnvelope {
    /** Ramps in and out over min(80 ms, a quarter of the length): no pop at the item's ends. */
    STEADY,

    /** Attack to full in 80 ms, holds to 70% of the length, then eases back to 0 by the end. */
    PUNCH,

    /** Full at the start, then exponential decay to about 2% at the end. */
    DECAY,

    /** 0 to full by the end, then a jump back at the item's end: what a push-in cut wants. */
    RAMP,
}

/**
 * One effect of the catalog: the id saved in every project, where the sheet lists it, its envelope, and what a new item starts with.
 * [isBuilt] is false for the effects of P4-06b: their ids are saved from now on, but no engine draws them and the sheet doesn't list them yet.
 */
data class EffectDef(
    val id: String,
    val tab: EffectTab,
    val envelope: EffectEnvelope,
    val defaultDurationUs: Long,
    val defaultIntensity: Float,
    val isBuilt: Boolean,
    /** The instant into an item at which the effect shows best: the reference renders and the sheet's tile pictures are taken there (spec P4-06 → The list, "Ref time"). */
    val refTimeUs: Long = 0,
)

/**
 * The 30 video effects (P4-06; spec docs/ux/specs/P4-06-video-effects.md → The list, design/tokens.json → effects). Never read through
 * MemixTheme. Ids are saved forever: never rename or reuse one. The numbers a look is made of live next to the code that draws it
 * (`EffectMath` and the shaders, which every build of one effect must match); the reference renders in docs/qa/references/P4-06-effects/ pin them.
 */
object EffectCatalog {
    /** At most this many items may overlap at any moment: a fourth is refused (the 30 fps budget; spec P4-06 → Stacking). */
    const val MAX_OVERLAP = 3

    /** Steady's ramp length, and Punch's attack. */
    const val ATTACK_US = 80_000L

    /** Punch holds full strength to this share of the length. */
    const val PUNCH_HOLD_SHARE = 0.7f

    /** Decay's exponent: strength is exp(-DECAY_RATE * elapsed / length), about 2% at the end. */
    const val DECAY_RATE = 4f

    /** Intensity changes in steps of 10% from the keyboard and screen readers. */
    const val INTENSITY_STEP = 0.1f

    /** At most this many Flash items may start within any one second (photosensitivity, WCAG 2.3.1); a fourth is refused. */
    const val MAX_FLASHES_PER_SECOND = 3

    const val FLASH_ID = "flash"

    /** A playhead with less than this left in the video lands the item ending at the end of the video instead. */
    const val MIN_REMAINING_FOR_PLAYHEAD_LANDING_US = 500_000L

    private fun def(id: String, tab: EffectTab, envelope: EffectEnvelope, lengthMs: Long, intensity: Float, isBuilt: Boolean, refMs: Long = 0) =
        EffectDef(id, tab, envelope, lengthMs * 1_000, intensity, isBuilt, refMs * 1_000)

    /** All 30, tab by tab in sheet order. */
    val all: List<EffectDef> = listOf(
        def("zoom_punch", EffectTab.MOTION, EffectEnvelope.PUNCH, 600, 0.7f, true, refMs = 80),
        def("slow_push", EffectTab.MOTION, EffectEnvelope.RAMP, 3000, 0.6f, true, refMs = 3000),
        def("shake", EffectTab.MOTION, EffectEnvelope.STEADY, 1000, 0.6f, true, refMs = 120),
        def("wobble", EffectTab.MOTION, EffectEnvelope.STEADY, 2000, 0.6f, true, refMs = 208),
        def("bounce", EffectTab.MOTION, EffectEnvelope.STEADY, 2000, 0.6f, true, refMs = 125),
        def("glitch", EffectTab.GLITCH, EffectEnvelope.STEADY, 1000, 0.7f, false),
        def("rgb_split", EffectTab.GLITCH, EffectEnvelope.STEADY, 1500, 0.6f, false),
        def("tear", EffectTab.GLITCH, EffectEnvelope.STEADY, 2000, 0.6f, false),
        def("pixelate", EffectTab.GLITCH, EffectEnvelope.STEADY, 1000, 0.5f, false),
        def("static", EffectTab.GLITCH, EffectEnvelope.STEADY, 1500, 0.5f, false),
        def("flash", EffectTab.LIGHT, EffectEnvelope.DECAY, 250, 0.8f, true),
        def("light_pulse", EffectTab.LIGHT, EffectEnvelope.STEADY, 2000, 0.5f, true, refMs = 250),
        def("glow", EffectTab.LIGHT, EffectEnvelope.STEADY, 2000, 0.6f, true),
        def("spotlight", EffectTab.LIGHT, EffectEnvelope.STEADY, 2000, 0.7f, true),
        def("sun_leak", EffectTab.LIGHT, EffectEnvelope.STEADY, 2000, 0.6f, true, refMs = 1667),
        def("deep_fry", EffectTab.COLOR, EffectEnvelope.STEADY, 2000, 0.7f, true),
        def("mono", EffectTab.COLOR, EffectEnvelope.STEADY, 2000, 1f, true),
        def("negative", EffectTab.COLOR, EffectEnvelope.STEADY, 1000, 1f, true),
        def("duotone", EffectTab.COLOR, EffectEnvelope.STEADY, 2000, 0.7f, true),
        def("poster", EffectTab.COLOR, EffectEnvelope.STEADY, 2000, 0.6f, true),
        def("blur", EffectTab.BLUR, EffectEnvelope.STEADY, 2000, 0.6f, false),
        def("radial_blur", EffectTab.BLUR, EffectEnvelope.STEADY, 1000, 0.7f, false),
        def("smear", EffectTab.BLUR, EffectEnvelope.STEADY, 1000, 0.6f, false),
        def("tilt_blur", EffectTab.BLUR, EffectEnvelope.STEADY, 2000, 0.7f, false),
        def("soft_focus", EffectTab.BLUR, EffectEnvelope.STEADY, 2000, 0.6f, false),
        def("vhs", EffectTab.RETRO, EffectEnvelope.STEADY, 3000, 0.7f, false),
        def("old_screen", EffectTab.RETRO, EffectEnvelope.STEADY, 2000, 0.6f, false),
        def("film_grain", EffectTab.RETRO, EffectEnvelope.STEADY, 3000, 0.5f, false),
        def("fisheye", EffectTab.RETRO, EffectEnvelope.STEADY, 2000, 0.6f, false),
        def("mirror", EffectTab.RETRO, EffectEnvelope.STEADY, 2000, 1f, false),
    )

    private val byId: Map<String, EffectDef> = all.associateBy { it.id }

    /** The effect with [id], or null for an id this build doesn't know. */
    fun of(id: String): EffectDef? = byId[id]

    /** True when [id] is an effect this build draws: items of other ids are kept in the project and skipped. */
    fun isBuilt(id: String): Boolean = byId[id]?.isBuilt == true

    /** The effects the sheet lists under [tab]: the built ones. */
    fun builtIn(tab: EffectTab): List<EffectDef> = all.filter { it.tab == tab && it.isBuilt }

    /** The tabs that have a built effect, in sheet order: a tab sits one place to the left until the tab before it is built. */
    val shownTabs: List<EffectTab> = EffectTab.entries.filter { tab -> all.any { it.tab == tab && it.isBuilt } }
}
