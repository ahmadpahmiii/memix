package app.memix.feature.videoeditor.masks

import androidx.compose.ui.geometry.Offset
import app.memix.core.model.project.ClipMask
import app.memix.core.model.project.MaskCatalog
import app.memix.core.model.project.MaskShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The arithmetic of the mask's gestures on the stage (spec P4-03b → The mask on the stage → Gestures). Pure: no Compose state, no drawing.
 * Every point is in box pixels: the origin is the middle of the clip's own box, x runs right and y down, the box is not turned (the stage
 * turns the overlay's picture; this is the picture's own axes), and one unit is a canvas pixel at the clip's scale.
 */

/** The clip's own box in box pixels. */
internal data class BoxPx(val width: Float, val height: Float) {
    val shorter: Float get() = min(width, height)
}

/** One of the four edge handles of a Rectangle or Circle, with the side of the shape it drags (-1 or 1 along its own axis). */
internal enum class MaskEdge(val signX: Int, val signY: Int) { RIGHT(1, 0), LEFT(-1, 0), BOTTOM(0, 1), TOP(0, -1) }

/** What a gesture has snapped to; each part going from false to true is one haptic tick. */
internal data class MaskSnaps(val centerX: Boolean = false, val centerY: Boolean = false, val flush: Boolean = false, val rotation: Boolean = false) {
    /** True when [this] has a snap that [before] had not. */
    fun engagedSince(before: MaskSnaps): Boolean =
        (centerX && !before.centerX) || (centerY && !before.centerY) || (flush && !before.flush) || (rotation && !before.rotation)
}

internal object MaskGestureMath {
    /** The mask's center in box pixels. */
    fun centerOf(mask: ClipMask, box: BoxPx): Offset = Offset((mask.centerX - MaskCatalog.CENTER) * box.width, (mask.centerY - MaskCatalog.CENTER) * box.height)

    /** The half extents of a Rectangle or Circle along its own axes, in box pixels (Mirror: x is the band's half width). */
    fun halfExtentsOf(mask: ClipMask, box: BoxPx): Offset = when (mask.shape) {
        MaskShape.RECTANGLE -> Offset(mask.width * box.width / 2f, mask.height * box.height / 2f)
        MaskShape.CIRCLE -> Offset(mask.width * box.shorter / 2f, mask.height * box.shorter / 2f)
        MaskShape.MIRROR -> Offset(mask.width * box.shorter / 2f, 0f)
        MaskShape.LINEAR -> Offset.Zero
    }

    /** [point] in the mask's own axes (turned by its rotation), from its center. */
    fun toMaskAxes(mask: ClipMask, box: BoxPx, point: Offset): Offset {
        val radians = mask.rotationDegrees.toRadians()
        val d = point - centerOf(mask, box)
        return Offset(d.x * cos(radians) + d.y * sin(radians), -d.x * sin(radians) + d.y * cos(radians))
    }

    /** A point given in the mask's own axes, back in box pixels. */
    fun fromMaskAxes(mask: ClipMask, box: BoxPx, local: Offset): Offset {
        val radians = mask.rotationDegrees.toRadians()
        val center = centerOf(mask, box)
        return Offset(center.x + local.x * cos(radians) - local.y * sin(radians), center.y + local.x * sin(radians) + local.y * cos(radians))
    }

    /** One finger inside the frame: the mask moves with it. */
    fun moved(start: ClipMask, box: BoxPx, delta: Offset): ClipMask =
        MaskCatalog.clean(start.copy(centerX = start.centerX + delta.x / box.width, centerY = start.centerY + delta.y / box.height))

    /** The scale-and-rotate handle dragged from [from] to [to]: the size follows the distance from the center, the turn follows the angle. */
    fun scaledAndTurned(start: ClipMask, box: BoxPx, from: Offset, to: Offset): ClipMask {
        val center = centerOf(start, box)
        val a = from - center
        val b = to - center
        if (a.getDistance() < MIN_LEVER_PX || b.getDistance() < MIN_LEVER_PX) return start
        val turn = (atan2(b.y, b.x) - atan2(a.y, a.x)).toDegrees()
        return scaledBy(start, b.getDistance() / a.getDistance()).let { MaskCatalog.clean(it.copy(rotationDegrees = start.rotationDegrees + turn)) }
    }

    /** Linear and Mirror's turn handle: the line turns to point at [to]; nothing is sized. */
    fun turnedTo(start: ClipMask, box: BoxPx, from: Offset, to: Offset): ClipMask {
        val center = centerOf(start, box)
        val a = from - center
        val b = to - center
        if (a.getDistance() < MIN_LEVER_PX || b.getDistance() < MIN_LEVER_PX) return start
        val turn = (atan2(b.y, b.x) - atan2(a.y, a.x)).toDegrees()
        return MaskCatalog.clean(start.copy(rotationDegrees = start.rotationDegrees + turn))
    }

    /** Both sides of a Rectangle, Circle or Mirror scaled by [factor] about the center (a pinch). */
    fun scaledBy(mask: ClipMask, factor: Float): ClipMask = when (mask.shape) {
        MaskShape.RECTANGLE, MaskShape.CIRCLE -> MaskCatalog.clean(mask.copy(width = mask.width * factor, height = mask.height * factor))
        MaskShape.MIRROR -> MaskCatalog.clean(mask.copy(width = mask.width * factor))
        MaskShape.LINEAR -> mask
    }

    /** Two fingers: [factor] of the distance between them, [turnDegrees] of the line between them, [pan] of their middle, all since they began. */
    fun pinched(start: ClipMask, box: BoxPx, factor: Float, turnDegrees: Float, pan: Offset): ClipMask {
        val scaled = scaledBy(start, factor)
        return moved(MaskCatalog.clean(scaled.copy(rotationDegrees = start.rotationDegrees + turnDegrees)), box, pan)
    }

    /**
     * An edge handle of a Rectangle or Circle dragged to [to]: that side follows the finger along its own axis and the opposite side stays
     * (so the center moves half as far), stopping at the size limits.
     */
    fun edgeResized(start: ClipMask, box: BoxPx, edge: MaskEdge, to: Offset): ClipMask {
        val reference = if (start.shape == MaskShape.RECTANGLE) Offset(box.width, box.height) else Offset(box.shorter, box.shorter)
        val half = halfExtentsOf(start, box)
        val local = toMaskAxes(start, box, to)
        val alongX = edge.signX != 0
        val oldHalf = if (alongX) half.x else half.y
        val reach = if (alongX) local.x * edge.signX else local.y * edge.signY
        val refSide = if (alongX) reference.x else reference.y
        val newHalf = ((oldHalf + reach) / 2f).coerceIn(MaskCatalog.SIZE_MIN * refSide / 2f, MaskCatalog.SIZE_MAX * refSide / 2f)
        // The fixed side is at -oldHalf in the dragged direction: the center sits half the new size from it.
        val shift = (newHalf - oldHalf) * (if (alongX) edge.signX else edge.signY)
        val shiftLocal = if (alongX) Offset(shift, 0f) else Offset(0f, shift)
        val newCenter = fromMaskAxes(start, box, shiftLocal)
        val sized = if (alongX) start.copy(width = 2f * newHalf / refSide) else start.copy(height = 2f * newHalf / refSide)
        return MaskCatalog.clean(sized.copy(centerX = MaskCatalog.CENTER + newCenter.x / box.width, centerY = MaskCatalog.CENTER + newCenter.y / box.height))
    }

    /** A Mirror band's edge line dragged to [to]: the band's half width is the finger's distance from the middle line. */
    fun bandWidened(start: ClipMask, box: BoxPx, to: Offset): ClipMask {
        val local = toMaskAxes(start, box, to)
        return MaskCatalog.clean(start.copy(width = 2f * abs(local.y) / box.shorter))
    }

    /**
     * [mask] pulled to the snaps within [tolerancePx] box pixels (spec → Gestures): with [snapsPlace], the center to the box's center and the
     * sides flush with the box's edges; with [snapsRotation], the rotation to a quarter turn within [MaskCatalog.ROTATION_SNAP_DEGREES].
     * Reports which snaps hold, for the guides and the tick.
     */
    fun snapped(mask: ClipMask, box: BoxPx, tolerancePx: Float, snapsPlace: Boolean = true, snapsRotation: Boolean = true): Pair<ClipMask, MaskSnaps> {
        var result = mask
        val quarter = (mask.rotationDegrees / QUARTER_TURN).roundToInt()
        val rotationHolds = snapsRotation && abs(mask.rotationDegrees - quarter * QUARTER_TURN) <= MaskCatalog.ROTATION_SNAP_DEGREES
        if (rotationHolds) result = result.copy(rotationDegrees = quarter * QUARTER_TURN)
        if (!snapsPlace) return result to MaskSnaps(rotation = rotationHolds)
        var centerX = false
        var centerY = false
        if (abs((result.centerX - MaskCatalog.CENTER) * box.width) <= tolerancePx) {
            result = result.copy(centerX = MaskCatalog.CENTER)
            centerX = true
        }
        if (abs((result.centerY - MaskCatalog.CENTER) * box.height) <= tolerancePx) {
            result = result.copy(centerY = MaskCatalog.CENTER)
            centerY = true
        }
        val aligned = abs(result.rotationDegrees - (result.rotationDegrees / QUARTER_TURN).roundToInt() * QUARTER_TURN) < ALIGNED_DEGREES
        val flush = if (aligned && result.shape.hasSides) flushed(result, box, tolerancePx, (result.rotationDegrees / QUARTER_TURN).roundToInt()) else null
        if (flush != null) result = flush
        return result to MaskSnaps(centerX, centerY, flush != null, rotationHolds)
    }

    // A side within the tolerance of the box's edge moves the center so it lies on it; only when the shape's axes line up with the box's.
    private fun flushed(mask: ClipMask, box: BoxPx, tolerancePx: Float, quarterTurns: Int): ClipMask? {
        val half = halfExtentsOf(mask, box)
        val sideways = abs(quarterTurns) % 2 == 1
        val halfX = if (sideways) half.y else half.x
        val halfY = if (sideways) half.x else half.y
        val center = centerOf(mask, box)
        val dx = snapOffset(center.x, halfX, box.width / 2f, tolerancePx)
        val dy = snapOffset(center.y, halfY, box.height / 2f, tolerancePx)
        if (dx == null && dy == null) return null
        return mask.copy(centerX = mask.centerX + (dx ?: 0f) / box.width, centerY = mask.centerY + (dy ?: 0f) / box.height)
    }

    // How far to move a shape centered at [center] with half size [half] so a side meets the box's edge at +-[edge], or null when neither is near.
    private fun snapOffset(center: Float, half: Float, edge: Float, tolerancePx: Float): Float? {
        val toLow = (-edge) - (center - half)
        val toHigh = edge - (center + half)
        return when {
            abs(toLow) <= tolerancePx && abs(toLow) <= abs(toHigh) -> toLow
            abs(toHigh) <= tolerancePx -> toHigh
            else -> null
        }
    }

    private val MaskShape.hasSides: Boolean get() = this == MaskShape.RECTANGLE || this == MaskShape.CIRCLE

    private const val MIN_LEVER_PX = 1f
    private const val ALIGNED_DEGREES = 0.01f
    private const val QUARTER_TURN = 90f
}

internal fun Float.toRadians(): Float = this * (PI.toFloat() / 180f)

internal fun Float.toDegrees(): Float = this * (180f / PI.toFloat())
