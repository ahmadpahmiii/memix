package app.memix.engine.video

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.PorterDuff
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import app.memix.core.model.project.Transform

/**
 * The captions of a composition, drawn with [AndroidCaptionRenderer] into one frame-sized overlay on the composited
 * video (a composition-level effect, so it sees composition time and a caption spans clip cuts; Media3 1.11.1 adds each
 * item's offset before any effect, in CompositionPlayer and Transformer alike). The picture is redrawn only when the
 * set of captions shown, or where one is drawn (keyframes, P4-02), changes, so most frames reuse the uploaded texture. A keyframed caption
 * costs one full-frame redraw and upload per frame while it moves: measure it on the phone (hand check P4-02a).
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
    // What the last picture shows: each caption with where it was drawn, so a keyframed caption redraws as it moves and a still one doesn't (P4-02).
    private var hasDrawn = false
    private val drawnIds = ArrayList<String>()
    private val drawnTransforms = ArrayList<Transform>()

    // Refilled each frame, so asking "did anything change" allocates no list.
    private val shownIds = ArrayList<String>()
    private val shownTransforms = ArrayList<Transform>()
    private var drawnWidth = 0
    private var drawnHeight = 0

    // core-ktx's withScale isn't a declared dependency of this module; save and restore do the same.
    @SuppressLint("UseKtx")
    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val hidden = visibility.hiddenItemId
        shownIds.clear()
        shownTransforms.clear()
        for (caption in captions) {
            if (!caption.isShownAt(presentationTimeUs) || caption.itemId == hidden) continue
            shownIds.add(caption.itemId)
            shownTransforms.add(caption.transformAt(presentationTimeUs))
        }
        if (hasDrawn && shownIds == drawnIds && shownTransforms == drawnTransforms && canvas.width == drawnWidth && canvas.height == drawnHeight) return
        hasDrawn = true
        drawnIds.clear()
        drawnIds.addAll(shownIds)
        drawnTransforms.clear()
        drawnTransforms.addAll(shownTransforms)
        drawnWidth = canvas.width
        drawnHeight = canvas.height
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        canvas.save()
        // The frame may be smaller than the canvas (a lower export resolution): the layout stays, only the scale changes.
        canvas.scale(canvas.width / canvasWidthPx.toFloat(), canvas.height / canvasHeightPx.toFloat())
        for (index in shownIds.indices) layouts.getValue(shownIds[index]).draw(canvas, shownTransforms[index])
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

/**
 * Which overlay the preview leaves out while the editor's stand-in follows a finger (P4-01b): the compositor draws it at alpha 0.
 * Written on the main thread, read on the GL thread, like [CaptionVisibility].
 */
internal class OverlayVisibility {
    @Volatile
    var hiddenItemId: String? = null

    /** The overlay the Opacity slider is moving with the opacity it draws at meanwhile, set as one value so the GL thread never reads half of it; null when none. */
    @Volatile
    var liveOpacity: LiveOpacity? = null
}

/** [itemId] drawn at [opacity] while the Opacity slider moves. */
internal class LiveOpacity(val itemId: String, val opacity: Float)
