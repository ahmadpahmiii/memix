package app.memix.core.domain.project

import app.memix.core.model.project.CutOut
import app.memix.core.model.project.CutOutCatalog
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.Project
import app.memix.core.model.project.Stroke
import app.memix.core.model.project.withCutOut

/*
 * Cut-out edits (spec docs/ux/specs/P2-07-background-removal.md → What Apply does to the layer). Pure functions like every edit.
 */

/** The cut-out of image layer [layerId], or null when it isn't an image layer or has none. */
fun Project.cutOutOf(layerId: String): CutOut? = (layer(layerId) as? ImageLayer)?.cutOut

/**
 * Sets image layer [layerId]'s cut-out ([cutOut] null removes it: the layer gets its whole picture back). Refused while the
 * layer is locked; anything but an image layer is left as it is. The layer's transform is untouched: the transparent parts
 * still count as its box. Strokes beyond [CutOutCatalog.MAX_POINTS] are cut off at the stroke that crosses the cap, so a
 * caller that forgot to cap can't store an unbounded list.
 */
fun Project.setCutOut(layerId: String, cutOut: CutOut?): LayerEdit {
    val old = layer(layerId) as? ImageLayer ?: return LayerEdit.Edited(this)
    if (old.locked) return LayerEdit.Refused(LayerRefusal.LOCKED)
    val capped = cutOut?.copy(strokes = cutOut.strokes.cappedAt(CutOutCatalog.MAX_POINTS))
    return LayerEdit.Edited(replaceLayer(layerId) { old.withCutOut(capped) })
}

private fun List<Stroke>.cappedAt(maxPoints: Int): List<Stroke> {
    var total = 0
    return takeWhile { stroke -> (total + stroke.pointCount <= maxPoints).also { if (it) total += stroke.pointCount } }
}
