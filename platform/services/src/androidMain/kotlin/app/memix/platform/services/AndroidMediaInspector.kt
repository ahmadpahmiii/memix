package app.memix.platform.services

import android.content.Context
import android.graphics.ImageDecoder
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Size
import app.memix.core.domain.Logger
import app.memix.core.domain.media.MediaFacts
import app.memix.core.domain.media.MediaInspector
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.PixelSize
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Checks copies with the phone's own media framework: MediaExtractor and the decoder list for videos (picture and
 * sound) and sounds, ImageDecoder for photos. Nothing is played or fully decoded, so a check takes milliseconds.
 */
internal class AndroidMediaInspector(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : MediaInspector {
    // Asking the codec service once per process is enough; the phone's decoders don't change while it runs.
    private val decoders by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS) }

    override suspend fun inspect(path: String, kind: MediaKind): MediaFacts? = withContext(ioDispatcher) {
        val file = File(context.filesDir, path)
        // Any failure to read the file means the same to the user: it won't play. Nothing here suspends.
        try {
            when (kind) {
                MediaKind.VIDEO -> inspectVideo(file)
                MediaKind.IMAGE -> inspectPhoto(file)
                MediaKind.AUDIO -> inspectSound(file)
            }
        } catch (e: Exception) {
            logger.debug(TAG, "Media check failed: ${e::class.simpleName}")
            null
        }
    }

    private fun inspectVideo(file: File): MediaFacts? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            val video = formats.firstOrNull { it.mimeType()?.startsWith("video/") == true } ?: return null
            val mimeType = video.mimeType() ?: return null
            val width = video.getInteger(MediaFormat.KEY_WIDTH)
            val height = video.getInteger(MediaFormat.KEY_HEIGHT)
            val durationUs = longestTrackUs(formats)
            if (durationUs <= 0 || width <= 0 || height <= 0 || !canDecodeVideo(mimeType, width, height)) return null
            val rotation = if (video.containsKey(MediaFormat.KEY_ROTATION)) video.getInteger(MediaFormat.KEY_ROTATION) else 0
            val quarterTurn = rotation % HALF_TURN_DEGREES != 0
            val shownSize = if (quarterTurn) PixelSize(height, width) else PixelSize(width, height)
            return MediaFacts(durationUs, shownSize, hasAudio = formats.any(::isDecodableAudio))
        } finally {
            extractor.release()
        }
    }

    private fun inspectSound(file: File): MediaFacts? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            if (formats.none(::isDecodableAudio)) return null
            val durationUs = longestTrackUs(formats)
            return if (durationUs > 0) MediaFacts(durationUs, pixelSize = null, hasAudio = true) else null
        } finally {
            extractor.release()
        }
    }

    // Like the engine's own extractor, the file plays as long as its longest track.
    private fun longestTrackUs(formats: List<MediaFormat>): Long =
        formats.maxOf { if (it.containsKey(MediaFormat.KEY_DURATION)) it.getLong(MediaFormat.KEY_DURATION) else 0L }

    // A decoder for the codec at this size. Frame rate and profile are left out on purpose: the engine can still
    // play (or tone-map) a stream the decoder rates slightly above its limits, and rejecting it would be worse.
    private fun canDecodeVideo(mimeType: String, width: Int, height: Int): Boolean =
        decodableMimeTypes(mimeType).any { candidate ->
            decoders.findDecoderForFormat(MediaFormat.createVideoFormat(candidate, width, height)) != null
        }

    private fun isDecodableAudio(format: MediaFormat): Boolean {
        val mimeType = format.mimeType()?.takeIf { it.startsWith("audio/") } ?: return false
        if (!format.containsKey(MediaFormat.KEY_SAMPLE_RATE) || !format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) return false
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        return decoders.findDecoderForFormat(MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount)) != null
    }

    // Dolby Vision files carry an HEVC or AVC base layer that plays where there's no Dolby Vision decoder.
    private fun decodableMimeTypes(mimeType: String): List<String> =
        if (mimeType == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) {
            listOf(mimeType, MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC)
        } else {
            listOf(mimeType)
        }

    // Decodes a small version only, to prove the picture decodes. ImageDecoder applies EXIF orientation, so the
    // header size is the size as shown (Android 10 included).
    private fun inspectPhoto(file: File): MediaFacts? {
        var shownSize: Size? = null
        val preview = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            shownSize = info.size
            decoder.setTargetSampleSize(sampleSizeFor(info.size))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        preview.recycle()
        val size = shownSize ?: return null
        return MediaFacts(durationUs = null, pixelSize = PixelSize(size.width, size.height), hasAudio = false)
    }

    private fun sampleSizeFor(size: Size): Int = (maxOf(size.width, size.height) / CHECK_DECODE_MAX_SIDE_PX).coerceAtLeast(1)

    private fun MediaFormat.mimeType(): String? = getString(MediaFormat.KEY_MIME)

    private companion object {
        const val TAG = "MediaImport"
        const val HALF_TURN_DEGREES = 180
        // The check decodes the photo at about this size: enough to find a damaged file, cheap even for 50 MP.
        const val CHECK_DECODE_MAX_SIDE_PX = 512
    }
}
