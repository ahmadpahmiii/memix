package app.memix.engine.video

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.os.Build
import app.memix.core.domain.AppError
import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.video.Thumbnail
import app.memix.core.domain.video.ThumbnailReader
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Android [ThumbnailReader]: video frames from `MediaMetadataRetriever` (the platform's hardware decoders, frame
 * rotation applied), photos from `ImageDecoder` (EXIF orientation applied), each center-cropped to a square and
 * encoded as JPEG. Reads the app's own copy of each file only.
 *
 * Everything runs on one worker on [ioDispatcher], one picture at a time: a retriever isn't thread-safe, and one
 * extra decoder beside the preview's stays inside every phone's codec limit. The last [MAX_OPEN_VIDEOS] videos stay
 * open, because opening a file costs more than reading one frame from it.
 */
internal class MediaThumbnailReader(
    private val filesDir: File,
    ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : ThumbnailReader {
    private val worker = ioDispatcher.limitedParallelism(1)

    // Only the worker touches these; close() hands their release to the worker too.
    private val openVideos = LinkedHashMap<String, OpenVideo>(MAX_OPEN_VIDEOS, LOAD_FACTOR, /* accessOrder= */ true)

    @Volatile
    private var closed = false

    override suspend fun thumbnail(source: MediaRef, timeUs: Long, sizePx: Int): Outcome<Thumbnail> = withContext(worker) {
        if (closed) return@withContext Outcome.Failure(AppError.Unexpected(IllegalStateException("The thumbnail reader is closed")))
        val file = source.cachedCopyPath?.let { File(filesDir, it) }?.takeIf { it.isFile }
            ?: return@withContext Outcome.Failure(AppError.NotFound)
        try {
            val picture = when (source.kind) {
                MediaKind.VIDEO -> videoFrame(file, timeUs, sizePx)
                MediaKind.IMAGE -> photo(file, sizePx)
                MediaKind.AUDIO -> null
            } ?: return@withContext unreadable(IllegalStateException("No picture in this ${source.kind} file"))
            Outcome.Success(Thumbnail(picture.toSquareJpeg(sizePx), sizePx))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // MediaMetadataRetriever and ImageDecoder report damaged or unsupported files with several exception types.
            unreadable(e)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        // On the worker, after a picture still being decoded, so no retriever is released while in use.
        CoroutineScope(worker).launch {
            openVideos.values.forEach { it.release() }
            openVideos.clear()
        }
    }

    private fun videoFrame(file: File, timeUs: Long, sizePx: Int): Bitmap? {
        val video = openVideo(file)
        val box = boxForShortSide(video.shownWidth, video.shownHeight, sizePx)
        val frameUs = timeUs.coerceIn(0, video.lastFrameUs)
        // The closest frame, not the closest keyframe: zoomed in, neighbouring tiles must show different frames.
        // Choosing RGB_565 needs Android 11; Android 10 gets the default ARGB_8888.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            video.retriever.getScaledFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST, box.first, box.second, FRAME_PARAMS)
        } else {
            video.retriever.getScaledFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST, box.first, box.second)
        }
    }

    private fun openVideo(file: File): OpenVideo {
        openVideos[file.path]?.let { return it }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
        } catch (e: RuntimeException) {
            retriever.release()
            throw e
        }
        val video = OpenVideo(retriever)
        openVideos[file.path] = video
        if (openVideos.size > MAX_OPEN_VIDEOS) {
            val eldest = openVideos.keys.first()
            openVideos.remove(eldest)?.release()
        }
        return video
    }

    // Decoded at the smallest power-of-two reduction that still covers the tile, so a 12 MP photo never loads whole.
    private fun photo(file: File, sizePx: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(sampleSizeFor(min(info.size.width, info.size.height), sizePx))
        }

    private fun unreadable(cause: Exception): Outcome<Thumbnail> {
        // Only the exception type: messages can quote a path.
        logger.debug(TAG, "No thumbnail: ${cause::class.simpleName}")
        return Outcome.Failure(AppError.Unexpected(cause))
    }

    /** An open video file and the facts needed to ask it for frames. */
    private class OpenVideo(val retriever: MediaMetadataRetriever) {
        private val rotated = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % HALF_TURN_DEGREES != 0
        private val codedWidth = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        private val codedHeight = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)

        /** The frame's size as shown, rotation applied (the retriever returns frames rotated). */
        val shownWidth = if (rotated) codedHeight else codedWidth
        val shownHeight = if (rotated) codedWidth else codedHeight

        /** About one frame before the end: asking past the last frame returns nothing on some phones. */
        val lastFrameUs = max(0L, retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_DURATION) * MICROS_PER_MILLI - LAST_FRAME_MARGIN_US)

        fun release() {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Nothing to do: the file is being let go of anyway.
            }
        }
    }

    private companion object {
        const val TAG = "MemixThumbnails"
        const val MAX_OPEN_VIDEOS = 2
        const val LOAD_FACTOR = 0.75f
        const val HALF_TURN_DEGREES = 180
        const val MICROS_PER_MILLI = 1_000L
        const val LAST_FRAME_MARGIN_US = 40_000L

        // RGB_565 halves the full-size frame the retriever decodes before scaling; a thumbnail doesn't need alpha.
        val FRAME_PARAMS = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.RGB_565 }
    }
}

/** A box whose short side is [sizePx] in the frame's own shape, so the retriever's scale-to-fit gives that short side. */
private fun boxForShortSide(width: Int, height: Int, sizePx: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return sizePx to sizePx
    return if (width <= height) {
        sizePx to ceil(sizePx.toDouble() * height / width).toInt()
    } else {
        ceil(sizePx.toDouble() * width / height).toInt() to sizePx
    }
}

/** The largest power of two that keeps a side of [shortSidePx] at least [sizePx] after reduction. */
private fun sampleSizeFor(shortSidePx: Int, sizePx: Int): Int {
    var sample = 1
    while (shortSidePx / (sample * 2) >= sizePx) sample *= 2
    return sample
}

private fun MediaMetadataRetriever.metadataInt(key: Int): Int = extractMetadata(key)?.toIntOrNull() ?: 0

/** The center square of this picture at [sizePx], as JPEG. Recycles the in-between bitmaps, which are never drawn. */
private fun Bitmap.toSquareJpeg(sizePx: Int): ByteArray {
    val side = min(width, height)
    val cropped = if (width == height) this else Bitmap.createBitmap(this, (width - side) / 2, (height - side) / 2, side, side)
    val square = if (side == sizePx) cropped else Bitmap.createScaledBitmap(cropped, sizePx, sizePx, /* filter= */ true)
    val bytes = ByteArrayOutputStream().also { square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY_PERCENT, it) }.toByteArray()
    listOf(this, cropped, square).distinct().forEach { it.recycle() }
    return bytes
}

// Thumbnails are small and decoded once; 85 keeps edges clean at a few kilobytes each.
private const val JPEG_QUALITY_PERCENT = 85
