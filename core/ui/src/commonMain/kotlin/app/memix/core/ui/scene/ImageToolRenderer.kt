package app.memix.core.ui.scene

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.memix.core.model.project.ImageEdit
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.shownSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Applies the image tools' color pipeline (adjustments, a filter, deep-fry: [app.memix.core.domain.image.ImagePipeline]) to a
 * picture that already has its geometry (spec docs/ux/specs/P2-04-image-tools.md → How it draws). Implemented in `:engine:photo`
 * with the platform's pixel access and bound in the composition root, so no feature touches pixels. The same math runs on every
 * Android version (a CPU pass on the picture at its drawn size; the GL and AGSL paths of the spec's request 2 are not built, see
 * TECHNICAL_DESIGN → Decisions); iOS reports [isSupported] false until P7-09, never a silent no-op.
 */
interface ImageToolRenderer {
    /** False where the color tools can't run (iOS until P7-09): the editor explains instead of showing dead controls. */
    val isSupported: Boolean

    /**
     * [picture] (a software bitmap: its pixels can be read) with [edit]'s color tools applied, a new bitmap of the same size; null when
     * unsupported or when it can't be done. Off the main thread; cancellable.
     */
    suspend fun process(picture: ImageBitmap, edit: ImageEdit): ImageBitmap?

    /** A software bitmap of [width] x [height] from ARGB [pixels] (not premultiplied), for the cut-out's alpha; null when unsupported. */
    suspend fun bitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap?

    /** [image] as PNG bytes, keeping its alpha (a saved sticker); null when unsupported or when it can't be encoded. */
    suspend fun encodePng(image: ImageBitmap): ByteArray?
}

/** The iOS binding until P7-09 (and the harness's default): nothing can be processed. */
object UnsupportedImageToolRenderer : ImageToolRenderer {
    override val isSupported = false

    override suspend fun process(picture: ImageBitmap, edit: ImageEdit): ImageBitmap? = null

    override suspend fun bitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap? = null

    override suspend fun encodePng(image: ImageBitmap): ByteArray? = null
}

/**
 * [picture] (a software bitmap of the whole picture, [SceneBitmap.sourceSize] as shown) shaped by [edit]'s geometry (turn, mirrors,
 * straighten, crop) at the size whose longer side is at most [longSidePx], then color-processed by [renderer]: the picture the
 * scene draws for an edited layer. The editor, the render hook and the export (P2-08) all make it here, so they can't disagree.
 * Null when the renderer can't process. [cutOut], when the layer has one, is applied to the picture before any of that.
 */
suspend fun renderEditedPicture(picture: SceneBitmap, edit: ImageEdit, longSidePx: Int, renderer: ImageToolRenderer, cutOut: PreparedCutOut? = null): ImageBitmap? {
    if (!renderer.isSupported) return null
    // The cut-out is taken from the whole upright picture first, so crop, turn and the color tools act on the cut picture and keep its alpha (spec P2-07).
    val picture = if (cutOut == null) picture else cutOutPicture(picture, cutOut, renderer) ?: return null
    val shown = edit.shownSize(picture.sourceSize)
    val scale = min(1f, longSidePx.toFloat() / max(shown.widthPx, shown.heightPx))
    val width = max(1, (shown.widthPx * scale).roundToInt())
    val height = max(1, (shown.heightPx * scale).roundToInt())
    // A software canvas draw of up to a few million pixels: off the main thread, as the color pass after it.
    val shaped = withContext(Dispatchers.Default) {
        ImageBitmap(width, height).also { bitmap ->
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                withTransform({ translate(width / 2f, height / 2f) }) {
                    drawPictureWithEdit(picture.image, picture.sourceSize, edit, width.toFloat(), height.toFloat())
                }
            }
        }
    }
    return renderer.process(shaped, edit)
}

/**
 * How many pixels the longer side of an edited layer's picture is drawn at on an output [outputWidthPx] wide: its fitted box times
 * its scale, never more than the cropped picture has. The decoder and [renderEditedPicture] use it so a processed picture is as big
 * as it is drawn.
 */
fun editedLongSidePx(boxLongSideAtScaleOne: Float, scale: Float, canvasWidthPx: Int, outputWidthPx: Int, shown: PixelSize): Int {
    val drawn = boxLongSideAtScaleOne * scale * outputWidthPx / canvasWidthPx
    return min(drawn.roundToInt(), max(shown.widthPx, shown.heightPx)).coerceAtLeast(1)
}
