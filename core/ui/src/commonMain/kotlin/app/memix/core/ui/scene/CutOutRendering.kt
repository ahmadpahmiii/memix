package app.memix.core.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import app.memix.core.model.project.CutOutAlpha
import app.memix.core.model.project.CutOut
import app.memix.core.model.project.DrawCatalog
import app.memix.core.model.project.Stroke
import app.memix.core.model.project.StrokeMode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A layer's [cutOut] with its mask picture decoded (a software bitmap, the segmenter's grayscale PNG); null [mask] keeps everything. */
class PreparedCutOut(val cutOut: CutOut, val mask: ImageBitmap?)

/**
 * [picture] (the layer's whole upright picture, a software bitmap) with the background taken away: the mask stretched bilinearly to the
 * picture's size, the refine strokes drawn over it in saved order (white restores, black erases), the edge feathered at this size
 * ([CutOutAlpha.featherRadius]), and the result written into the picture's alpha. Null when the renderer can't make bitmaps.
 * The editor, the render hook and the export all come through here, so they can't disagree (spec P2-07 → What Apply does).
 */
suspend fun cutOutPicture(picture: SceneBitmap, prepared: PreparedCutOut, renderer: ImageToolRenderer): SceneBitmap? {
    val width = picture.image.width
    val height = picture.image.height
    val context = currentCoroutineContext()
    val pixels = withContext(Dispatchers.Default) {
        val levels = drawLevels(prepared, width, height).let { image -> IntArray(width * height).also { image.readPixels(it, 0, 0, width, height, 0, width) } }
        context.ensureActive()
        val colors = IntArray(width * height).also { picture.image.readPixels(it, 0, 0, width, height, 0, width) }
        CutOutAlpha.applyTo(colors, levels, width, height, CutOutAlpha.featherRadius(prepared.cutOut.softness, max(width, height))) { context.ensureActive() }
        colors
    }
    val image = renderer.bitmapOf(pixels, width, height) ?: return null
    return SceneBitmap(image, picture.sourceSize)
}

// The mask as gray levels at the picture's size: the mask, then each stroke on top of it.
private fun drawLevels(prepared: PreparedCutOut, width: Int, height: Int): ImageBitmap {
    val levels = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(levels), Size(width.toFloat(), height.toFloat())) {
        val mask = prepared.mask
        if (mask == null) {
            drawRect(Color.White)
        } else {
            drawImage(
                mask,
                srcSize = IntSize(mask.width, mask.height),
                dstSize = IntSize(width, height),
                filterQuality = FilterQuality.Low,
            )
        }
        prepared.cutOut.strokes.forEach { drawRefineStroke(it, width, height) }
    }
    return levels
}

/** One refine stroke over a picture [width] x [height]: points are thousandths of the longer side from the center. */
fun DrawScope.drawRefineStroke(stroke: Stroke, width: Int, height: Int) {
    val longSide = max(width, height).toFloat()
    val unit = longSide / DrawCatalog.UNITS_PER_S
    val color = if (stroke.mode == StrokeMode.ERASE) Color.Black else Color.White
    val diameter = stroke.widthS * longSide
    fun pointAt(index: Int) = Offset(width / 2f + stroke.points[2 * index] * unit, height / 2f + stroke.points[2 * index + 1] * unit)
    if (stroke.pointCount == 0) return
    if (stroke.pointCount == 1) {
        drawCircle(color, radius = diameter / 2f, center = pointAt(0))
        return
    }
    val path = Path().apply {
        moveTo(pointAt(0).x, pointAt(0).y)
        for (i in 1 until stroke.pointCount) lineTo(pointAt(i).x, pointAt(i).y)
    }
    drawPath(path, color, style = DrawStroke(diameter, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * The sticker of a finished cut-out: [cut] (the picture with its cut-out applied, nothing else) trimmed to where something is left and
 * scaled down so its longer side is at most [maxLongSidePx], as PNG bytes. Null when nothing is left to save, or when the renderer
 * can't encode (spec docs/ux/specs/P2-07-background-removal.md → Save as sticker).
 */
suspend fun stickerPng(cut: ImageBitmap, maxLongSidePx: Int, renderer: ImageToolRenderer): StickerPng {
    val width = cut.width
    val height = cut.height
    val trimmed = withContext(Dispatchers.Default) {
        val pixels = IntArray(width * height).also { cut.readPixels(it, 0, 0, width, height, 0, width) }
        val box = CutOutAlpha.opaqueBounds(pixels, width, height) ?: return@withContext null
        val boxWidth = box[2] - box[0]
        val boxHeight = box[3] - box[1]
        val scale = minOf(1f, maxLongSidePx.toFloat() / max(boxWidth, boxHeight))
        val outWidth = max(1, (boxWidth * scale).roundToInt())
        val outHeight = max(1, (boxHeight * scale).roundToInt())
        ImageBitmap(outWidth, outHeight).also { out ->
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(out), Size(outWidth.toFloat(), outHeight.toFloat())) {
                drawImage(
                    cut,
                    srcOffset = IntOffset(box[0], box[1]),
                    srcSize = IntSize(boxWidth, boxHeight),
                    dstSize = IntSize(outWidth, outHeight),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    } ?: return StickerPng.Empty
    val bytes = renderer.encodePng(trimmed) ?: return StickerPng.Failed
    return StickerPng.Ready(bytes)
}

sealed interface StickerPng {
    class Ready(val bytes: ByteArray) : StickerPng

    /** The cut-out has nothing left in it. */
    data object Empty : StickerPng

    data object Failed : StickerPng
}
