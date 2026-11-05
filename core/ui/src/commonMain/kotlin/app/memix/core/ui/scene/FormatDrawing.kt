package app.memix.core.ui.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.BoxSize
import app.memix.core.model.project.BubbleLayer
import app.memix.core.model.project.BubblePoint
import app.memix.core.model.project.BubbleShape
import app.memix.core.model.project.BubblesFormat
import app.memix.core.model.project.CaptionBarFormat
import app.memix.core.model.project.CaptionLook
import app.memix.core.model.project.ClassicFormat
import app.memix.core.model.project.DemotivationalFormat
import app.memix.core.model.project.PanelsFormat
import app.memix.core.model.project.PhotoFormat
import app.memix.core.model.project.PhotoFormatCatalog
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Transform
import app.memix.core.model.project.bubbleBodySize
import app.memix.core.model.project.bubbleTail
import app.memix.core.model.project.captionBarRect
import app.memix.core.model.project.clampedTail
import app.memix.core.model.project.demotivationalFrameRect
import app.memix.core.model.project.geometry
import app.memix.core.model.project.pictureRect
import app.memix.core.model.project.shortSideOf
import app.memix.core.model.project.thoughtDots
import kotlin.math.hypot
import kotlin.math.min

/*
 * What a meme format draws under the layers and what a speech bubble looks like (spec docs/ux/specs/P2-02-meme-formats.md).
 * All in canvas pixels, from the pure functions in :core:model, so the editor, the render hook and export draw the same.
 */

/** The format's parts, after the background and under the layers. Plain, Classic and Bubbles have none. */
internal fun DrawScope.drawFormatParts(format: PhotoFormat?, canvasSize: PixelSize, images: SceneImages) {
    when (format) {
        null, ClassicFormat, BubblesFormat -> Unit
        CaptionBarFormat -> captionBarRect(canvasSize).let { bar ->
            drawRect(colorOf(PhotoFormatCatalog.BAR_COLOR), Offset(bar.left, bar.top), Size(bar.width, bar.height))
        }
        DemotivationalFormat -> drawPoster(canvasSize)
        is PanelsFormat -> drawPanelCells(format, canvasSize, images)
    }
}

// The poster paints the whole canvas black (hiding the canvas background), then the frame's white line is stroked on its edge.
private fun DrawScope.drawPoster(canvasSize: PixelSize) {
    drawRect(colorOf(PhotoFormatCatalog.POSTER_COLOR), Offset.Zero, Size(canvasSize.widthPx.toFloat(), canvasSize.heightPx.toFloat()))
    val frame = demotivationalFrameRect(canvasSize)
    drawRect(
        colorOf(PhotoFormatCatalog.FRAME_COLOR),
        Offset(frame.left, frame.top),
        Size(frame.width, frame.height),
        style = Stroke(PhotoFormatCatalog.FRAME_STROKE_OF_SHORT_SIDE * shortSideOf(canvasSize)),
    )
}

// Each cell clips its picture, drawn to cover; an empty cell, or one whose picture isn't ready or is missing, draws nothing: the
// gutter's own color (the canvas background) shows (spec → Panels: an empty cell is background in the render and the export).
private fun DrawScope.drawPanelCells(format: PanelsFormat, canvasSize: PixelSize, images: SceneImages) {
    val geometry = format.geometry(canvasSize) ?: return
    format.cells.forEachIndexed { index, cell ->
        val rect = geometry.cells.getOrNull(index) ?: return@forEachIndexed
        val path = cell.source?.cachedCopyPath ?: return@forEachIndexed
        val picture = images.imageOf(path) ?: return@forEachIndexed
        val shown = cell.source?.pixelSize ?: picture.sourceSize
        val target = cell.pictureRect(rect, shown)
        clipRect(rect.left, rect.top, rect.right, rect.bottom) {
            withTransform({
                translate(target.left, target.top)
                scale(target.width / picture.image.width, target.height / picture.image.height, Offset.Zero)
            }) {
                drawImage(
                    picture.image,
                    srcSize = IntSize(picture.image.width, picture.image.height),
                    dstSize = IntSize(picture.image.width, picture.image.height),
                    alpha = images.appearanceOf(path),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}

/**
 * A bubble: the body and tail as one filled path with one round-joined black stroke (a thought bubble's circles each filled
 * and stroked), then its words through [captions] at the bubble's size and wrap. At an opacity below 1 the whole bubble goes
 * into its own layer first, so the stroke never shows through the fill (as a text's outline doesn't). [liveTail] is the tip
 * as a finger has it now, in body widths and heights.
 */
internal fun DrawScope.drawBubbleLayer(
    layer: BubbleLayer,
    transform: Transform,
    opacity: Float,
    liveTail: BubblePoint?,
    canvasSize: PixelSize,
    captions: SceneCaptions,
) {
    val block = captions.measure(layer.text, layer.style, canvasSize.widthPx, canvasSize.heightPx, CaptionLook.Bubble)
    val body = bubbleBodySize(block.width, block.height, canvasSize)
    val tip = liveTail?.let { clampedTail(it.x, it.y) } ?: BubblePoint(layer.tailX, layer.tailY)
    val tipPx = BubblePoint(tip.x * body.width, tip.y * body.height)
    val grouped = opacity < 1f
    if (grouped) drawContext.canvas.saveLayer(Rect(0f, 0f, canvasSize.widthPx.toFloat(), canvasSize.heightPx.toFloat()), Paint().apply { alpha = opacity })
    withTransform({
        translate(transform.centerX * canvasSize.widthPx, transform.centerY * canvasSize.heightPx)
        rotate(transform.rotationDegrees, Offset.Zero)
        scale(transform.scale, transform.scale, Offset.Zero)
    }) {
        val fill = colorOf(layer.fill)
        val stroke = Stroke(PhotoFormatCatalog.BUBBLE_STROKE_OF_SHORT_SIDE * shortSideOf(canvasSize), join = StrokeJoin.Round)
        val strokeColor = colorOf(PhotoFormatCatalog.BUBBLE_STROKE_COLOR)
        if (layer.shape == BubbleShape.THOUGHT) {
            drawThoughtBubble(body, tipPx, canvasSize, fill, strokeColor, stroke)
        } else {
            val path = speechOrRoundPath(layer.shape, body, tipPx)
            drawPath(path, fill, style = Fill)
            drawPath(path, strokeColor, style = stroke)
        }
    }
    captions.draw(this, layer.text, layer.style, transform, if (grouped) 1f else opacity, canvasSize.widthPx, canvasSize.heightPx, CaptionLook.Bubble)
    if (grouped) drawContext.canvas.restore()
}

private fun DrawScope.drawThoughtBubble(body: BoxSize, tip: BubblePoint, canvasSize: PixelSize, fill: Color, strokeColor: Color, stroke: Stroke) {
    val ellipse = Path().apply { addOval(Rect(-body.width / 2f, -body.height / 2f, body.width / 2f, body.height / 2f)) }
    drawPath(ellipse, fill, style = Fill)
    drawPath(ellipse, strokeColor, style = stroke)
    for (dot in thoughtDots(body, tip, canvasSize)) {
        val circle = Path().apply { addOval(Rect(dot.center.x - dot.radius, dot.center.y - dot.radius, dot.center.x + dot.radius, dot.center.y + dot.radius)) }
        drawPath(circle, fill, style = Fill)
        drawPath(circle, strokeColor, style = stroke)
    }
}

// Body and tail as one path, so the stroke runs round the whole outline and never across the join. The tail's triangle reaches
// into the body (its base is moved inward), so a speech bubble's rounded corners leave no gap.
private fun speechOrRoundPath(shape: BubbleShape, body: BoxSize, tip: BubblePoint): Path {
    val halfWidth = body.width / 2f
    val halfHeight = body.height / 2f
    val bodyPath = Path().apply {
        if (shape == BubbleShape.SPEECH) {
            val corner = PhotoFormatCatalog.SPEECH_CORNER_OF_SHORTER_SIDE * min(body.width, body.height)
            addRoundRect(RoundRect(Rect(-halfWidth, -halfHeight, halfWidth, halfHeight), CornerRadius(corner, corner)))
        } else {
            addOval(Rect(-halfWidth, -halfHeight, halfWidth, halfHeight))
        }
    }
    val tail = bubbleTail(shape, body, tip) ?: return bodyPath
    val distance = hypot(tail.tip.x, tail.tip.y)
    val inwardX = -tail.tip.x / distance * TAIL_INSET_OF_SHORTER_SIDE * min(body.width, body.height)
    val inwardY = -tail.tip.y / distance * TAIL_INSET_OF_SHORTER_SIDE * min(body.width, body.height)
    val tailPath = Path().apply {
        moveTo(tail.tip.x, tail.tip.y)
        lineTo(tail.baseA.x, tail.baseA.y)
        lineTo(tail.baseA.x + inwardX, tail.baseA.y + inwardY)
        lineTo(tail.baseB.x + inwardX, tail.baseB.y + inwardY)
        lineTo(tail.baseB.x, tail.baseB.y)
        close()
    }
    return Path.combine(PathOperation.Union, bodyPath, tailPath)
}

private fun colorOf(color: ArgbColor): Color = Color(color.argb.toInt())

private const val TAIL_INSET_OF_SHORTER_SIDE = 0.2f
