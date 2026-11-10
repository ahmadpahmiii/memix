package app.memix.core.domain.project

import app.memix.core.model.project.BoxSize
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Transform
import kotlin.math.max
import kotlin.math.min

/*
 * What a ratio change does to one layer (spec docs/ux/specs/P1-11-canvas.md and P2-01-photo-editor-shell.md → Ratio
 * change → Layers): the same rule for captions on a video, and for every kind of layer on a photo.
 */

/**
 * This [Transform] on the canvas [new] after a ratio change from [old]. [boxAt] is the layer's box (its turned bounds,
 * in canvas pixels) on a canvas of a given size at a given scale.
 *
 * Fully inside [old] and crossing an edge of [new]: it shrinks first if it's bigger than [new] less two margins (never
 * below [scaleMin]), then moves straight in to the margin, which is [marginOfShortSide] of the shorter side. Still too
 * big at the smallest size, it's centered on that axis. Partly outside [old], placed there on purpose: unchanged.
 */
internal fun Transform.keptInside(
    old: PixelSize,
    new: PixelSize,
    scaleMin: Float,
    marginOfShortSide: Float,
    boxAt: (canvas: PixelSize, scale: Float) -> BoxSize,
): Transform {
    val oldBox = boxAt(old, scale)
    val oldCenterX = centerX * old.widthPx
    val oldCenterY = centerY * old.heightPx
    // Hung off the edge on purpose: it keeps its place (spec → Layers, 3).
    if (crosses(oldCenterX, oldBox.width, old.widthPx.toFloat()) || crosses(oldCenterY, oldBox.height, old.heightPx.toFloat())) return this

    var keptScale = scale
    var box = boxAt(new, keptScale)
    val newCenterX = centerX * new.widthPx
    val newCenterY = centerY * new.heightPx
    if (!crosses(newCenterX, box.width, new.widthPx.toFloat()) && !crosses(newCenterY, box.height, new.heightPx.toFloat())) return this

    val marginPx = min(new.widthPx, new.heightPx) * marginOfShortSide
    val roomWidth = new.widthPx - 2 * marginPx
    val roomHeight = new.heightPx - 2 * marginPx
    if (box.width > roomWidth + EDGE_TOLERANCE_PX || box.height > roomHeight + EDGE_TOLERANCE_PX) {
        // The box grows with the scale, so one ratio finds the size that fits.
        val fitting = keptScale * min(roomWidth / box.width, roomHeight / box.height)
        keptScale = max(fitting, min(keptScale, scaleMin))
        box = boxAt(new, keptScale)
    }
    val keptCenterX = keptOnAxis(newCenterX, box.width, new.widthPx.toFloat(), marginPx)
    val keptCenterY = keptOnAxis(newCenterY, box.height, new.heightPx.toFloat(), marginPx)
    return copy(centerX = keptCenterX / new.widthPx, centerY = keptCenterY / new.heightPx, scale = keptScale)
}

// Where a box [length] long centered at [center] goes on an axis [size] long: where it is when it doesn't cross an edge,
// else just far enough in to sit [margin] inside it; centered when it can't fit between the margins.
private fun keptOnAxis(center: Float, length: Float, size: Float, margin: Float): Float {
    val half = length / 2
    return when {
        !crosses(center, length, size) -> center
        length > size - 2 * margin + EDGE_TOLERANCE_PX -> size / 2
        else -> {
            // A box within the tolerance of filling the room can leave the range empty by a rounding error: center it.
            val lowest = margin + half
            val highest = size - margin - half
            if (lowest > highest) size / 2 else center.coerceIn(lowest, highest)
        }
    }
}

// Whether a box [length] long centered at [center] reaches past either end of an axis [size] long.
private fun crosses(center: Float, length: Float, size: Float): Boolean =
    center - length / 2 < -EDGE_TOLERANCE_PX || center + length / 2 > size + EDGE_TOLERANCE_PX

/** Rounding slack: a box this close to an edge counts as touching it, not crossing it. */
internal const val EDGE_TOLERANCE_PX = 0.5f
