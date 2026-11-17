package app.memix.feature.videoeditor.masks

import androidx.compose.ui.geometry.Offset
import app.memix.core.domain.project.MaskStageBox
import app.memix.core.model.project.ClipMask
import app.memix.core.model.project.MaskShape
import app.memix.core.ui.layers.CanvasFrameRect
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** What a finger that went down on the stage has hold of (spec P4-03b → The mask on the stage). */
internal enum class MaskGrab { NONE, MOVE, TURN, EDGE_RIGHT, EDGE_LEFT, EDGE_BOTTOM, EDGE_TOP, BAND_EDGE }

/**
 * Where a clip's box and its mask lie on the stage: maps box pixels (the unturned box, from its middle; [MaskGestureMath]'s space) to stage
 * pixels and back, and finds the handles. [box] is the clip's box on the canvas (turned with an overlay), [frame] the canvas on the stage.
 */
internal class MaskStageGeometry(
    val frame: CanvasFrameRect,
    canvasWidthPx: Int,
    val box: MaskStageBox,
    /** The stage's size and the turn handle's radius, so the handle is kept whole on the stage (spec → Look); no limit by default. */
    private val stageWidth: Float = Float.POSITIVE_INFINITY,
    private val stageHeight: Float = Float.POSITIVE_INFINITY,
    private val handleRadiusPx: Float = 0f,
) {
    /** Stage pixels per canvas pixel. */
    val scale: Float = if (canvasWidthPx > 0) frame.width / canvasWidthPx else 0f
    val boxPx = BoxPx(box.width, box.height)

    private val boxRadians = box.rotationDegrees.toRadians()

    /** The box pixel point [local] on the stage. */
    fun toStage(local: Offset): Offset {
        val x = box.centerX + local.x * cos(boxRadians) - local.y * sin(boxRadians)
        val y = box.centerY + local.x * sin(boxRadians) + local.y * cos(boxRadians)
        return Offset(frame.left + x * scale, frame.top + y * scale)
    }

    /** The box pixel point under stage point [stage]. */
    fun toLocal(stage: Offset): Offset {
        if (scale <= 0f) return Offset.Zero
        val dx = (stage.x - frame.left) / scale - box.centerX
        val dy = (stage.y - frame.top) / scale - box.centerY
        return Offset(dx * cos(boxRadians) + dy * sin(boxRadians), -dx * sin(boxRadians) + dy * cos(boxRadians))
    }

    /** Whether stage point [stage] is inside the clip's box. */
    fun boxContains(stage: Offset): Boolean {
        val local = toLocal(stage)
        return abs(local.x) <= box.width / 2f && abs(local.y) <= box.height / 2f
    }

    /** The turn handle's place in box pixels: a corner of a Rectangle or Circle, a point on the middle line of a Linear or Mirror. */
    fun turnHandle(mask: ClipMask): Offset {
        val half = MaskGestureMath.halfExtentsOf(mask, boxPx)
        val inMaskAxes = if (mask.shape == MaskShape.RECTANGLE || mask.shape == MaskShape.CIRCLE) half else Offset(lineReach(), 0f)
        return MaskGestureMath.fromMaskAxes(mask, boxPx, inMaskAxes)
    }

    /** The turn handle's place on the stage, never closer than half its size to the stage's edge: where it is drawn and where it is grabbed. */
    fun turnHandleOnStage(mask: ClipMask): Offset {
        val at = toStage(turnHandle(mask))
        return Offset(at.x.coerceIn(handleRadiusPx, maxOf(handleRadiusPx, stageWidth - handleRadiusPx)), at.y.coerceIn(handleRadiusPx, maxOf(handleRadiusPx, stageHeight - handleRadiusPx)))
    }

    /** The edge handle [edge]'s place in box pixels (Rectangle and Circle). */
    fun edgeHandle(mask: ClipMask, edge: MaskEdge): Offset {
        val half = MaskGestureMath.halfExtentsOf(mask, boxPx)
        return MaskGestureMath.fromMaskAxes(mask, boxPx, Offset(edge.signX * half.x, edge.signY * half.y))
    }

    /** How far from the center along its line the Linear and Mirror turn handle sits. */
    fun lineReach(): Float = boxPx.shorter * LINE_HANDLE_SHARE

    /**
     * What a finger at stage point [stage] has hold of: a handle first (within [targetRadiusPx] of its center), then a Mirror's band edge,
     * then anywhere inside the clip's box (moves the mask); outside the box nothing, so the touch plays or pauses as everywhere.
     */
    fun grabAt(mask: ClipMask, stage: Offset, targetRadiusPx: Float): MaskGrab {
        if (near(stage, turnHandleOnStage(mask), targetRadiusPx)) return MaskGrab.TURN
        when (mask.shape) {
            MaskShape.RECTANGLE, MaskShape.CIRCLE -> MaskEdge.entries.forEach { edge ->
                if (near(stage, toStage(edgeHandle(mask, edge)), targetRadiusPx)) return edge.grab()
            }
            MaskShape.MIRROR -> if (nearBandEdge(mask, stage, targetRadiusPx)) return MaskGrab.BAND_EDGE
            MaskShape.LINEAR -> Unit
        }
        return if (boxContains(stage)) MaskGrab.MOVE else MaskGrab.NONE
    }

    // A Mirror's band edge is a whole line: the finger is near it when its distance across the band, on the stage, is within the target.
    private fun nearBandEdge(mask: ClipMask, stage: Offset, targetRadiusPx: Float): Boolean {
        val acrossPx = abs(MaskGestureMath.toMaskAxes(mask, boxPx, toLocal(stage)).y) * scale
        val bandPx = MaskGestureMath.halfExtentsOf(mask, boxPx).x * scale
        return abs(acrossPx - bandPx) <= targetRadiusPx && boxContains(stage)
    }

    private fun near(a: Offset, b: Offset, radiusPx: Float): Boolean = (a - b).getDistance() <= radiusPx

    private companion object {
        /** The Linear and Mirror handle sits this share of the box's shorter side from the center. */
        const val LINE_HANDLE_SHARE = 0.4f
    }
}

internal fun MaskEdge.grab(): MaskGrab = when (this) {
    MaskEdge.RIGHT -> MaskGrab.EDGE_RIGHT
    MaskEdge.LEFT -> MaskGrab.EDGE_LEFT
    MaskEdge.BOTTOM -> MaskGrab.EDGE_BOTTOM
    MaskEdge.TOP -> MaskGrab.EDGE_TOP
}

internal fun MaskGrab.edge(): MaskEdge? = when (this) {
    MaskGrab.EDGE_RIGHT -> MaskEdge.RIGHT
    MaskGrab.EDGE_LEFT -> MaskEdge.LEFT
    MaskGrab.EDGE_BOTTOM -> MaskEdge.BOTTOM
    MaskGrab.EDGE_TOP -> MaskEdge.TOP
    MaskGrab.NONE, MaskGrab.MOVE, MaskGrab.TURN, MaskGrab.BAND_EDGE -> null
}
