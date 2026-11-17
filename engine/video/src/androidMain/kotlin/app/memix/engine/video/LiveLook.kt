package app.memix.engine.video

import app.memix.core.model.project.ClipLook

/** The two shader passes of a clip's look (P4-07): per-pixel color (adjustments, filter) and the neighbourhood pass (sharpen, vignette). */
internal enum class LookPass { COLOR, DETAIL }

/** Which passes a look needs; a neutral look needs none, so a clip with no look costs nothing. */
internal fun ClipLook.passes(): Set<LookPass> {
    if (isNeutral) return emptySet()
    val filterShown = (filter?.intensity ?: 0f) > 0f
    val color = adjust.brightness != 0f || adjust.contrast != 0f || adjust.saturation != 0f || adjust.warmth != 0f || filterShown
    val detail = adjust.sharpen != 0f || adjust.vignette != 0f
    return buildSet {
        if (color) add(LookPass.COLOR)
        if (detail) add(LookPass.DETAIL)
    }
}

/**
 * A clip's look as the shaders read it every frame. The preview changes [look] while an Intensity or Adjust slider drags, so the picture
 * follows without rebuilding the composition (spec P4-07 → The panel: playback keeps running, a slider drag is live); an export never
 * changes it. The composition was built with [passes] and with the LUT of [filterId] only: a look that needs another pass or another
 * filter needs a new composition ([covers]). The GL thread reads, the main thread writes: one reference, replaced whole.
 */
internal class LiveLook(initial: ClipLook, val passes: Set<LookPass> = initial.passes(), val filterId: String? = initial.filter?.id) {
    @Volatile
    var look: ClipLook = initial

    /** True when this composition's shaders can draw [wanted] without being rebuilt. */
    fun covers(wanted: ClipLook): Boolean = wanted.passes().all { it in passes } && (wanted.filter?.id ?: filterId) == filterId
}
