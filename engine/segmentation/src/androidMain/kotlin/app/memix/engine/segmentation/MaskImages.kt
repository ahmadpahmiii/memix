package app.memix.engine.segmentation

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/** The picture going into the model and the mask coming out of it. */
internal object MaskImages {
    private const val OPAQUE = 0xFF shl 24
    private const val FOREGROUND_CONFIDENCE = 0.5f
    private const val MAX_LEVEL = 255

    /**
     * Decodes [file] with its EXIF orientation applied, scaled down while decoding so its longer side is at most
     * [longSidePx] (never scaled up). A software ARGB_8888 sRGB bitmap, which is what ML Kit's `InputImage.fromBitmap`
     * takes. Throws `IOException` for a file that is missing or damaged.
     */
    fun decode(file: File, longSidePx: Int): Bitmap {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val longSide = max(info.size.width, info.size.height)
            val scale = if (longSide > longSidePx) longSidePx.toFloat() / longSide else 1f
            decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        }
        // A wide-gamut source can still come back as RGBA_F16.
        return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }

    /**
     * Turns the model's per-pixel confidence (0 to 1, row by row, [width] x [height] values) into a grayscale PNG.
     * [checkActive] runs once per row so a cancel lands within a few milliseconds.
     */
    fun encodeMask(confidence: FloatBuffer, width: Int, height: Int, checkActive: () -> Unit): EncodedMask {
        val pixels = IntArray(width * height)
        var foregroundPixels = 0
        for (row in 0 until height) {
            checkActive()
            for (index in row * width until (row + 1) * width) {
                val value = confidence.get(index).coerceIn(0f, 1f)
                if (value >= FOREGROUND_CONFIDENCE) foregroundPixels++
                val level = (value * MAX_LEVEL + 0.5f).toInt()
                pixels[index] = OPAQUE or (level shl 16) or (level shl 8) or level
            }
        }
        checkActive()
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        return EncodedMask(png, foregroundShare = foregroundPixels.toFloat() / (width * height))
    }
}

/** A mask PNG and the share of the picture (0 to 1) the subject covers. */
internal class EncodedMask(val png: ByteArray, val foregroundShare: Float)
