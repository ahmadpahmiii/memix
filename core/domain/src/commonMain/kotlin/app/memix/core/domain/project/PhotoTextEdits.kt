package app.memix.core.domain.project

import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.PhotoCatalog
import app.memix.core.model.project.Project
import app.memix.core.model.project.TextLayer
import app.memix.core.model.project.Transform
import kotlin.math.abs

/*
 * Text layers of the photo editor (spec docs/ux/specs/P2-03-photo-text-and-stickers.md → Text on the canvas). A caption's
 * look is the video editor's (P1-10): the same CaptionStyle, the same renderer, the same size ladder. What differs is that
 * a text is a layer in the stack, not an item on a timeline. Locked layers refuse like every layer (LayerEdits).
 */

/** The first landing slot (top, bottom, middle) that no visible text layer sits on; the middle when all are taken. */
fun Project.photoTextLandingCenterY(): Float {
    val taken = layers().filterIsInstance<TextLayer>().filter { !it.hidden }.map { it.transform.centerY }
    val slots = PhotoCatalog.TEXT_LANDING_SLOTS
    return slots.firstOrNull { slot -> taken.none { abs(it - slot) < PhotoCatalog.TEXT_SLOT_TAKEN_WITHIN } } ?: slots.last()
}

/**
 * Adds [text] in [style] as layer [id] at the front of the stack, centered horizontally at 100% and 0°, at the first free
 * landing slot ([photoTextLandingCenterY]). Blank text adds nothing.
 */
fun Project.addTextLayer(text: String, style: CaptionStyle, id: String): Project {
    if (text.isBlank()) return this
    val layer = TextLayer(id, text, style, Transform(centerX = 0.5f, centerY = photoTextLandingCenterY()))
    return withLayers(layers() + layer)
}

/**
 * New words for text layer [layerId]; cleared to blank the layer is deleted (spec → Typing → commit, as P1-10). Refused
 * while it's locked.
 */
fun Project.editTextLayer(layerId: String, text: String): LayerEdit {
    val layer = layer(layerId) as? TextLayer ?: return LayerEdit.Edited(this)
    if (layer.locked) return LayerEdit.Refused(LayerRefusal.LOCKED)
    if (text.isBlank()) return deleteLayer(layerId)
    if (text == layer.text) return LayerEdit.Edited(this)
    return LayerEdit.Edited(replaceLayer(layerId) { layer.copy(text = text) })
}

/** Text layer [layerId] in [style]. Refused while it's locked. */
fun Project.setTextLayerStyle(layerId: String, style: CaptionStyle): LayerEdit {
    val layer = layer(layerId) as? TextLayer ?: return LayerEdit.Edited(this)
    if (layer.locked) return LayerEdit.Refused(LayerRefusal.LOCKED)
    if (layer.style == style) return LayerEdit.Edited(this)
    return LayerEdit.Edited(replaceLayer(layerId) { layer.copy(style = style) })
}
