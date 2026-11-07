package app.memix.core.domain.project

import app.memix.core.domain.EditorTool
import app.memix.core.model.project.CropRect
import app.memix.core.model.project.ImageAdjust
import app.memix.core.model.project.ImageEdit
import app.memix.core.model.project.flippedAcross
import app.memix.core.model.project.flippedDown
import app.memix.core.model.project.turnedRight
import kotlin.math.abs
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.isAsPlaced
import app.memix.core.model.project.mainPhoto
import app.memix.core.model.project.placeMainPhoto
import app.memix.core.model.project.shownSize
import app.memix.core.model.project.withEdit

/*
 * Image tool edits (spec docs/ux/specs/P2-04-image-tools.md → What a crop does to the layer). Pure functions like every edit.
 */

/** The edit of image layer [layerId], or null when it isn't an image layer or has none. */
fun Project.imageEditOf(layerId: String): ImageEdit? = (layer(layerId) as? ImageLayer)?.edit

/**
 * Replaces image layer [layerId]'s edit with [edit] (null or a neutral edit removes it). Refused while it's locked; anything
 * but an image layer is left as it is. The layer keeps its transform; its size is recomputed from the new picture shape
 * wherever it is used. The main photo under a format with a picture area, if it was as placed before the edit, is placed again
 * for its new shape (spec P2-04 → What a crop does; P2-02's as-placed rule), and a photo the user had moved stays.
 */
fun Project.setImageEdit(layerId: String, edit: ImageEdit?): LayerEdit {
    val old = layer(layerId) as? ImageLayer ?: return LayerEdit.Edited(this)
    if (old.locked) return LayerEdit.Refused(LayerRefusal.LOCKED)
    val updated = old.withEdit(edit)
    if (updated == old) return LayerEdit.Edited(this)
    val canvasSize = PixelSize(canvas.widthPx, canvas.heightPx)
    val format = photo?.format
    val replacePlacement = layers().mainPhoto()?.id == layerId && old.source.pixelSize != null && isAsPlaced(old, format, canvasSize)
    val placed = if (replacePlacement) {
        val size = old.source.pixelSize?.let(updated::shownSize)
        if (size != null) updated.copy(transform = placeMainPhoto(format, canvasSize, size)) else updated
    } else {
        updated
    }
    return LayerEdit.Edited(replaceLayer(layerId) { placed })
}

/**
 * The `tool_use` events for a crop screen's Done: one for each geometry part that changed from [before] to [after]
 * (spec P2-04 → Analytics): `crop` the frame, `rotate_image` a quarter turn, `flip_image` a mirror, `straighten` the angle.
 * A turn or a mirror carries the frame and the angle with the picture, so [before] is first mapped through them: only what
 * differs from that counts (turning a cropped picture is `rotate_image`, not also `crop`).
 */
fun changedGeometryTools(before: ImageEdit, after: ImageEdit): List<EditorTool> {
    val turns = (after.quarterTurns - before.quarterTurns + QUARTER_TURNS) % QUARTER_TURNS
    var carried = before
    repeat(turns) { carried = carried.turnedRight() }
    val flipped = after.flipH != carried.flipH || after.flipV != carried.flipV
    if (after.flipH != carried.flipH) carried = carried.flippedAcross()
    if (after.flipV != carried.flipV) carried = carried.flippedDown()
    return buildList {
        if (!carried.crop.isCloseTo(after.crop)) add(EditorTool.CROP)
        if (turns != 0) add(EditorTool.ROTATE_IMAGE)
        if (flipped) add(EditorTool.FLIP_IMAGE)
        if (abs(carried.straighten - after.straighten) > TOLERANCE) add(EditorTool.STRAIGHTEN)
    }
}

private fun CropRect.isCloseTo(other: CropRect) =
    abs(left - other.left) <= TOLERANCE && abs(top - other.top) <= TOLERANCE && abs(right - other.right) <= TOLERANCE && abs(bottom - other.bottom) <= TOLERANCE

private const val QUARTER_TURNS = 4
private const val TOLERANCE = 1e-3f

/** The Edit photo panel's tabs (spec P2-04 → The Edit photo panel); Reset acts on the open one. */
enum class EditPhotoTab { FILTERS, ADJUST, DEEP_FRY }

/** The six adjustments: the event each logs and the range of its fraction. Brightness, contrast, saturation and warmth run -1 to 1, sharpen and vignette 0 to 1. */
enum class AdjustPart(val tool: EditorTool, val range: ClosedFloatingPointRange<Float>) {
    BRIGHTNESS(EditorTool.ADJUST_BRIGHTNESS, -1f..1f),
    CONTRAST(EditorTool.ADJUST_CONTRAST, -1f..1f),
    SATURATION(EditorTool.ADJUST_SATURATION, -1f..1f),
    WARMTH(EditorTool.ADJUST_WARMTH, -1f..1f),
    SHARPEN(EditorTool.ADJUST_SHARPEN, 0f..1f),
    VIGNETTE(EditorTool.ADJUST_VIGNETTE, 0f..1f),
}

fun ImageAdjust.valueOf(part: AdjustPart): Float = when (part) {
    AdjustPart.BRIGHTNESS -> brightness
    AdjustPart.CONTRAST -> contrast
    AdjustPart.SATURATION -> saturation
    AdjustPart.WARMTH -> warmth
    AdjustPart.SHARPEN -> sharpen
    AdjustPart.VIGNETTE -> vignette
}

fun ImageAdjust.with(part: AdjustPart, value: Float): ImageAdjust {
    val v = value.coerceIn(part.range)
    return when (part) {
        AdjustPart.BRIGHTNESS -> copy(brightness = v)
        AdjustPart.CONTRAST -> copy(contrast = v)
        AdjustPart.SATURATION -> copy(saturation = v)
        AdjustPart.WARMTH -> copy(warmth = v)
        AdjustPart.SHARPEN -> copy(sharpen = v)
        AdjustPart.VIGNETTE -> copy(vignette = v)
    }
}

/** [this] edit with [tab]'s part put back to normal: no filter, all six adjustments 0, or no deep-fry. */
fun ImageEdit.resetTab(tab: EditPhotoTab): ImageEdit = when (tab) {
    EditPhotoTab.FILTERS -> copy(filter = null)
    EditPhotoTab.ADJUST -> copy(adjust = ImageAdjust())
    EditPhotoTab.DEEP_FRY -> copy(deepFry = 0f)
}

/** The events a tab's Reset logs: one for each part it changed (`filter` with the filter `none`, the `adjust_*` ids, `deep_fry`). */
fun changedColorTools(before: ImageEdit, after: ImageEdit): List<EditorTool> = buildList {
    if (before.filter != after.filter) add(EditorTool.FILTER)
    for (part in AdjustPart.entries) if (before.adjust.valueOf(part) != after.adjust.valueOf(part)) add(part.tool)
    if (before.deepFry != after.deepFry) add(EditorTool.DEEP_FRY)
}
