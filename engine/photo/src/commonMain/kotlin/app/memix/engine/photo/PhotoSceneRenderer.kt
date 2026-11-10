package app.memix.engine.photo

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.memix.core.domain.project.layers
import app.memix.core.domain.segmentation.SegmentationLimits
import app.memix.core.domain.text.PrepareCaptionsUseCase
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.ImageEdit
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.PanelsFormat
import app.memix.core.model.project.PhotoCatalog
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.StickerLayer
import app.memix.core.model.project.editOrNull
import app.memix.core.model.project.pictureZoom
import app.memix.core.model.project.fittedImageSize
import app.memix.core.model.project.shownSize
import app.memix.core.ui.scene.ImageToolRenderer
import app.memix.core.ui.scene.PreparedCutOut
import app.memix.core.ui.scene.SceneBitmap
import app.memix.core.ui.scene.SceneCaptions
import app.memix.core.ui.scene.SceneImageSource
import app.memix.core.ui.scene.SceneImageUse
import app.memix.core.ui.scene.SceneImages
import app.memix.core.ui.scene.drawPhotoScene
import app.memix.core.ui.scene.editedLongSidePx
import app.memix.core.ui.scene.renderEditedPicture
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A picture whose file is there but can't be decoded, or a cut-out or color pass that can't be made: the picture would be wrong, so nothing is rendered. */
class UnreadablePictureException(message: String) : Exception(message)

/**
 * Draws a saved photo project at an output size into one bitmap, through the one scene-drawing function the editor uses
 * (spec docs/ux/specs/P2-08-photo-export.md → What the file is). The export and the debug render hook both call it, so the
 * file equals what the hook renders, apart from the mark.
 *
 * Pictures are decoded software-side at the size they are drawn at on this output, never above [PhotoCatalog.MAX_DECODE_PX]. A layer with a cut-out or color tools gets
 * its processed picture made one layer at a time; when no other layer draws the same file, its full-size original is dropped
 * right after, so the peak is the plain pictures plus one original and its processed picture, not every original at once.
 * A picture whose file is missing is left out of the drawing, as in the editor.
 */
class PhotoSceneRenderer(
    private val imageSource: SceneImageSource,
    private val imageTools: ImageToolRenderer,
    private val captions: SceneCaptions,
    private val prepareCaptions: PrepareCaptionsUseCase,
    private val isFilePresent: (path: String) -> Boolean,
    private val drawDispatcher: CoroutineDispatcher,
) {
    /**
     * [project]'s scene at [output] pixels, with the "Memix" mark drawn last when [watermark]. Throws
     * [UnreadablePictureException] for a picture that is there but can't be read; out-of-memory errors pass through.
     */
    suspend fun render(project: Project, output: PixelSize, watermark: Boolean): ImageBitmap {
        val pictures = ScenePictures(imageSource, isFilePresent, decodeSizesOf(project, output))
        val processed = HashMap<String, SceneBitmap>()
        val visibleLayers = project.layers().filterNot { it.hidden }
        val edited = visibleLayers.filterIsInstance<ImageLayer>().filter { it.needsProcessing() }
        // Files something besides an edited layer draws: kept at full size.
        val plainPaths = plainPathsOf(project) + visibleLayers.filterIsInstance<ImageLayer>().filterNot { it.needsProcessing() }.mapNotNull { it.source.cachedCopyPath }
        edited.forEachIndexed { index, layer ->
            val picture = layer.source.cachedCopyPath?.let { pictures.load(it) } ?: return@forEachIndexed
            processed[layer.id] = processedPicture(project, layer, picture, output)
            val path = layer.source.cachedCopyPath ?: return@forEachIndexed
            val usedAgain = path in plainPaths || edited.drop(index + 1).any { it.source.cachedCopyPath == path }
            if (!usedAgain) pictures.shrinkToSize(path)
        }
        plainPaths.forEach { pictures.load(it) }
        // The caption fonts load before text layers draw (P2-03).
        prepareCaptions()
        val images = object : SceneImages {
            override fun processedOf(layerId: String): SceneBitmap? = processed[layerId]

            override fun imageOf(path: String): SceneBitmap? = pictures.get(path)

            override fun appearanceOf(path: String): Float = 1f
        }
        val bitmap = ImageBitmap(output.widthPx, output.heightPx)
        withContext(drawDispatcher) {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(output.widthPx.toFloat(), output.heightPx.toFloat())) {
                drawPhotoScene(project.canvas, project.layers(), images, captions = captions, format = project.photo?.format)
                if (watermark) captions.drawWatermark(this, output.widthPx, output.heightPx)
            }
        }
        return bitmap
    }

    // An edited layer: its cut-out (the mask decoded here) and color tools, made at the size it is drawn at on this output.
    private suspend fun processedPicture(project: Project, layer: ImageLayer, picture: SceneBitmap, output: PixelSize): SceneBitmap {
        val edit = layer.editOrNull()?.takeIf { it.hasColorEdits } ?: ImageEdit.NONE
        val canvasSize = PixelSize(project.canvas.widthPx, project.canvas.heightPx)
        val shown = edit.shownSize(layer.source.pixelSize ?: picture.sourceSize)
        val fitted = fittedImageSize(shown, canvasSize)
        val longSide = editedLongSidePx(max(fitted.width, fitted.height), layer.transform.scale, project.canvas.widthPx, output.widthPx, shown)
        val prepared = layer.cutOut?.let { cutOut ->
            val maskPath = cutOut.maskPath
            if (maskPath == null) {
                PreparedCutOut(cutOut, null)
            } else {
                val mask = imageSource.decode(maskPath, SegmentationLimits.INPUT_LONG_SIDE_PX, SceneImageUse.OFFSCREEN)
                    ?: throw UnreadablePictureException("A cut-out's mask can't be read")
                PreparedCutOut(cutOut, mask.image)
            }
        }
        val made = renderEditedPicture(picture, edit, longSide, imageTools, prepared)
            ?: throw UnreadablePictureException("The picture's cut-out or color tools can't be applied")
        return SceneBitmap(made, picture.sourceSize)
    }

    // Every file the scene draws as itself: sticker layers, the panels' cell photos and the background picture.
    private fun plainPathsOf(project: Project): Set<String> {
        val cellPhotos = (project.photo?.format as? PanelsFormat)?.cells?.mapNotNull { it.source }.orEmpty()
        val stickers = project.layers().filterNot { it.hidden }.filterIsInstance<StickerLayer>().map { it.source }
        val background = (project.canvas.background as? CanvasBackground.Image)?.source
        return (cellPhotos + stickers + listOfNotNull(background)).mapNotNull { it.cachedCopyPath }.toSet()
    }

    // The long side each picture is decoded at: its biggest image layer's drawn size on this output (the editor's rule, so the two
    // agree), and the full cap for a file a sticker, a cell or the background also draws. A 12 MP photo drawn at 1080 is
    // then decoded at about 1080, not 4096.
    private fun decodeSizesOf(project: Project, output: PixelSize): Map<String, Int> {
        val canvasSize = PixelSize(project.canvas.widthPx, project.canvas.heightPx)
        val sizes = HashMap<String, Int>()
        plainPathsOf(project).forEach { sizes[it] = PhotoCatalog.MAX_DECODE_PX }
        for (layer in project.layers().filterNot { it.hidden }.filterIsInstance<ImageLayer>()) {
            val path = layer.source.cachedCopyPath ?: continue
            val picture = layer.source.pixelSize
            val needed = if (picture == null) {
                PhotoCatalog.MAX_DECODE_PX
            } else {
                val edit = layer.editOrNull() ?: ImageEdit.NONE
                val fitted = fittedImageSize(edit.shownSize(picture), canvasSize)
                val drawn = max(fitted.width, fitted.height) * layer.transform.scale * output.widthPx / project.canvas.widthPx
                // The crop shows a part of the picture, so the whole picture is decoded bigger by the crop's zoom.
                (drawn * edit.pictureZoom(picture)).roundToInt().coerceIn(1, PhotoCatalog.MAX_DECODE_PX)
            }
            sizes[path] = max(sizes[path] ?: 0, needed)
        }
        return sizes
    }

    private fun ImageLayer.needsProcessing(): Boolean = cutOut != null || editOrNull()?.hasColorEdits == true
}

// The decoded originals by file, loaded when first asked for.
private class ScenePictures(
    private val imageSource: SceneImageSource,
    private val isFilePresent: (String) -> Boolean,
    private val longSides: Map<String, Int>,
) {
    private val loaded = HashMap<String, SceneBitmap>()

    fun get(path: String): SceneBitmap? = loaded[path]

    /** The picture of [path], decoded once; null when its file is missing (left out of the scene). */
    suspend fun load(path: String): SceneBitmap? {
        loaded[path]?.let { return it }
        val decoded = imageSource.decode(path, longSides[path] ?: PhotoCatalog.MAX_DECODE_PX, SceneImageUse.OFFSCREEN)
        if (decoded == null) {
            if (isFilePresent(path)) throw UnreadablePictureException("A picture can't be read")
            return null
        }
        loaded[path] = decoded
        return decoded
    }

    /** Frees [path]'s pixels but keeps its size, which the scene reads for a layer drawn from its processed picture. */
    fun shrinkToSize(path: String) {
        val original = loaded[path] ?: return
        loaded[path] = SceneBitmap(ImageBitmap(1, 1), original.sourceSize)
    }
}
