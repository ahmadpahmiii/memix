package app.memix.core.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import app.memix.core.model.project.BubbleLayer
import app.memix.core.model.project.BubblePoint
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.ImageEdit
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.Layer
import app.memix.core.model.project.PhotoFormat
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.StickerLayer
import app.memix.core.model.project.TextLayer
import app.memix.core.model.project.Transform
import app.memix.core.model.project.coveredImageSize
import app.memix.core.model.project.editOrNull
import app.memix.core.model.project.shownSize
import app.memix.core.model.project.straightenScale
import app.memix.core.model.project.turnedSize
import app.memix.core.model.project.fittedImageSize
import app.memix.core.model.project.stickerSizeAtScaleOne

/**
 * Values that differ from the project while a finger or a slider is down: read while drawing, so the scene redraws without
 * recomposing (spec P2-01 → Gestures: during a gesture only the drawn layer moves, in the draw phase).
 */
interface SceneLive {
    fun transformOf(layerId: String): Transform?

    fun opacityOf(layerId: String): Float?

    /** The format as a finger or a held arrow has it now (a divider, a cell's view, a bubble's tail), or null for the project's own. */
    fun formatOf(): PhotoFormat? = null

    /** A bubble's tail tip as the finger has it now, or null for the project's own. */
    fun tailOf(layerId: String): BubblePoint? = null
}

/**
 * THE one function that draws a photo scene, at any pixel size: the editor at screen size, the render hook and (P2-08)
 * export at theirs, so preview and export can't disagree. The scene fills this [DrawScope]'s whole size, with [canvas]'s
 * shape; layers draw bottom to top, hidden ones skipped, each with its opacity and transform, clipped at the canvas edge.
 * The format's parts (a bar, a poster and frame, panels' cells) are drawn after the background and under the layers (spec P2-02 → How a format works); [format] is the scene's. A layer whose picture isn't ready or whose file is missing isn't drawn. A bubble is one path, then its words. A text layer is drawn by [captions] (the video's caption
 * renderer, laid out once in canvas pixels), a sticker like an image layer at its own size at scale 1 (35% of the shorter side). A background picture (P2-12) is drawn first, to
 * cover the canvas; while it isn't ready or when its file is missing the canvas is black.
 */
fun DrawScope.drawPhotoScene(
    canvas: Canvas,
    layers: List<Layer>,
    images: SceneImages,
    live: SceneLive? = null,
    captions: SceneCaptions = NoSceneCaptions,
    format: PhotoFormat? = null,
) {
    val canvasSize = PixelSize(canvas.widthPx, canvas.heightPx)
    val pixelScale = size.width / canvas.widthPx
    clipRect(0f, 0f, size.width, size.height) {
        drawRect(backgroundOf(canvas))
        withTransform({ scale(pixelScale, pixelScale, Offset.Zero) }) {
            (canvas.background as? CanvasBackground.Image)?.let { drawBackgroundImage(it, canvasSize, images) }
            drawFormatParts(live?.formatOf() ?: format, canvasSize, images)
            for (layer in layers) {
                if (layer.hidden) continue
                val transform = live?.transformOf(layer.id) ?: layer.transform
                val opacity = live?.opacityOf(layer.id) ?: layer.opacity
                when (layer) {
                    is ImageLayer -> drawImageLayer(layer, transform, opacity, canvasSize, images)
                    is TextLayer -> captions.draw(this, layer.text, layer.style, transform, opacity, canvasSize.widthPx, canvasSize.heightPx)
                    is StickerLayer -> drawStickerLayer(layer, transform, opacity, canvasSize, images)
                    is BubbleLayer -> drawBubbleLayer(layer, transform, opacity, live?.tailOf(layer.id), canvasSize, captions)
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

// A sticker: its size at scale 1 is 35% of the shorter side on its longer side (never fitted inside the frame), else as an image.
private fun DrawScope.drawStickerLayer(layer: StickerLayer, transform: Transform, opacity: Float, canvasSize: PixelSize, images: SceneImages) {
    val path = layer.source.cachedCopyPath ?: return
    val picture = images.imageOf(path) ?: return
    val image = picture.image
    val size = stickerSizeAtScaleOne(layer.source.pixelSize ?: picture.sourceSize, canvasSize)
    drawPicture(image, size.width * transform.scale, size.height * transform.scale, transform, opacity * images.appearanceOf(path), canvasSize)
}

// Placed in canvas pixels: the picture (turned and cropped by its edit) fitted inside the canvas at scale 1, times the layer's scale,
// turned about its center. The whole picture is drawn under a clip at the crop's edges (spec P2-04 → How it draws).
private fun DrawScope.drawImageLayer(layer: ImageLayer, transform: Transform, opacity: Float, canvasSize: PixelSize, images: SceneImages) {
    val path = layer.source.cachedCopyPath ?: return
    val picture = images.imageOf(path) ?: return
    val shown = layer.source.pixelSize ?: picture.sourceSize
    val alpha = opacity * images.appearanceOf(path)
    val edit = layer.editOrNull()
    if (edit == null || !edit.changesGeometry) {
        val fitted = fittedImageSize(shown, canvasSize)
        drawPicture(picture.image, fitted.width * transform.scale, fitted.height * transform.scale, transform, alpha, canvasSize)
        return
    }
    drawEditedPicture(picture.image, shown, edit, transform, alpha, canvasSize)
}

// The layer's picture: centered at the transform's center and turned about it, filling a box of the cropped shape.
private fun DrawScope.drawEditedPicture(image: ImageBitmap, source: PixelSize, edit: ImageEdit, transform: Transform, alpha: Float, canvasSize: PixelSize) {
    val fitted = fittedImageSize(edit.shownSize(source), canvasSize)
    withTransform({
        translate(transform.centerX * canvasSize.widthPx, transform.centerY * canvasSize.heightPx)
        rotate(transform.rotationDegrees, Offset.Zero)
    }) {
        drawPictureWithEdit(image, source, edit, fitted.width * transform.scale, fitted.height * transform.scale, alpha)
    }
}

/**
 * [image] (the whole picture, [source] pixels as shown) with [edit]'s turn, mirrors, straighten and crop, filling a box
 * [boxWidth] × [boxHeight] centered on the origin of this scope: the part inside the crop fills the box and the rest is
 * clipped away. The scene's image layers and the Crop screen (which draws with a full crop) both use it, so the picture the
 * user shapes is the picture the scene draws. Order of the picture's transforms: quarter turns, mirrors, straighten (scaled up
 * to keep covering the frame), crop (spec P2-04 → How it draws).
 */
fun DrawScope.drawPictureWithEdit(image: ImageBitmap, source: PixelSize, edit: ImageEdit, boxWidth: Float, boxHeight: Float, alpha: Float = 1f) {
    val turned = edit.turnedSize(source)
    // Canvas pixels per source pixel.
    val unit = boxWidth / (turned.widthPx * edit.crop.width)
    val frameWidth = turned.widthPx * unit
    val frameHeight = turned.heightPx * unit
    val straightenZoom = if (edit.straighten != 0f) straightenScale(edit.straighten, turned.widthPx.toFloat() / turned.heightPx) else 1f
    clipRect(-boxWidth / 2f, -boxHeight / 2f, boxWidth / 2f, boxHeight / 2f) {
        withTransform({
            translate(-((edit.crop.left + edit.crop.right) / 2f - 0.5f) * frameWidth, -((edit.crop.top + edit.crop.bottom) / 2f - 0.5f) * frameHeight)
            rotate(edit.straighten, Offset.Zero)
            scale(straightenZoom, straightenZoom, Offset.Zero)
            scale(if (edit.flipH) -1f else 1f, if (edit.flipV) -1f else 1f, Offset.Zero)
            rotate(edit.quarterTurns * QUARTER_TURN_DEGREES, Offset.Zero)
            scale(unit * source.widthPx / image.width, unit * source.heightPx / image.height, Offset.Zero)
            translate(-image.width / 2f, -image.height / 2f)
        }) {
            drawImage(
                image,
                srcSize = IntSize(image.width, image.height),
                dstSize = IntSize(image.width, image.height),
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

private const val QUARTER_TURN_DEGREES = 90f

// [image] drawn [width] × [height] canvas pixels big, centered at the transform's center and turned about it.
private fun DrawScope.drawPicture(image: ImageBitmap, width: Float, height: Float, transform: Transform, alpha: Float, canvasSize: PixelSize) {
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
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
    }
}
