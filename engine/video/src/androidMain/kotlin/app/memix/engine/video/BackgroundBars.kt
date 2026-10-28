package app.memix.engine.video

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Rect
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.coveredImageSize
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The background picture as the bars a Fit clip leaves (spec docs/ux/specs/P2-12-canvas-background-image.md → How it
 * draws): the picture scaled to cover the whole [frame], centered, the overflow cropped, decoded once at the frame's size
 * (EXIF turn applied, software bitmap so the GL overlay can upload it), then cut into the bar rectangles the clips need.
 * Preview and export build their frames at their own [frame] sizes, so the same picture lies the same in both.
 *
 * Reads the file: call from a background thread. A file that is missing or can't be decoded gives no bars ([bar] is null),
 * which draws black, as a gap does.
 */
internal class BackgroundBars(private val file: File?, private val frame: PixelSize) {
    private val cover: Bitmap? by lazy { decodeCover() }
    private val bars = HashMap<Rect, Bitmap>()

    /** The part of the covered picture inside [area] (frame pixels), or null when there's no picture. Equal areas share one bitmap. */
    fun bar(area: Rect): Bitmap? {
        val picture = cover ?: return null
        return bars.getOrPut(area) { Bitmap.createBitmap(picture, area.left, area.top, area.width(), area.height()) }
    }

    private fun decodeCover(): Bitmap? {
        if (file == null || !file.isFile) return null
        return try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                // info.size is the picture as shown (orientation applied); cover scaling is the model's, shared with the photo scene.
                val covered = coveredImageSize(PixelSize(info.size.width, info.size.height), frame)
                val width = max(frame.widthPx, covered.width.roundToInt())
                val height = max(frame.heightPx, covered.height.roundToInt())
                decoder.setTargetSize(width, height)
                // Centered: the overflow on every side is cut by decoding only the frame's rectangle.
                val left = (width - frame.widthPx) / 2
                val top = (height - frame.heightPx) / 2
                decoder.setCrop(Rect(left, top, left + frame.widthPx, top + frame.heightPx))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // 8-bit sRGB, like the video it sits beside: a wide-gamut or 16-bit picture would not upload as a GL texture the same way.
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            }
        } catch (e: IOException) {
            // Includes ImageDecoder.DecodeException.
            null
        } catch (e: OutOfMemoryError) {
            // Too big for this phone at this size: the bars draw black.
            null
        }
    }
}
