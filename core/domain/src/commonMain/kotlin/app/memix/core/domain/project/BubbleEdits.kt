package app.memix.core.domain.project

import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.BoxSize
import app.memix.core.model.project.BubbleBox
import app.memix.core.model.project.BubbleLayer
import app.memix.core.model.project.BubblePoint
import app.memix.core.model.project.BubbleShape
import app.memix.core.model.project.CaptionLook
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.PhotoFormatCatalog
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.Transform
import app.memix.core.model.project.bubbleBodySize
import app.memix.core.model.project.bubbleBox
import app.memix.core.model.project.clampedTail
import app.memix.core.model.project.defaultTail
import app.memix.core.model.project.defaultThoughtTip
import app.memix.core.model.project.emptySlots
import app.memix.core.model.project.SlotKind
import app.memix.core.model.project.tipIn
import app.memix.core.model.project.turnedBounds
import kotlin.math.abs

/*
 * Speech bubbles (spec docs/ux/specs/P2-02-meme-formats.md → Bubbles). A bubble is a layer like text: it moves, scales, turns,
 * fades, locks, hides, reorders, duplicates and deletes through LayerEdits, and its words are edited like text. Locked
 * bubbles refuse like every layer.
 */

/** A bubble's look: the shape, the fill and the words' style, kept for the next bubble of this visit. */
data class BubbleLook(val shape: BubbleShape, val fill: ArgbColor, val style: CaptionStyle) {
    companion object {
        val Default = BubbleLook(BubbleShape.SPEECH, PhotoFormatCatalog.BUBBLE_DEFAULT_FILL, PhotoFormatCatalog.BUBBLE_DEFAULT_STYLE)
    }
}

/**
 * The first landing slot (top, bottom, middle) no visible bubble sits on; the middle when all are taken. When the Bubbles
 * format's slot is empty, it is the same top slot.
 */
fun Project.bubbleLandingCenterY(): Float {
    val taken = layers().filterIsInstance<BubbleLayer>().filter { !it.hidden }.map { it.transform.centerY }
    val slots = PhotoFormatCatalog.BUBBLE_LANDING_SLOTS
    return slots.firstOrNull { slot -> taken.none { abs(it - slot) < PhotoFormatCatalog.SLOT_TAKEN_WITHIN } } ?: slots.last()
}

/**
 * Adds a bubble [text] with [look] as layer [id] at the front of the stack, centered at 100% and 0° on the first free landing
 * slot, with the default tail (toward the canvas center). Blank text adds nothing.
 */
fun Project.addBubbleLayer(text: String, look: BubbleLook, id: String, body: BoxSize? = null): Project {
    if (text.isBlank()) return this
    val centerY = bubbleLandingCenterY()
    // A thought bubble's default tail is longer (its dot chain needs the reach): [body] is its measured body.
    val tip = if (look.shape == BubbleShape.THOUGHT && body != null) defaultThoughtTip(body, 0.5f, centerY, PixelSize(canvas.widthPx, canvas.heightPx)) else defaultTail(centerY)
    val layer = BubbleLayer(id, text, look.style, look.shape, look.fill, tip.x, tip.y, Transform(centerX = 0.5f, centerY = centerY))
    return withLayers(layers() + layer)
}

/** New words for bubble [layerId]; cleared to blank the layer is deleted (as text). Refused while it's locked. */
fun Project.editBubbleText(layerId: String, text: String): LayerEdit {
    val layer = layer(layerId) as? BubbleLayer ?: return LayerEdit.Edited(this)
    if (layer.locked) return LayerEdit.Refused(LayerRefusal.LOCKED)
    if (text.isBlank()) return deleteLayer(layerId)
    if (text == layer.text) return LayerEdit.Edited(this)
    return LayerEdit.Edited(replaceLayer(layerId) { layer.copy(text = text) })
}

/** Bubble [layerId]'s words in [style] (font and color; the outline isn't used). Refused while it's locked. */
fun Project.setBubbleStyle(layerId: String, style: CaptionStyle): LayerEdit =
    editBubble(layerId) { if (it.style == style) it else it.copy(style = style) }

/** Bubble [layerId] in [shape]. Refused while it's locked. */
fun Project.setBubbleShape(layerId: String, shape: BubbleShape): LayerEdit =
    editBubble(layerId) { if (it.shape == shape) it else it.copy(shape = shape) }

/** Bubble [layerId] filled with [fill]. Refused while it's locked. */
fun Project.setBubbleFill(layerId: String, fill: ArgbColor): LayerEdit =
    editBubble(layerId) { if (it.fill == fill) it else it.copy(fill = fill) }

/** Bubble [layerId]'s tail tip at ([tailX], [tailY]) body widths and heights from the center, kept within the limit. Refused while it's locked. */
fun Project.setBubbleTail(layerId: String, tailX: Float, tailY: Float): LayerEdit {
    val tip = clampedTail(tailX, tailY)
    return editBubble(layerId) { if (it.tailX == tip.x && it.tailY == tip.y) it else it.copy(tailX = tip.x, tailY = tip.y) }
}

/** The bubble's tail back to its default for where it sits (a thought bubble's longer one, from its [body]). Refused while it's locked. */
fun Project.resetBubbleTail(layerId: String, body: BoxSize? = null): LayerEdit {
    val layer = layer(layerId) as? BubbleLayer ?: return LayerEdit.Edited(this)
    val size = PixelSize(canvas.widthPx, canvas.heightPx)
    val tip = if (layer.shape == BubbleShape.THOUGHT && body != null) defaultThoughtTip(body, layer.transform.centerX, layer.transform.centerY, size) else defaultTail(layer.transform.centerY)
    return setBubbleTail(layerId, tip.x, tip.y)
}

/** Whether the Bubbles format's slot is empty: no visible bubble sits at its place. */
fun Project.bubbleSlotIsEmpty(): Boolean = photo?.format.emptySlots(PixelSize(canvas.widthPx, canvas.heightPx), layers()).any { it.kind == SlotKind.BUBBLE }

private inline fun Project.editBubble(layerId: String, change: (BubbleLayer) -> BubbleLayer): LayerEdit {
    val layer = layer(layerId) as? BubbleLayer ?: return LayerEdit.Edited(this)
    if (layer.locked) return LayerEdit.Refused(LayerRefusal.LOCKED)
    val changed = change(layer)
    return LayerEdit.Edited(if (changed == layer) this else replaceLayer(layerId) { changed })
}

/** The bubble's body at scale 1 on [canvas]: its words measured by [measure] at the bubble's size and wrap, plus the padding. */
fun BubbleLayer.bodyOn(canvas: PixelSize, measure: CaptionBlockMeasure): BoxSize {
    val block = measure.measure(text, style, canvas.widthPx, canvas.heightPx, CaptionLook.Bubble)
    return bubbleBodySize(block.widthPx, block.heightPx, canvas)
}

/** The bubble's whole drawn box at scale 1 on [canvas], in its own axes from the body's center. */
fun BubbleLayer.boxIn(canvas: PixelSize, measure: CaptionBlockMeasure): BubbleBox {
    val body = bodyOn(canvas, measure)
    return bubbleBox(shape, body, tipIn(body), canvas)
}

/**
 * A box centered on the bubble's body that covers the whole drawn shape, at [scale] and turned: what the ratio-change rule
 * keeps inside the frame (a box off-center from the body would break its "centered" arithmetic).
 */
internal fun BubbleLayer.coveringBoxOn(canvas: PixelSize, scale: Float, measure: CaptionBlockMeasure): BoxSize {
    val box = boxIn(canvas, measure)
    return turnedBounds(BoxSize((box.width + 2f * abs(box.offsetX)) * scale, (box.height + 2f * abs(box.offsetY)) * scale), transform.rotationDegrees)
}

/** A tip point in a bubble's own axes. */
fun BubblePoint.isNear(other: BubblePoint): Boolean = abs(x - other.x) < NEAR && abs(y - other.y) < NEAR

private const val NEAR = 0.0005f
