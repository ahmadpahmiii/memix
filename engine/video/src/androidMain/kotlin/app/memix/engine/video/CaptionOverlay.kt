package app.memix.engine.video

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.PorterDuff
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay

/**
 * The captions of a composition, drawn with [AndroidCaptionRenderer] into one frame-sized overlay on the composited
 * video (a composition-level effect, so it sees composition time and a caption spans clip cuts; Media3 1.11.1 adds each
 * item's offset before any effect, in CompositionPlayer and Transformer alike). The picture is redrawn only when the
 * set of captions shown changes, so most frames reuse the uploaded texture.
 *
 * The preview hides the caption being edited ([visibility]), which the editor's stand-in draws instead.
 */
@OptIn(UnstableApi::class)
internal class CaptionOverlay(
    private val captions: List<CaptionClip>,
    renderer: AndroidCaptionRenderer,
    private val canvasWidthPx: Int,
    private val canvasHeightPx: Int,
    private val visibility: CaptionVisibility = CaptionVisibility(),
) : CanvasOverlay(/* useInputFrameSize= */ true) {
    // Laid out once, when the composition is built (off the main thread).
    private val layouts = captions.associate { it.itemId to renderer.layout(it.text, it.style, canvasWidthPx, canvasHeightPx) }
    private var drawnIds: List<String>? = null
    private var drawnWidth = 0
    private var drawnHeight = 0

    // core-ktx's withScale isn't a declared dependency of this module; save and restore do the same.
    @SuppressLint("UseKtx")
    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val hidden = visibility.hiddenItemId
        val shown = captions.filter { it.isShownAt(presentationTimeUs) && it.itemId != hidden }
        val shownIds = shown.map { it.itemId }
        if (shownIds == drawnIds && canvas.width == drawnWidth && canvas.height == drawnHeight) return
        drawnIds = shownIds
        drawnWidth = canvas.width
        drawnHeight = canvas.height
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        canvas.save()
        // The frame may be smaller than the canvas (a lower export resolution): the layout stays, only the scale changes.
        canvas.scale(canvas.width / canvasWidthPx.toFloat(), canvas.height / canvasHeightPx.toFloat())
        for (caption in shown) layouts.getValue(caption.itemId).draw(canvas, caption.transform)
        canvas.restore()
    }
}

/**
 * Which caption the preview leaves out while the editor's stand-in draws it (a caption being typed or moved). Written
 * on the main thread, read on the GL thread.
 */
internal class CaptionVisibility {
    @Volatile
    var hiddenItemId: String? = null
}
