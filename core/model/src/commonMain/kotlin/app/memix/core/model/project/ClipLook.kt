package app.memix.core.model.project

/**
 * The color look of one video clip (P4-07): the six adjustments and one filter, the photo editor's [ImageAdjust] and [FilterRef]
 * unchanged, so a filter id, its LUT and its numbers mean the same in both editors. It is stored in [MediaClip.edit] next to the
 * geometry P4-16 will draw; [MediaClip.look] reads it and [MediaClip.withLook] writes it. Tint and grain are not part of v1.
 */
data class ClipLook(
    val adjust: ImageAdjust = ImageAdjust(),
    val filter: FilterRef? = null,
) {
    /** True when this look changes no pixel: no filter (or one at 0%) and every adjustment at 0. */
    val isNeutral: Boolean get() = adjust == ImageAdjust() && (filter == null || filter.intensity <= 0f)

    companion object {
        val NONE = ClipLook()
    }
}

/** The clip's look; [ClipLook.NONE] when its edit has no color part. Deep-fry is a photo-only tool and is never read for video. */
val MediaClip.look: ClipLook
    get() = edit?.let { ClipLook(it.adjust, it.filter) } ?: ClipLook.NONE

/**
 * This clip with [look] as its look and the rest of its edit (P4-16's geometry) kept. An edit left with nothing in it is dropped, so a
 * clip with no look is exactly as drafts saved before P4-07 were. A filter at 0% stays: its tile is still the chosen one.
 */
fun MediaClip.withLook(look: ClipLook): MediaClip {
    val updated = (edit ?: ImageEdit.NONE).copy(adjust = look.adjust, filter = look.filter)
    return copy(edit = updated.takeUnless { it.isNeutral })
}
