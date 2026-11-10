package app.memix.core.domain.photo

import app.memix.core.model.project.Layer
import app.memix.core.model.project.TextLayer

/**
 * How a photo scene divides when it becomes a video meme (spec P2-10 → Rules): the [baked] layers are drawn into the one
 * picture, bottom to top, and the [lifted] captions stay editable text on top of it, bottom to top. Hidden layers are in neither:
 * they were never drawn.
 */
data class VideoMemeSplit(val baked: List<Layer>, val lifted: List<TextLayer>)

/**
 * The lifted captions are the contiguous top run of visible, fully opaque text layers. A text with any opacity below 100%, or
 * under another kind of layer (a sticker, a drawing, a bubble), is baked instead, so the first video frame is the photo's.
 * Hidden layers don't break the run: they aren't there.
 */
fun List<Layer>.splitForVideoMeme(): VideoMemeSplit {
    val visible = filterNot { it.hidden }
    val liftedCount = visible.asReversed().takeWhile { it is TextLayer && it.opacity >= FULL_OPACITY }.size
    val bakedCount = visible.size - liftedCount
    return VideoMemeSplit(
        baked = visible.take(bakedCount),
        lifted = visible.drop(bakedCount).filterIsInstance<TextLayer>(),
    )
}

private const val FULL_OPACITY = 1f
