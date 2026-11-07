package app.memix.core.ui.crop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.memix.core.model.project.CropFrames
import app.memix.core.model.project.CropRect
import app.memix.core.model.project.ImageEdit
import app.memix.core.model.project.ImageToolCatalog
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.flippedAcross
import app.memix.core.model.project.flippedDown
import app.memix.core.model.project.turnedLeft
import app.memix.core.model.project.turnedRight
import app.memix.core.model.project.turnedSize
import kotlin.math.abs
import kotlin.math.round

/** The aspect tiles, in the screen's order. A [ratio] is width ÷ height; null means "decided by the picture or the canvas" (spec P2-04 → Layout). */
enum class CropAspect(val ratio: Float?) {
    FREE(null), ORIGINAL(null), CANVAS(null), SQUARE(1f), FOUR_FIVE(4f / 5f), THREE_FOUR(3f / 4f), NINE_SIXTEEN(9f / 16f), SIXTEEN_NINE(16f / 9f),
}

/**
 * What the Crop screen is editing (spec docs/ux/specs/P2-04-image-tools.md → The Crop screen): the picture's [ImageEdit] with
 * its turn, mirrors, straighten and crop, held apart from any layer so P4-16's video clips can use the same screen. Nothing
 * here is applied to a project: [result] is handed back on Done, and closing drops it. The crop is its own state, read in the
 * stage's draw phase, so a drag redraws the frame without recomposing the screen.
 */
@Stable
class CropState(private val initial: ImageEdit, val pictureSize: PixelSize, private val canvasAspect: Float) {
    /** The canvas's shape, for the Canvas tile's picture. */
    val canvasShape: Float get() = canvasAspect

    /** Turn, mirrors and straighten (and any color edits, carried through untouched); the crop is [crop]. */
    var base by mutableStateOf(initial)
        private set
    var crop by mutableStateOf(initial.crop)
        private set
    var aspect by mutableStateOf(CropAspect.FREE)
        private set

    /** The locked shape (width ÷ height in pixels), or null while Free. */
    var lockedAspect: Float? by mutableStateOf(null)
        private set
    var dragging by mutableStateOf(false)

    /** The turned picture's frame in pixels. */
    val turned: PixelSize by derivedStateOf { base.turnedSize(pictureSize) }

    /** The edit as it stands: hand it back on Done. */
    val result: ImageEdit get() = base.copy(crop = crop)

    fun dragCrop(handle: CropFrames.Handle, xFraction: Float, yFraction: Float) {
        crop = if (handle == CropFrames.Handle.MOVE) crop else CropFrames.dragged(crop, handle, xFraction, yFraction, lockedAspect, turned)
    }

    fun moveCrop(dx: Float, dy: Float) {
        crop = CropFrames.moved(crop, dx, dy)
    }

    /** Selecting a tile replaces the frame by the largest centered frame of that shape inside the picture, and locks it (Free unlocks). */
    fun select(choice: CropAspect) {
        aspect = choice
        val ratio = ratioOf(choice)
        lockedAspect = ratio
        if (ratio != null) crop = CropFrames.centeredWithAspect(ratio, turned)
    }

    private fun ratioOf(choice: CropAspect): Float? = when (choice) {
        CropAspect.FREE -> null
        CropAspect.ORIGINAL -> turned.widthPx.toFloat() / turned.heightPx
        CropAspect.CANVAS -> canvasAspect
        else -> choice.ratio
    }

    fun turnLeft() = turn(result.turnedLeft())

    fun turnRight() = turn(result.turnedRight())

    // The frame turns with the picture, so its shape inverts; a locked shape inverts with it (Original follows the new frame).
    private fun turn(turnedEdit: ImageEdit) {
        base = turnedEdit
        crop = turnedEdit.crop
        lockedAspect = lockedAspect?.let { if (aspect == CropAspect.ORIGINAL) turned.widthPx.toFloat() / turned.heightPx else 1f / it }
    }

    fun flipAcross() = mirror(result.flippedAcross())

    fun flipDown() = mirror(result.flippedDown())

    private fun mirror(mirrored: ImageEdit) {
        base = mirrored
        crop = mirrored.crop
    }

    /** Degrees, rounded to [ImageToolCatalog.STRAIGHTEN_STEP] and kept in range. */
    fun straighten(degrees: Float) {
        val step = ImageToolCatalog.STRAIGHTEN_STEP
        val rounded = round(degrees / step) * step
        base = base.copy(straighten = rounded.coerceIn(ImageToolCatalog.STRAIGHTEN_RANGE))
    }

    /** One Edges-panel step on [side] by [count] taps (negative trims less); false when the first step can't move. */
    fun stepEdge(side: CropFrames.Handle, count: Int): Boolean {
        var moved = false
        val sign = if (count >= 0) 1 else -1
        repeat(abs(count)) {
            val next = CropFrames.edgeStepped(crop, side, sign * ImageToolCatalog.EDGE_STEP, lockedAspect, turned) ?: return moved
            crop = next
            moved = true
        }
        return moved
    }

    /** How much of the picture is trimmed from [side], in percent (the Edges readout). */
    fun trimmedPercent(side: CropFrames.Handle): Int = round(
        100f * when (side) {
            CropFrames.Handle.LEFT -> crop.left
            CropFrames.Handle.RIGHT -> 1f - crop.right
            CropFrames.Handle.TOP -> crop.top
            else -> 1f - crop.bottom
        },
    ).toInt()

    /** Back to the state the screen opened in. */
    fun reset() {
        base = initial
        crop = initial.crop
        aspect = CropAspect.FREE
        lockedAspect = null
    }
}
