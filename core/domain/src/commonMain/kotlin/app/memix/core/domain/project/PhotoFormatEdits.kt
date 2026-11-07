package app.memix.core.domain.project

import app.memix.core.model.project.shownSize
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.Layer
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.PanelCell
import app.memix.core.model.project.PanelsFormat
import app.memix.core.model.project.PhotoFormat
import app.memix.core.model.project.PanelLayouts
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.Transform
import app.memix.core.model.project.isAsPlaced
import app.memix.core.model.project.mainPhoto
import app.memix.core.model.project.panelsFormatOf
import app.memix.core.model.project.placeMainPhoto

/*
 * Choosing a meme format (spec docs/ux/specs/P2-02-meme-formats.md → How a format works, rule 3). One choice is one undo step
 * and never deletes or moves the user's text, stickers, bubbles or extra photos: only the main photo (the lowest image
 * layer) is placed in the format's picture area, or moved into cell 1 of Panels, and only while it is where the format put it.
 */

/** What [setPhotoFormat] did: the new project, and whether photos in removed Panels cells were dropped from the meme (the toast says so). */
data class FormatChange(val project: Project, val droppedPhotos: Boolean)

/**
 * The project in [target] (null is plain), leaving the current format first:
 * - from Panels, cell 1's photo comes back as the main photo (layer [restoredPhotoId], at the back of the stack, fitted inside the
 *   canvas at scale 1), and the photos of cells 2 to 6 leave the meme;
 * - a main photo that sits where Caption bar or Demotivational put it goes back to filling the canvas;
 * - into a picture-area format, a main photo that sits where the previous format put it is placed in the picture area;
 * - into Panels, the main photo moves into cell 1 (its layer is removed, its file is kept in the cell).
 * A locked main photo is never moved or made a cell. Choosing the current format changes nothing.
 */
fun Project.setPhotoFormat(target: PhotoFormat?, restoredPhotoId: String): FormatChange {
    val scene = photo ?: return FormatChange(this, droppedPhotos = false)
    val current = scene.format
    if (current == target || (current is PanelsFormat && target is PanelsFormat)) return FormatChange(this, droppedPhotos = false)
    val canvasSize = PixelSize(canvas.widthPx, canvas.heightPx)
    var layers = scene.layers
    var dropped = false
    // The format the main photo is judged against when it leaves: Panels leaves it plain.
    var leaving: PhotoFormat? = current
    if (current is PanelsFormat) {
        val first = current.cells.firstOrNull()?.source
        dropped = current.cells.drop(1).any { it.source != null }
        if (first != null) layers = listOf<Layer>(ImageLayer(restoredPhotoId, first, Transform())) + layers
        leaving = null
    }
    val main = layers.mainPhoto()
    val movable = main != null && !main.locked
    val wasPlaced = movable && main != null && (current is PanelsFormat || isAsPlaced(main, leaving, canvasSize))
    var format = target
    if (target is PanelsFormat) {
        val photoCell = if (movable && main != null) main.source else null
        if (photoCell != null) layers = layers.filterNot { it.id == main?.id }
        format = panelsFormatOf(PanelLayouts.default, listOf(PanelCell(source = photoCell)))
    } else if (wasPlaced && main != null) {
        val size = main.source.pixelSize?.let { main.shownSize(it) }
        val placed = if (size != null) placeMainPhoto(target, canvasSize, size) else Transform()
        layers = layers.map { if (it.id == main.id) main.copy(transform = placed) else it }
    }
    return FormatChange(copy(photo = scene.copy(layers = layers, format = format)), dropped)
}

/** [media] as the photo of cell [cellIndex] (a pick or Replace), centered at zoom 100%. */
fun Project.setCellPhoto(cellIndex: Int, media: MediaRef): Project = editCell(cellIndex) { PanelCell(source = media) }

/** Cell [cellIndex] emptied (Remove). */
fun Project.removeCellPhoto(cellIndex: Int): Project = editCell(cellIndex) { PanelCell() }

/** Cell [cellIndex]'s view of its photo: its zoom and the point of the picture at its center; the caller has kept them covering. */
fun Project.setCellView(cellIndex: Int, zoom: Float, centerX: Float, centerY: Float): Project =
    editCell(cellIndex) { it.copy(zoom = zoom, centerX = centerX, centerY = centerY) }

private inline fun Project.editCell(cellIndex: Int, change: (PanelCell) -> PanelCell): Project {
    val scene = photo ?: return this
    val panels = scene.format as? PanelsFormat ?: return this
    val cell = panels.cells.getOrNull(cellIndex) ?: return this
    val changed = change(cell)
    if (changed == cell) return this
    return copy(photo = scene.copy(format = panels.copy(cells = panels.cells.mapIndexed { index, existing -> if (index == cellIndex) changed else existing })))
}
