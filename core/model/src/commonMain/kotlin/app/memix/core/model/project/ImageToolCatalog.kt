package app.memix.core.model.project

/**
 * The image tools' fixed content (spec docs/ux/specs/P2-04-image-tools.md; design/tokens.json → photo.imageTools). Like
 * [CaptionCatalog], these numbers and ids are part of every saved draft's look: an id is never renamed, and changing a rule
 * after the closed test needs a schema bump. P4-07 (video filters) and P4-16 (clip crop) reuse the ids and the LUT data.
 */
object ImageToolCatalog {
    /** Straighten's range in degrees, and the step the slider moves in. */
    val STRAIGHTEN_RANGE: ClosedFloatingPointRange<Float> = -45f..45f
    const val STRAIGHTEN_STEP = 0.5f

    /** The crop frame never gets smaller than this share of the picture's width and of its height. */
    const val MIN_CROP = 0.10f

    /** Edges stepper: how much one tap trims, as a share of the picture. */
    const val EDGE_STEP = 0.01f

    /** The colors in a LUT: [LUT_SIZE] cubed, sampled trilinearly, the same data in every engine. */
    const val LUT_SIZE = 33

    /** The 20 filters, in the strip's order after "None". Ids are saved in drafts and never renamed. */
    val filters: List<FilterSpec> = listOf(
        "vivid", "warm", "cool", "mono", "noir", "fade", "vintage", "film", "sepia", "cinema",
        "pastel", "punch", "drama", "sunset", "neon", "aqua", "matte", "chrome", "dusk", "bleach",
    ).map(::FilterSpec)

    /** [id]'s filter, or null when this build doesn't have it (a newer build's draft). */
    fun filterOf(id: String): FilterSpec? = filters.firstOrNull { it.id == id }
}

/** One filter: its [id] (the name resource is `filter_<id>`, the LUT file `filters/<id>.png`). */
data class FilterSpec(val id: String) {
    val lutFileName: String get() = "$id.png"
}
