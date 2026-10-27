package app.memix.feature.videoeditor.captions

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import app.memix.core.domain.text.CaptionBlockSize
import app.memix.core.model.project.Transform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Where the canvas frame sits on the stage, in stage pixels: the largest rectangle of the canvas ratio inside the stage
 * less [inset] on every side, centered in the top [areaHeight] of it (typing keeps the frame where it was, spec P1-10
 * → Typing). The preview, the captions and their frames all use this one rectangle.
 */
@Immutable
data class CanvasFrameRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    companion object {
        val Empty = CanvasFrameRect(0f, 0f, 0f, 0f)

        fun fit(stageWidth: Float, areaHeight: Float, aspectRatio: Float, inset: Float): CanvasFrameRect {
            val maxWidth = stageWidth - 2 * inset
            val maxHeight = areaHeight - 2 * inset
            if (maxWidth <= 0f || maxHeight <= 0f || aspectRatio <= 0f) return Empty
            val width = min(maxWidth, maxHeight * aspectRatio)
            val height = width / aspectRatio
            return CanvasFrameRect((stageWidth - width) / 2, (areaHeight - height) / 2, width, height)
        }
    }
}

/**
 * A caption's block on the stage: centered at [center] (stage pixels), [halfWidth] × [halfHeight] before it's turned
 * clockwise by [rotationDegrees] around that center.
 */
@Immutable
data class CaptionBox(val center: Offset, val halfWidth: Float, val halfHeight: Float, val rotationDegrees: Float) {
    /**
     * Whether [point] is on the box grown by [margin] on every side, and to at least [minHalfSize] from the center
     * each way (a 48 dp target, spec P1-10 → On the preview).
     */
    fun contains(point: Offset, margin: Float, minHalfSize: Float): Boolean {
        val local = toLocal(point)
        return abs(local.x) <= max(halfWidth + margin, minHalfSize) && abs(local.y) <= max(halfHeight + margin, minHalfSize)
    }

    /** [point] in the box's own unturned axes, relative to its center. */
    fun toLocal(point: Offset): Offset {
        val radians = -rotationDegrees.toRadians()
        val dx = point.x - center.x
        val dy = point.y - center.y
        return Offset(dx * cos(radians) - dy * sin(radians), dx * sin(radians) + dy * cos(radians))
    }

    /** A corner, turned with the box: [signX] and [signY] are -1 or 1 (1, 1 is bottom-right). */
    fun corner(signX: Int, signY: Int, outset: Float): Offset {
        val radians = rotationDegrees.toRadians()
        val x = signX * (halfWidth + outset)
        val y = signY * (halfHeight + outset)
        return Offset(center.x + x * cos(radians) - y * sin(radians), center.y + x * sin(radians) + y * cos(radians))
    }

    companion object {
        /** The box of a caption whose block at scale 1 is [block] canvas pixels, placed by [transform] in [frame]. */
        fun of(block: CaptionBlockSize, transform: Transform, frame: CanvasFrameRect, canvasWidthPx: Int): CaptionBox {
            val pixelScale = if (canvasWidthPx > 0) frame.width / canvasWidthPx else 0f
            return CaptionBox(
                center = Offset(frame.left + transform.centerX * frame.width, frame.top + transform.centerY * frame.height),
                halfWidth = block.widthPx * transform.scale * pixelScale / 2,
                halfHeight = block.heightPx * transform.scale * pixelScale / 2,
                rotationDegrees = transform.rotationDegrees,
            )
        }
    }
}

internal fun Float.toRadians(): Float = this * (kotlin.math.PI.toFloat() / 180f)

internal fun Float.toDegrees(): Float = this * (180f / kotlin.math.PI.toFloat())
