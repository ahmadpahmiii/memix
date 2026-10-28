package app.memix.core.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.Layer
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.StickerLayer
import app.memix.core.model.project.TextLayer
import app.memix.core.model.project.Transform
import app.memix.core.model.project.coveredImageSize
import app.memix.core.model.project.fittedImageSize

/**
 * Values that differ from the project while a finger or a slider is down: read while drawing, so the scene redraws without
 * recomposing (spec P2-01 → Gestures: during a gesture only the drawn layer moves, in the draw phase).
 */
interface SceneLive {
    fun transformOf(layerId: String): Transform?

    fun opacityOf(layerId: String): Float?
}

/**
 * THE one function that draws a photo scene, at any pixel size: the editor at screen size, the render hook and (P2-08)
 * export at theirs, so preview and export can't disagree. The scene fills this [DrawScope]'s whole size, with [canvas]'s
 * shape; layers draw bottom to top, hidden ones skipped, each with its opacity and transform, clipped at the canvas edge.
 * A layer whose picture isn't ready or whose file is missing isn't drawn. A background picture (P2-12) is drawn first, to
 * cover the canvas; while it isn't ready or when its file is missing the canvas is black.
 */
fun DrawScope.drawPhotoScene(canvas: Canvas, layers: List<Layer>, images: SceneImages, live: SceneLive? = null) {
    val canvasSize = PixelSize(canvas.widthPx, canvas.heightPx)
    val pixelScale = size.width / canvas.widthPx
    clipRect(0f, 0f, size.width, size.height) {
        drawRect(backgroundOf(canvas))
        withTransform({ scale(pixelScale, pixelScale, Offset.Zero) }) {
            (canvas.background as? CanvasBackground.Image)?.let { drawBackgroundImage(it, canvasSize, images) }
            for (layer in layers) {
                if (layer.hidden) continue
                val transform = live?.transformOf(layer.id) ?: layer.transform
                val opacity = live?.opacityOf(layer.id) ?: layer.opacity
                when (layer) {
                    is ImageLayer -> drawImageLayer(layer, transform, opacity, canvasSize, images)
                    // TODO(P2-03): text and stickers draw here, with the same caption renderer and sticker bitmaps as the video.
                    is TextLayer, is StickerLayer -> Unit
                }
            }
        }
    }
}

// The canvas color. Blur is video only (a copy of the playing clip): a photo draws white where one would be. A picture
// is drawn over black by [drawBackgroundImage], so a missing file shows black, as in the video export.
private fun backgroundOf(canvas: Canvas): Color = when (val background = canvas.background) {
    is CanvasBackground.Solid -> Color(background.color.argb.toInt())
    CanvasBackground.Blur -> Color.White
    is CanvasBackground.Image -> Color.Black
}

// Covers the canvas, centered; the clip at the canvas edge cuts the overflow (spec P2-12 → How it draws).
private fun DrawScope.drawBackgroundImage(background: CanvasBackground.Image, canvasSize: PixelSize, images: SceneImages) {
    val path = background.source.cachedCopyPath ?: return
    val picture = images.imageOf(path) ?: return
    val image = picture.image
    val covered = coveredImageSize(background.source.pixelSize ?: picture.sourceSize, canvasSize)
    withTransform({
        translate(canvasSize.widthPx / 2f, canvasSize.heightPx / 2f)
        scale(covered.width / image.width, covered.height / image.height, Offset.Zero)
        translate(-image.width / 2f, -image.height / 2f)
    }) {
        drawImage(
            image,
            srcSize = IntSize(image.width, image.height),
            dstSize = IntSize(image.width, image.height),
            alpha = images.appearanceOf(path),
            filterQuality = FilterQuality.Medium,
        )
    }
}

// Placed in canvas pixels: the picture's fitted size at scale 1, times the layer's scale, turned about its center.
private fun DrawScope.drawImageLayer(layer: ImageLayer, transform: Transform, opacity: Float, canvasSize: PixelSize, images: SceneImages) {
    val path = layer.source.cachedCopyPath ?: return
    val picture = images.imageOf(path) ?: return
    val image = picture.image
    val shown = layer.source.pixelSize ?: picture.sourceSize
    val fitted = fittedImageSize(shown, canvasSize)
    val width = fitted.width * transform.scale
    val height = fitted.height * transform.scale
    withTransform({
        translate(transform.centerX * canvasSize.widthPx, transform.centerY * canvasSize.heightPx)
        rotate(transform.rotationDegrees, Offset.Zero)
        scale(width / image.width, height / image.height, Offset.Zero)
        translate(-image.width / 2f, -image.height / 2f)
    }) {
        drawImage(
            image,
            srcSize = IntSize(image.width, image.height),
            dstSize = IntSize(image.width, image.height),
            alpha = opacity * images.appearanceOf(path),
            filterQuality = FilterQuality.Medium,
        )
    }
}
