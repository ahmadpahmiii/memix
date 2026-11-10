package app.memix.core.ui.crop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixStroke
import app.memix.core.model.project.CropFrames
import app.memix.core.model.project.CropRect
import app.memix.core.model.project.PixelSize
import app.memix.core.ui.scene.drawPictureWithEdit
import kotlin.math.hypot
import kotlin.math.min

/**
 * The Crop screen's stage (spec docs/ux/specs/P2-04-image-tools.md → The crop frame): the whole picture with its turn, mirrors and
 * straighten, centered on black, the crop frame over it with the outside dimmed. A drag on a corner or an edge resizes the
 * frame, a drag inside moves it. The frame is read in the draw phase only. Hidden from screen readers: the aspect tiles, the
 * Edges panel and Straighten are the single-pointer way (WCAG 2.5.1, 2.5.7).
 */
@Composable
internal fun CropStage(state: CropState, picture: ImageBitmap?, modifier: Modifier = Modifier) {
    val gridAlpha = animateFloatAsState(if (state.dragging) 1f else 0f, tween(MemixMotion.durationPress), label = "grid")
    Box(
        modifier
            .clearAndSetSemantics {}
            .drawBehind { drawStage(state, picture, gridAlpha.value) }
            .pointerInput(state) { handleDrags(state) },
    )
}

// The turned picture's frame, fitted in the stage with room for the handles all round.
private fun stageFrame(stage: Size, turned: PixelSize, margin: Float): Rect {
    val width = (stage.width - 2 * margin).coerceAtLeast(1f)
    val height = (stage.height - 2 * margin).coerceAtLeast(1f)
    val scale = min(width / turned.widthPx, height / turned.heightPx)
    val frameWidth = turned.widthPx * scale
    val frameHeight = turned.heightPx * scale
    return Rect((stage.width - frameWidth) / 2f, (stage.height - frameHeight) / 2f, (stage.width + frameWidth) / 2f, (stage.height + frameHeight) / 2f)
}

private fun cropRectIn(frame: Rect, crop: CropRect) = Rect(
    frame.left + crop.left * frame.width, frame.top + crop.top * frame.height,
    frame.left + crop.right * frame.width, frame.top + crop.bottom * frame.height,
)

private fun DrawScope.drawStage(state: CropState, picture: ImageBitmap?, gridAlpha: Float) {
    val handle = MemixSize.canvasHandleSize.toPx()
    val frame = stageFrame(size, state.turned, handle)
    if (picture != null) {
        withTransform({ translate(frame.center.x, frame.center.y) }) {
            drawPictureWithEdit(picture, state.pictureSize, state.base.copy(crop = CropRect.Full), frame.width, frame.height)
        }
    }
    val crop = cropRectIn(frame, state.crop)
    // The outside of the frame, dimmed (four bars around the crop).
    drawRect(MemixColors.scrim, frame.topLeft, Size(frame.width, crop.top - frame.top))
    drawRect(MemixColors.scrim, Offset(frame.left, crop.bottom), Size(frame.width, frame.bottom - crop.bottom))
    drawRect(MemixColors.scrim, Offset(frame.left, crop.top), Size(crop.left - frame.left, crop.height))
    drawRect(MemixColors.scrim, Offset(crop.right, crop.top), Size(frame.right - crop.right, crop.height))
    if (gridAlpha > 0f) drawThirds(crop, gridAlpha)
    drawFrameOutline(crop)
    drawHandles(crop, handle)
}

// Two-tone like every canvas handle: a dark edge under the white line, so it reads over any picture.
private fun DrawScope.drawFrameOutline(crop: Rect) {
    val line = MemixStroke.strokeSelection.toPx()
    drawRect(MemixColors.onPrimary, crop.topLeft, crop.size, style = Stroke(line + 2 * MemixStroke.strokeHairline.toPx()))
    drawRect(MemixColors.selection, crop.topLeft, crop.size, style = Stroke(line))
}

private fun DrawScope.drawThirds(crop: Rect, alpha: Float) {
    val color = MemixColors.selection.copy(alpha = THIRDS_ALPHA * alpha)
    val width = MemixStroke.strokeHairline.toPx()
    for (i in 1..2) {
        val x = crop.left + crop.width * i / 3f
        val y = crop.top + crop.height * i / 3f
        drawLine(color, Offset(x, crop.top), Offset(x, crop.bottom), width)
        drawLine(color, Offset(crop.left, y), Offset(crop.right, y), width)
    }
}

// An L at each corner (arms [arm] long) and a short bar at each edge's middle, white with the dark edge.
private fun DrawScope.drawHandles(crop: Rect, arm: Float) {
    val thickness = MemixStroke.strokeSelection.toPx() * 2f
    fun bar(from: Offset, to: Offset) {
        drawLine(MemixColors.onPrimary, from, to, thickness + 2 * MemixStroke.strokeHairline.toPx())
        drawLine(MemixColors.selection, from, to, thickness)
    }
    for (corner in listOf(crop.topLeft, crop.topRight, crop.bottomLeft, crop.bottomRight)) {
        val dx = if (corner.x == crop.left) arm else -arm
        val dy = if (corner.y == crop.top) arm else -arm
        bar(corner, Offset(corner.x + dx, corner.y))
        bar(corner, Offset(corner.x, corner.y + dy))
    }
    val half = arm / 2f
    bar(Offset(crop.center.x - half, crop.top), Offset(crop.center.x + half, crop.top))
    bar(Offset(crop.center.x - half, crop.bottom), Offset(crop.center.x + half, crop.bottom))
    bar(Offset(crop.left, crop.center.y - half), Offset(crop.left, crop.center.y + half))
    bar(Offset(crop.right, crop.center.y - half), Offset(crop.right, crop.center.y + half))
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.handleDrags(state: CropState) {
    val target = MemixSize.touchTarget.toPx() / 2f
    var handle: CropFrames.Handle? = null
    var finger = Offset.Zero
    detectDragGestures(
        onDragStart = { start ->
            val frame = stageFrame(Size(size.width.toFloat(), size.height.toFloat()), state.turned, MemixSize.canvasHandleSize.toPx())
            handle = handleAt(start, cropRectIn(frame, state.crop), target)
            finger = start
            state.dragging = handle != null
        },
        onDragEnd = { state.dragging = false },
        onDragCancel = { state.dragging = false },
    ) { change, dragAmount ->
        val held = handle ?: return@detectDragGestures
        change.consume()
        finger += dragAmount
        val frame = stageFrame(Size(size.width.toFloat(), size.height.toFloat()), state.turned, MemixSize.canvasHandleSize.toPx())
        if (held == CropFrames.Handle.MOVE) {
            state.moveCrop(dragAmount.x / frame.width, dragAmount.y / frame.height)
        } else {
            state.dragCrop(held, (finger.x - frame.left) / frame.width, (finger.y - frame.top) / frame.height)
        }
    }
}

// The handle whose 48 dp target holds [point]: corners first, then edges (a locked shape's edges do nothing), then the inside.
private fun handleAt(point: Offset, crop: Rect, target: Float): CropFrames.Handle? {
    val corners = listOf(
        CropFrames.Handle.TOP_LEFT to crop.topLeft, CropFrames.Handle.TOP_RIGHT to crop.topRight,
        CropFrames.Handle.BOTTOM_LEFT to crop.bottomLeft, CropFrames.Handle.BOTTOM_RIGHT to crop.bottomRight,
    )
    corners.firstOrNull { (_, at) -> hypot(point.x - at.x, point.y - at.y) <= target }?.let { return it.first }
    val withinY = point.y in (crop.top - target)..(crop.bottom + target)
    val withinX = point.x in (crop.left - target)..(crop.right + target)
    return when {
        withinY && kotlin.math.abs(point.x - crop.left) <= target -> CropFrames.Handle.LEFT
        withinY && kotlin.math.abs(point.x - crop.right) <= target -> CropFrames.Handle.RIGHT
        withinX && kotlin.math.abs(point.y - crop.top) <= target -> CropFrames.Handle.TOP
        withinX && kotlin.math.abs(point.y - crop.bottom) <= target -> CropFrames.Handle.BOTTOM
        crop.contains(point) -> CropFrames.Handle.MOVE
        else -> null
    }
}

private const val THIRDS_ALPHA = 0.4f
