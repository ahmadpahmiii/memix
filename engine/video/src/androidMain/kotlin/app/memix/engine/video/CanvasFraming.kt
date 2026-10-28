package app.memix.engine.video

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.GaussianBlurWithFrameOverlaid
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import app.memix.core.model.project.hasSameShape
import app.memix.core.model.project.CanvasCatalog
import app.memix.core.model.project.FrameFit
import app.memix.core.model.project.PixelSize
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The video effects that draw main clip [clip] into a [frame]-sized picture by the canvas rules (spec P1-11 → Rendering
 * rules), the same in the preview and the export:
 * - **Fill:** scaled to cover the frame, centered, the overflow cropped.
 * - **Fit:** scaled to show whole, centered; a clip within 1% of the frame's shape is stretched that last bit instead,
 *   so no hairline bars show.
 * - **Color background:** two bars of the color over the transparent space Fit leaves (Presentation leaves it black
 *   with alpha 0, which shows as black when the background is black).
 * - **Image background (P2-12):** the same two bars, each showing its part of the picture scaled to cover the whole frame
 *   ([BackgroundBars]); a picture that can't be read leaves the bars black.
 * - **Blur background:** Media3's GaussianBlurWithFrameOverlaid (1.11.1) draws the fitted clip sharp over a blurred,
 *   stretched copy of itself. Stretched by the same factor both ways, that copy is the Fill copy once the result is
 *   cropped to the frame; the sigma is given in the fitted clip's pixels, so it comes out at 3% of the frame's shorter
 *   side after the stretch.
 *
 * Gaps and missing clips aren't clips, so they stay black whatever the background. A clip of unknown size (media added
 * before schema 2) is fitted with no background around it.
 */
@OptIn(UnstableApi::class)
internal class CanvasFraming(
    private val clip: VisualClip,
    private val frame: PixelSize,
    private val background: PlanBackground,
    /** The background picture's bars; null unless [background] is an image. */
    private val pictureBars: BackgroundBars? = null,
) {
    fun effects(): List<Effect> {
        if (clip.fit == FrameFit.FILL) return listOf(presentation(frame, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
        val picture = clip.pixelSize?.takeIf { it.widthPx > 0 && it.heightPx > 0 }
            ?: return listOf(presentation(frame, Presentation.LAYOUT_SCALE_TO_FIT))
        if (hasSameShape(picture, frame)) return listOf(presentation(frame, Presentation.LAYOUT_STRETCH_TO_FIT))
        val geometry = FitGeometry(picture, frame)
        return when (background) {
            PlanBackground.Blur -> blurredFill(geometry)
            is PlanBackground.Color -> fittedOn(background.argb, geometry)
            is PlanBackground.Image -> fittedOnPicture(geometry)
        }
    }

    private fun fittedOn(argb: Long, geometry: FitGeometry): List<Effect> {
        val fitted = presentation(frame, Presentation.LAYOUT_SCALE_TO_FIT)
        if (argb == BLACK_ARGB) return listOf(fitted)
        return listOf(fitted, OverlayEffect(colorBars(argb, geometry)))
    }

    // The bars show the picture where it lies in the frame: each bar is the part of the covered picture at the bar's place, drawn
    // at the bar's size and anchored to the frame's edge, like the color bars. No picture: the bars stay black.
    private fun fittedOnPicture(geometry: FitGeometry): List<Effect> {
        val fitted = presentation(frame, Presentation.LAYOUT_SCALE_TO_FIT)
        val bars = pictureBars ?: return listOf(fitted)
        val thickness = geometry.barThickness
        val barPixels = thickness.roundToInt().coerceAtLeast(1)
        val areas = if (geometry.barsAtSides) {
            listOf(Rect(0, 0, barPixels, frame.heightPx) to (-1f to 0f), Rect(frame.widthPx - barPixels, 0, frame.widthPx, frame.heightPx) to (1f to 0f))
        } else {
            listOf(Rect(0, frame.heightPx - barPixels, frame.widthPx, frame.heightPx) to (0f to -1f), Rect(0, 0, frame.widthPx, barPixels) to (0f to 1f))
        }
        val overlays = areas.mapNotNull { (area, anchor) ->
            val bitmap = bars.bar(area) ?: return@mapNotNull null
            val settings = StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(anchor.first, anchor.second)
                .setOverlayFrameAnchor(anchor.first, anchor.second)
                // A bar of a rounded pixel count is drawn at the exact thickness, so it meets the clip where Presentation puts it.
                .apply { if (geometry.barsAtSides) setScale(thickness / barPixels, 1f) else setScale(1f, thickness / barPixels) }
                .build()
            BitmapOverlay.createStaticBitmapOverlay(bitmap, settings)
        }
        return if (overlays.isEmpty()) listOf(fitted) else listOf(fitted, OverlayEffect(overlays))
    }

    // Each bar is a one-pixel bitmap scaled to the bar's size in frame pixels, its outer edge on the frame's edge, so it
    // meets the fitted clip exactly where Presentation's own arithmetic puts it. core-ktx's createBitmap isn't a declared
    // dependency of this module; the platform call does the same.
    @SuppressLint("UseKtx")
    private fun colorBars(argb: Long, geometry: FitGeometry): List<BitmapOverlay> {
        val pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(argb.toInt()) }
        val thickness = geometry.barThickness
        val anchors = if (geometry.barsAtSides) listOf(-1f to 0f, 1f to 0f) else listOf(0f to 1f, 0f to -1f)
        return anchors.map { (x, y) ->
            val settings = StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(x, y)
                .setOverlayFrameAnchor(x, y)
                .apply { if (geometry.barsAtSides) setScale(thickness, frame.heightPx.toFloat()) else setScale(frame.widthPx.toFloat(), thickness) }
                .build()
            BitmapOverlay.createStaticBitmapOverlay(pixel, settings)
        }
    }

    private fun blurredFill(geometry: FitGeometry): List<Effect> {
        val fitted = PixelSize(geometry.fittedWidth.roundToInt().coerceAtLeast(1), geometry.fittedHeight.roundToInt().coerceAtLeast(1))
        val sharpScale = geometry.fitScale / geometry.fillScale
        val sigmaPx = min(frame.widthPx, frame.heightPx) * CanvasCatalog.BLUR_SIGMA_OF_SHORT_SIDE
        return listOf(
            presentation(fitted, Presentation.LAYOUT_SCALE_TO_FIT),
            GaussianBlurWithFrameOverlaid(sigmaPx * sharpScale, sharpScale, sharpScale),
            presentation(frame, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
        )
    }

    private fun presentation(size: PixelSize, layout: @Presentation.Layout Int): Presentation =
        Presentation.createForWidthAndHeight(size.widthPx, size.heightPx, layout)
}
