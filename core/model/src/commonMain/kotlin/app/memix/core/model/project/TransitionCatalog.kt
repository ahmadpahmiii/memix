package app.memix.core.model.project

/** The five groups of the Transition strip, in the order they show (spec P4-08 → The list). */
enum class TransitionGroup { BLEND, PUSH_AND_SLIDE, WIPE, ZOOM_AND_TURN, GRAPHIC }

/**
 * One transition of the catalog. [defaultDurationUs] is what a tile tap starts with. [flashes] marks the ones the flashing rule (WCAG 2.3.1)
 * watches; their recipes keep to one brightness swell, or at most three bursts a second, whatever the duration.
 */
data class TransitionDef(
    val id: String,
    val group: TransitionGroup,
    val defaultDurationUs: Long,
    val flashes: Boolean = false,
)

/**
 * The 20 transitions (P4-08; spec docs/ux/specs/P4-08-transitions.md → The list, design/tokens.json → transitions). Never read through MemixTheme.
 * Ids are saved forever: never rename or reuse one. The numbers a transition is made of live next to the code that draws it (`TransitionMath`
 * and the shaders, which must match each other); the reference renders in docs/qa/references/P4-08-transitions/ pin them.
 */
object TransitionCatalog {
    const val DEFAULT_DURATION_US = 500_000L
    const val MIN_DURATION_US = 100_000L
    const val MAX_DURATION_US = 2_000_000L

    /** The Duration slider's step, and the keyboard's. */
    const val DURATION_STEP_US = 100_000L

    /** Where the three bursts of Glitch cut sit, as shares of its window; `TransitionMath` draws them and the flash-rate rule counts them. */
    val GLITCH_BURST_CENTERS = floatArrayOf(0.2f, 0.5f, 0.8f)

    /** The one-time play after an edit starts this long before the window and ends this long after it (spec P4-08 → The Transition panel). */
    const val PLAY_LEAD_US = 800_000L
    const val PLAY_TRAIL_US = 400_000L

    private fun def(id: String, group: TransitionGroup, defaultMs: Long = 500, flashes: Boolean = false) =
        TransitionDef(id, group, defaultMs * 1_000, flashes)

    /** All 20, group by group in strip order. */
    val all: List<TransitionDef> = listOf(
        def("dissolve", TransitionGroup.BLEND),
        def("dip_black", TransitionGroup.BLEND, defaultMs = 800),
        def("dip_white", TransitionGroup.BLEND, defaultMs = 800, flashes = true),
        def("blur_through", TransitionGroup.BLEND),
        def("push_left", TransitionGroup.PUSH_AND_SLIDE),
        def("push_right", TransitionGroup.PUSH_AND_SLIDE),
        def("push_up", TransitionGroup.PUSH_AND_SLIDE),
        def("push_down", TransitionGroup.PUSH_AND_SLIDE),
        def("cover", TransitionGroup.PUSH_AND_SLIDE),
        def("uncover", TransitionGroup.PUSH_AND_SLIDE),
        def("wipe_right", TransitionGroup.WIPE),
        def("wipe_down", TransitionGroup.WIPE),
        def("iris", TransitionGroup.WIPE),
        def("clock", TransitionGroup.WIPE),
        def("blinds", TransitionGroup.WIPE),
        def("zoom_through", TransitionGroup.ZOOM_AND_TURN),
        def("twirl", TransitionGroup.ZOOM_AND_TURN),
        def("flip", TransitionGroup.ZOOM_AND_TURN),
        def("glitch_cut", TransitionGroup.GRAPHIC, defaultMs = 400, flashes = true),
        def("pixel_swap", TransitionGroup.GRAPHIC),
    )

    private val byId: Map<String, TransitionDef> = all.associateBy { it.id }

    /** The transition with [id], or null for an id this build doesn't know. */
    fun of(id: String): TransitionDef? = byId[id]

    /** True when [id] is a transition this build draws: a cut with another id is kept in the project and plays plain. */
    fun isBuilt(id: String): Boolean = id in byId

    /** [durationUs] rounded down to a whole [DURATION_STEP_US], at most [MAX_DURATION_US]. */
    fun roundedDown(durationUs: Long): Long = (durationUs.coerceAtMost(MAX_DURATION_US) / DURATION_STEP_US) * DURATION_STEP_US
}
