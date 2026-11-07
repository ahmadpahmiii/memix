package app.memix.core.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import app.memix.core.model.project.CensorCatalog
import app.memix.core.model.project.CensorLayer
import app.memix.core.model.project.CensorMode
import app.memix.core.model.project.CensorShape
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Transform
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * How a censor draws (spec docs/ux/specs/P2-05-draw-and-censor.md → The censor layer). A backdrop effect on every API with no
 * RenderEffect or shader: what is drawn under the censor is drawn again, at the size the effect needs, into a small bitmap
 * that is then enlarged back over the area, clipped to its shape. Pixelate averages each block by halving (an exact 2 x 2 mean per
 * step) from a bitmap at most 16 pixels per block; Blur does the same with a cell of two sigma and enlarges smoothly, so
 * it is a downscaled copy that has lost the detail. The pixelate grid is anchored at the canvas's top-left in units of S, so it is
 * the same at any size. Called only from drawPhotoScene: the editor, the render hook and export draw it the same way.
 */

/** [drawBelow] draws everything under the censor in canvas pixels (the scene so far); it is called for the offscreen copy. */
internal fun DrawScope.drawCensorLayer(layer: CensorLayer, transform: Transform, canvasSize: PixelSize, drawBelow: DrawScope.() -> Unit) {
    val shortSide = min(canvasSize.widthPx, canvasSize.heightPx).toFloat()
    val width = layer.widthS * shortSide * transform.scale
    val height = layer.heightS * shortSide * transform.scale
    val center = Offset(transform.centerX * canvasSize.widthPx, transform.centerY * canvasSize.heightPx)
    val shape = shapePath(layer.shape, width, height, center, transform.rotationDegrees)
    if (layer.mode == CensorMode.BAR) {
        drawPath(shape, Color.Black)
        return
    }
    val areaS = CensorCatalog.shorterSideS(layer.widthS, layer.heightS, transform.scale)
    val radians = transform.rotationDegrees * (kotlin.math.PI.toFloat() / 180f)
    val halfX = (abs(cos(radians)) * width + abs(sin(radians)) * height) / 2f
    val halfY = (abs(sin(radians)) * width + abs(cos(radians)) * height) / 2f
    val pixelate = layer.mode == CensorMode.PIXELATE
    val cell = (if (pixelate) CensorCatalog.blockS(areaS, layer.strength) else CensorCatalog.sigmaS(areaS, layer.strength) * BLUR_CELL_PER_SIGMA) * shortSide
    // Pixelate covers whole blocks of the canvas's grid; Blur reaches one cell (two sigma) past the shape so its edge blurs the content outside.
    val reach = if (pixelate) 0f else cell
    val left = if (pixelate) max(0f, floor((center.x - halfX) / cell) * cell) else center.x - halfX - reach
    val top = if (pixelate) max(0f, floor((center.y - halfY) / cell) * cell) else center.y - halfY - reach
    val right = if (pixelate) ceil((center.x + halfX) / cell) * cell else center.x + halfX + reach
    val bottom = if (pixelate) ceil((center.y + halfY) / cell) * cell else center.y + halfY + reach
    val columns = max(1, ceil((right - left) / cell - EPSILON).toInt())
    val rows = max(1, ceil((bottom - top) / cell - EPSILON).toInt())
    val small = averagedBackdrop(left, top, cell, columns, rows, drawBelow)
    clipPath(shape) {
        withTransform({
            translate(left, top)
            scale(cell, cell, Offset.Zero)
        }) {
            drawImage(
                small,
                srcSize = IntSize(columns, rows),
                dstSize = IntSize(columns, rows),
                filterQuality = if (pixelate) FilterQuality.None else FilterQuality.Low,
            )
        }
    }
}

// The scene under the censor over the box from ([left], [top]), [columns] x [rows] cells of [cell] canvas pixels, one pixel per cell:
// drawn at up to 16 pixels a cell, then halved (a bilinear draw at exactly half size is the mean of 2 x 2) down to one.
private fun DrawScope.averagedBackdrop(left: Float, top: Float, cell: Float, columns: Int, rows: Int, drawBelow: DrawScope.() -> Unit): ImageBitmap {
    var perCell = 1
    while (perCell * 2 <= min(cell, MAX_PIXELS_PER_CELL.toFloat())) perCell *= 2
    var current = ImageBitmap(columns * perCell, rows * perCell)
    CanvasDrawScope().draw(this, layoutDirection, Canvas(current), Size(current.width.toFloat(), current.height.toFloat())) {
        withTransform({
            scale(perCell / cell, perCell / cell, Offset.Zero)
            translate(-left, -top)
        }) { drawBelow() }
    }
    while (perCell > 1) {
        perCell /= 2
        val halved = ImageBitmap(columns * perCell, rows * perCell)
        CanvasDrawScope().draw(this, layoutDirection, Canvas(halved), Size(halved.width.toFloat(), halved.height.toFloat())) {
            withTransform({ scale(0.5f, 0.5f, Offset.Zero) }) {
                drawImage(current, srcSize = IntSize(current.width, current.height), dstSize = IntSize(current.width, current.height), filterQuality = FilterQuality.Low)
            }
        }
        current = halved
    }
    return current
}

private fun shapePath(shape: CensorShape, width: Float, height: Float, center: Offset, rotationDegrees: Float): Path {
    val box = Rect(-width / 2f, -height / 2f, width / 2f, height / 2f)
    val path = Path().apply { if (shape == CensorShape.ELLIPSE) addOval(box) else addRect(box) }
    path.transform(Matrix().apply { translate(center.x, center.y); rotateZ(rotationDegrees) })
    return path
}

private const val MAX_PIXELS_PER_CELL = 16

// A box average over one cell (variance cell^2 / 12) read back with a linear tent (cell^2 / 6) blurs like a Gaussian of sigma cell / 2,
// so the cell is two sigma: a cell of one sigma would blur only about half as much as the minimum the spec promises.
private const val BLUR_CELL_PER_SIGMA = 2f
private const val EPSILON = 0.001f
