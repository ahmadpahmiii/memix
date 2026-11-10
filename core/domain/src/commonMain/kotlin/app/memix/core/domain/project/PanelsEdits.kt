package app.memix.core.domain.project

import app.memix.core.model.project.PanelFamily
import app.memix.core.model.project.PanelLayouts
import app.memix.core.model.project.PanelsFormat
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.photos
import app.memix.core.model.project.panelsFormatOf
import app.memix.core.model.project.spec
import app.memix.core.model.project.withDivider
import app.memix.core.model.project.withEvenDividers

/*
 * The Panels format's own edits (spec docs/ux/specs/P2-02-meme-formats.md → Panels). The layout and divider choices are one
 * step each; a cell's photo and view are in PhotoFormatEdits.kt. Every edit is on the scene's current [PanelsFormat]; on any
 * other format, nothing changes.
 */

private fun Project.panels(): PanelsFormat? = photo?.format as? PanelsFormat

/**
 * Panels in [count] cells (2 to 6) of [family], else side by side when the count offers no such family: each photo keeps its
 * number, a lower count drops the highest-numbered cells' photos ([FormatChange.droppedPhotos]), the dividers start even.
 */
fun Project.setPanelsLayout(count: Int, family: PanelFamily): FormatChange {
    val scene = photo ?: return FormatChange(this, droppedPhotos = false)
    val panels = panels() ?: return FormatChange(this, droppedPhotos = false)
    val target = PanelLayouts.of(count.coerceIn(2, 6), family)
    if (panels.layout == target.id) return FormatChange(this, droppedPhotos = false)
    val dropped = panels.cells.drop(target.cellCount).any { it.source != null }
    return FormatChange(copy(photo = scene.copy(format = panelsFormatOf(target, panels.cells))), dropped)
}

/** Every divider at its even position. */
fun Project.makePanelsEven(): Project {
    val scene = photo ?: return this
    val panels = panels() ?: return this
    val even = panels.withEvenDividers()
    return if (even == panels) this else copy(photo = scene.copy(format = even))
}

/** Divider [index] at [position] (a share of its split's extent), kept within its limits. */
fun Project.setPanelsDivider(index: Int, position: Float): Project {
    val scene = photo ?: return this
    val panels = panels() ?: return this
    val moved = panels.withDivider(PixelSize(canvas.widthPx, canvas.heightPx), index, position)
    return if (moved == panels) this else copy(photo = scene.copy(format = moved))
}

/** The family of the current layout, so a count tap keeps it. */
fun PanelsFormat.family(): PanelFamily = spec()?.family ?: PanelFamily.COLUMNS

/** Whether any photo sits in a cell. */
fun PanelsFormat.hasPhotos(): Boolean = photos().isNotEmpty()
