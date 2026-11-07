package app.memix.core.domain.project

import app.memix.core.model.project.CensorLayer
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.Layer
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.StickerLayer
import app.memix.core.model.project.PanelsFormat
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.pictureArea
import app.memix.core.model.project.placeMainPhoto
import app.memix.core.model.project.PhotoCatalog
import app.memix.core.model.project.Project
import app.memix.core.model.project.Transform
import app.memix.core.model.project.photos
import app.memix.core.model.project.normalizedDegrees
import kotlin.math.min

/*
 * Layer edits of the photo editor (spec docs/ux/specs/P2-01-photo-editor-shell.md → Layers on the canvas, Locked
 * layers). Like every edit, each is a pure function that returns a new Project; a gesture, a tool, a panel and a screen
 * reader's action all reach the same rule here. List order is bottom to top: the last layer is the front one.
 */

/** Why a layer edit didn't apply; the editor explains each one (spec → Locked layers, Layers panel). */
enum class LayerRefusal {
    /** Locked layers can be selected, unlocked, hidden and duplicated, and nothing else. */
    LOCKED,

    /** Reordering needs at least two layers. */
    ONLY_LAYER,

    /** Move up on the front layer. */
    ALREADY_FRONT,

    /** Move down on the back layer. */
    ALREADY_BACK,
}

/** What a layer edit did: the new project, or why it refused. A project equal to the old one means nothing changed. */
sealed interface LayerEdit {
    data class Edited(val project: Project) : LayerEdit

    data class Refused(val reason: LayerRefusal) : LayerEdit
}

/** The photo scene's layers, bottom to top; empty for a video project. */
fun Project.layers(): List<Layer> = photo?.layers.orEmpty()

/** Layer [layerId], or null when there is no such layer. */
fun Project.layer(layerId: String): Layer? = layers().firstOrNull { it.id == layerId }

/**
 * True for a photo project with nothing left to keep, so leaving the editor deletes the draft: no layer, and no photo in a Panels
 * cell either (empty slots and cells are not content; a format alone doesn't make a project, spec P2-02 → How a format works).
 */
fun Project.hasNoLayers(): Boolean {
    val scene = photo ?: return false
    val cellPhotos = (scene.format as? PanelsFormat)?.photos().orEmpty()
    return scene.layers.isEmpty() && cellPhotos.isEmpty()
}

/** Layer [layerId]'s place, size and turn, kept inside the limits ([clampedFor]). Refused while it's locked. */
fun Project.setLayerTransform(layerId: String, transform: Transform): LayerEdit =
    editUnlocked(layerId) { layer -> layer.withState(transform = transform.clampedFor(layer)) }

/** Layer [layerId]'s opacity, within [PhotoCatalog.OPACITY_RANGE]. Refused while it's locked. */
fun Project.setLayerOpacity(layerId: String, opacity: Float): LayerEdit =
    editUnlocked(layerId) { layer -> if (layer is CensorLayer) layer else layer.withState(opacity = opacity.coerceIn(PhotoCatalog.OPACITY_RANGE)) }

/** Locks or unlocks layer [layerId]; always allowed. */
fun Project.setLayerLocked(layerId: String, locked: Boolean): Project =
    replaceLayer(layerId) { it.withState(locked = locked) }

/** Hides or shows layer [layerId]; always allowed, a locked layer included. */
fun Project.setLayerHidden(layerId: String, hidden: Boolean): Project =
    replaceLayer(layerId) { it.withState(hidden = hidden) }

/** Deletes layer [layerId]. Refused while it's locked: a protected layer mustn't disappear (undo exists, but still). */
fun Project.deleteLayer(layerId: String): LayerEdit {
    val layer = layer(layerId) ?: return LayerEdit.Edited(this)
    if (layer.locked) return LayerEdit.Refused(LayerRefusal.LOCKED)
    return LayerEdit.Edited(withLayers(layers().filterNot { it.id == layerId }))
}

/**
 * A copy of layer [layerId] as [newId], directly above the original, [PhotoCatalog.DUPLICATE_OFFSET_OF_SHORT_SIDE] of
 * the canvas's shorter side right and down so the user sees something happened (never past the center limits). The
 * copy is unlocked and keeps the opacity and hidden state; it shares the original's media file. Allowed on a locked layer.
 */
fun Project.duplicateLayer(layerId: String, newId: String): Project {
    val all = layers()
    val index = all.indexOfFirst { it.id == layerId }
    if (index < 0) return this
    val original = all[index]
    val shortSide = min(canvas.widthPx, canvas.heightPx).toFloat()
    val transform = original.transform
    val moved = transform.copy(
        centerX = (transform.centerX + PhotoCatalog.DUPLICATE_OFFSET_OF_SHORT_SIDE * shortSide / canvas.widthPx).coerceIn(0f, 1f),
        centerY = (transform.centerY + PhotoCatalog.DUPLICATE_OFFSET_OF_SHORT_SIDE * shortSide / canvas.heightPx).coerceIn(0f, 1f),
    )
    val copy = original.withState(transform = moved, locked = false).withId(newId)
    return withLayers(all.take(index + 1) + copy + all.drop(index + 1))
}

/**
 * A new image layer [id] of [media] at the front of the stack (spec P2-13 → What lands): centered, scale 1, no turn, unlocked,
 * shown, fully opaque, or placed in the format's picture area when the format has one. Its size at scale 1 is the picture fitted inside the canvas ([fittedImageSize], the same rule as every
 * image layer, computed when it is drawn), so the canvas doesn't change.
 */
fun Project.addImageLayer(media: MediaRef, id: String): Project {
    // Under a format with a picture area (Caption bar, Demotivational) a new photo lands inside it (spec P2-02 → How a format works).
    val size = media.pixelSize
    val area = photo?.format.pictureArea(PixelSize(canvas.widthPx, canvas.heightPx))
    val transform = if (area != null && size != null) placeMainPhoto(photo?.format, PixelSize(canvas.widthPx, canvas.heightPx), size) else Transform()
    return withLayers(layers() + ImageLayer(id, media, transform))
}

/**
 * A new sticker layer [id] of [media] (its copy, with its measured size) at the front of the stack (spec P2-14 → Landing): centered,
 * scale 1 (35% of the shorter side, P2-03's rule), no turn, unlocked, shown, fully opaque.
 */
fun Project.addStickerLayer(media: MediaRef, id: String): Project = withLayers(layers() + StickerLayer(id, media, Transform()))

/** One place toward the front. Refused at the front, with a single layer, or while it's locked. */
fun Project.moveLayerUp(layerId: String): LayerEdit = moveLayerBy(layerId, 1)

/** One place toward the back. Refused at the back, with a single layer, or while it's locked. */
fun Project.moveLayerDown(layerId: String): LayerEdit = moveLayerBy(layerId, -1)

/**
 * Drops layer [layerId] at [indexFromFront] (0 is the front layer, as the Layers panel lists them). Refused while it's
 * locked; dropped where it already is, nothing changes.
 */
fun Project.reorderLayer(layerId: String, indexFromFront: Int): LayerEdit {
    val all = layers()
    val index = all.indexOfFirst { it.id == layerId }
    if (index < 0) return LayerEdit.Edited(this)
    if (all[index].locked) return LayerEdit.Refused(LayerRefusal.LOCKED)
    val targetIndex = (all.size - 1 - indexFromFront).coerceIn(0, all.size - 1)
    if (targetIndex == index) return LayerEdit.Edited(this)
    val without = all.filterIndexed { i, _ -> i != index }
    return LayerEdit.Edited(withLayers(without.take(targetIndex) + all[index] + without.drop(targetIndex)))
}

/** [this] within layer [layer]'s limits: the center on the canvas (0…1), the scale in its kind's range, the turn −180° to 180°. */
fun Transform.clampedFor(layer: Layer): Transform = Transform(
    centerX = centerX.coerceIn(0f, 1f),
    centerY = centerY.coerceIn(0f, 1f),
    scale = scale.coerceIn(PhotoCatalog.scaleRangeOf(layer)),
    rotationDegrees = normalizedDegrees(rotationDegrees),
)

private fun Project.moveLayerBy(layerId: String, places: Int): LayerEdit {
    val all = layers()
    val index = all.indexOfFirst { it.id == layerId }
    if (index < 0) return LayerEdit.Edited(this)
    if (all[index].locked) return LayerEdit.Refused(LayerRefusal.LOCKED)
    if (all.size < 2) return LayerEdit.Refused(LayerRefusal.ONLY_LAYER)
    val target = index + places
    if (target >= all.size) return LayerEdit.Refused(LayerRefusal.ALREADY_FRONT)
    if (target < 0) return LayerEdit.Refused(LayerRefusal.ALREADY_BACK)
    val moved = all.toMutableList().apply { add(target, removeAt(index)) }
    return LayerEdit.Edited(withLayers(moved))
}

internal inline fun Project.editUnlocked(layerId: String, edit: (Layer) -> Layer): LayerEdit {
    val layer = layer(layerId) ?: return LayerEdit.Edited(this)
    if (layer.locked) return LayerEdit.Refused(LayerRefusal.LOCKED)
    return LayerEdit.Edited(replaceLayer(layerId, edit))
}

internal inline fun Project.replaceLayer(layerId: String, change: (Layer) -> Layer): Project {
    val all = layers()
    if (all.none { it.id == layerId }) return this
    return withLayers(all.map { if (it.id == layerId) change(it) else it })
}

internal fun Project.withLayers(layers: List<Layer>): Project {
    val scene = photo ?: return this
    return copy(photo = scene.copy(layers = layers))
}
