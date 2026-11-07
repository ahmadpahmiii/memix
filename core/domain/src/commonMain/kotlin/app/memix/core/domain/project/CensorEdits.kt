package app.memix.core.domain.project

import app.memix.core.model.project.CensorCatalog
import app.memix.core.model.project.CensorLayer
import app.memix.core.model.project.CensorMode
import app.memix.core.model.project.CensorShape
import app.memix.core.model.project.Project
import app.memix.core.model.project.Transform

/*
 * Censor layers (spec docs/ux/specs/P2-05-draw-and-censor.md → The censor layer). Like every layer edit they are pure functions;
 * a locked censor refuses (LayerEdits' rule). Moving, turning, hiding, reordering and the rest are LayerEdits'.
 */

/** A new censor [id] at the front of the stack: centered, 0.40 S by 0.25 S, a rectangle, Pixelate, strength 50%. */
fun Project.addCensorLayer(id: String): Project = withLayers(layers() + CensorLayer(id))

fun Project.setCensorMode(layerId: String, mode: CensorMode): LayerEdit = editCensor(layerId) { it.copy(mode = mode) }

fun Project.setCensorShape(layerId: String, shape: CensorShape): LayerEdit = editCensor(layerId) { it.copy(shape = shape) }

/** [strength] 0 to 1; 0 is the safe minimum, never off. */
fun Project.setCensorStrength(layerId: String, strength: Float): LayerEdit = editCensor(layerId) { it.copy(strength = strength.coerceIn(0f, 1f)) }

/** The censor's own size and place in one step (an edge handle's release, or a Width or Height tap); the size stays in [CensorCatalog.SIZE_RANGE]. */
fun Project.setCensorSize(layerId: String, widthS: Float, heightS: Float, transform: Transform? = null): LayerEdit = editCensor(layerId) {
    it.copy(
        widthS = widthS.coerceIn(CensorCatalog.SIZE_RANGE),
        heightS = heightS.coerceIn(CensorCatalog.SIZE_RANGE),
        transform = transform?.let { t -> t.copy(centerX = t.centerX.coerceIn(0f, 1f), centerY = t.centerY.coerceIn(0f, 1f)) } ?: it.transform,
    )
}

private inline fun Project.editCensor(layerId: String, change: (CensorLayer) -> CensorLayer): LayerEdit =
    editUnlocked(layerId) { layer -> if (layer is CensorLayer) change(layer) else layer }
