package app.memix.engine.video

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import app.memix.core.model.project.WatermarkCatalog
import kotlin.math.min

/**
 * The "Memix" mark on a free export (spec docs/ux/specs/P1-13-watermark.md): the top-most overlay of the composition,
 * for the whole video, drawn with the caption renderer's Anton. Only exports get one; the preview never does. The
 * picture never changes, so it is drawn once per frame size and the uploaded texture is reused.
 *
 * Sized and placed from the frame's shorter side ([WatermarkCatalog]); the left and top margins are measured to the
 * glyphs' ink box, so the mark sits where the spec says whatever Anton's ascent is.
 */
@OptIn(UnstableApi::class)
internal class WatermarkOverlay(private val renderer: AndroidCaptionRenderer) : CanvasOverlay(/* useInputFrameSize= */ true) {
    private var drawnWidth = 0
    private var drawnHeight = 0

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        if (canvas.width == drawnWidth && canvas.height == drawnHeight) return
        drawnWidth = canvas.width
        drawnHeight = canvas.height
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        val shortSidePx = min(canvas.width, canvas.height)
        val fontSizePx = WatermarkCatalog.fontSizePx(shortSidePx)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = renderer.typefaceOf(WatermarkCatalog.FONT_ID)
            textSize = fontSizePx
            color = WatermarkCatalog.FILL_ARGB.toInt()
            // No offset: a soft glow that lifts the mark off bright footage without an edge.
            setShadowLayer(fontSizePx * WatermarkCatalog.SHADOW_BLUR_OF_FONT_SIZE, 0f, 0f, WatermarkCatalog.SHADOW_ARGB.toInt())
        }
        val ink = Rect()
        paint.getTextBounds(WatermarkCatalog.TEXT, 0, WatermarkCatalog.TEXT.length, ink)
        // Text bounds are relative to the baseline and the draw origin, so the origin is the margin minus the ink's offset.
        val x = WatermarkCatalog.marginLeftPx(shortSidePx) - ink.left
        val baseline = WatermarkCatalog.marginTopPx(shortSidePx) - ink.top
        canvas.drawText(WatermarkCatalog.TEXT, x, baseline, paint)
    }
}
