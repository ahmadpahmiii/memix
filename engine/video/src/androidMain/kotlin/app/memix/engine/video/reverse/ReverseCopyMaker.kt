package app.memix.engine.video.reverse

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import app.memix.core.domain.Logger
import app.memix.core.model.project.FreezeReverseCatalog
import app.memix.engine.video.TransformerExport
import app.memix.engine.video.clipping
import app.memix.engine.video.setSourceDuration
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Makes the reversed copy of one range of a video (P4-10) with no codec of our own writing and nothing but the platform and Media3:
 *
 * 1. **Intra pass.** Media3's Transformer re-encodes the range, picture only, as H.264 in which every frame is a key frame, at most 1080p and 30 fps.
 * 2. **Turn around.** Every frame of that file can be read on its own, so its samples are read back to front in blocks and written to the new MP4 in that
 *    order with mirrored times ([ReverseMath]); nothing is decoded or encoded again. The sound comes from the original file ([ReversedAudio]) and is
 *    interleaved with the frames, so the copy plays from disk like any other MP4.
 *
 * Memory stays at one block of samples ([BLOCK_BYTES]) plus the sound (about 11.5 MB for 60 s of stereo); the disk holds the intra file next to the copy until the
 * end (TECHNICAL_DESIGN → Reverse and freeze frame has the numbers). Fails with a [ReverseException] when the phone has no room, the encoder ignores the
 * all-intra request (some files would then read back wrong, so it is checked sample by sample), or a codec fails.
 */
@OptIn(UnstableApi::class)
internal class ReverseCopyMaker(private val context: Context, private val transformerExport: TransformerExport, private val logger: Logger) {
    private val reversedAudio = ReversedAudio(context)

    /**
     * Writes the reversed [key] range of [sourceUri] to [target] (through a ".tmp" next to it, renamed when whole). [onProgress] gets 0 to 1, a few times a
     * second. Cancelling stops the work at the next frame or block and leaves no file behind.
     */
    suspend fun make(key: ReversalKey, sourceUri: Uri, target: File, onProgress: (Float) -> Unit) {
        // The editor refuses a longer range; a longer one here would be a bug or an old draft, and a copy of it would not fit the phone.
        if (key.spanUs > FreezeReverseCatalog.MAX_REVERSE_SOURCE_US + SLACK_US) throw ReverseException("The range is longer than ${FreezeReverseCatalog.MAX_REVERSE_SOURCE_US} µs: ${key.spanUs}", tooLong = true)
        val folder = target.parentFile ?: throw ReverseException("No cache folder")
        folder.mkdirs()
        requireRoomFor(key, folder)
        val intra = File(folder, "${target.name}.intra.tmp")
        val output = File(folder, "${target.name}.tmp")
        try {
            makeIntraWithRetries(key, sourceUri, intra) { onProgress(it * INTRA_SHARE) }
            val audio = reversedAudio.reverse(sourceUri, key.startUs, key.endUs)
            writeReversed(intra, audio, output) { onProgress(INTRA_SHARE + it * (1f - INTRA_SHARE)) }
            if (!output.renameTo(target)) throw ReverseException("The copy couldn't be named")
        } finally {
            intra.delete()
            output.delete()
        }
    }

    // About 2 x the intra file plus a margin, so a full phone says so before 30 s of work, not after it.
    private fun requireRoomFor(key: ReversalKey, folder: File) {
        val needed = estimatedBytes(key.spanUs) * 2 + MARGIN_BYTES
        val free = try {
            StatFs(folder.path).availableBytes
        } catch (e: IllegalArgumentException) {
            return
        }
        if (free < needed) throw ReverseException("Not enough space: $free of $needed bytes", noSpace = true)
    }

    // The all-intra request first; an encoder that ignores it gets one more try with a key frame every frame's time (a 1/30 s interval), and one that refuses
    // the bitrate gets one try at half of it. Which one worked is logged, so the hand check can see what a phone needed.
    private suspend fun makeIntraWithRetries(key: ReversalKey, sourceUri: Uri, intra: File, onProgress: (Float) -> Unit) {
        var interval = 0f
        var bitrate = INTRA_BITRATE_BPS
        var triedInterval = false
        var triedBitrate = false
        while (true) {
            try {
                makeIntraFile(key, sourceUri, intra, interval, bitrate, onProgress)
                if (verifyAllIntra(intra)) return
                if (triedInterval) throw ReverseException("This phone's encoder didn't make every frame a key frame")
                triedInterval = true
                interval = SHORT_INTERVAL_SECONDS
                logger.debug(TAG, "All-intra ignored; retrying with a ${interval}s key frame interval")
            } catch (e: ReverseException) {
                val unsupported = (e.cause as? ExportException)?.errorCode == ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED
                if (!unsupported || triedBitrate) throw e
                triedBitrate = true
                bitrate = LOWER_BITRATE_BPS
                logger.debug(TAG, "Encoder refused ${INTRA_BITRATE_BPS / BPS_PER_MBPS} Mbps; retrying at ${bitrate / BPS_PER_MBPS} Mbps")
            }
        }
    }

    // Whether every frame of the file is a key frame (a cheap walk without reading pictures).
    private fun verifyAllIntra(file: File): Boolean {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/") } ?: return false
            extractor.selectTrack(track)
            while (extractor.sampleTime >= 0) {
                if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC == 0) return false
                extractor.advance()
            }
            return true
        } finally {
            extractor.release()
        }
    }

    private suspend fun makeIntraFile(key: ReversalKey, sourceUri: Uri, intra: File, intervalSeconds: Float, bitrateBps: Int, onProgress: (Float) -> Unit) {
        val short = key.source.pixelSize?.let { minOf(it.widthPx, it.heightPx) } ?: 0
        val effects = buildList<Effect> { if (short > MAX_SHORT_SIDE_PX) add(Presentation.createForShortSide(MAX_SHORT_SIDE_PX)) }
        val item = EditedMediaItem.Builder(MediaItem.Builder().setUri(sourceUri).setClippingConfiguration(clipping(key.startUs, key.endUs)).build())
            .setRemoveAudio(true)
            // A ceiling: faster files drop frames, as the preview and the export do.
            .setFrameRate(FRAME_RATE)
            .setEffects(Effects(emptyList(), effects))
            .setSourceDuration(key.source, key.endUs)
            .build()
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addItem(item).build())
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
        try {
            transformerExport.run(composition, intra.path, MimeTypes.VIDEO_H264, bitrateBps, muxerFactory = null, onProgress = onProgress, allIntra = true, iFrameIntervalSeconds = intervalSeconds)
        } catch (e: ExportException) {
            throw ReverseException("The picture couldn't be prepared: ${e.errorCodeName}", noSpace = e.isNoSpace(), cause = e)
        }
    }

    private fun ExportException.isNoSpace(): Boolean =
        errorCode == ExportException.ERROR_CODE_IO_UNSPECIFIED && generateSequence<Throwable>(this) { it.cause }.any { it.message?.contains("ENOSPC") == true }

    // What one frame of the intra file is: where it is, how big, and when it starts.
    private class Scan(val startsUs: LongArray, val sizes: IntArray)

    private suspend fun writeReversed(intra: File, audio: EncodedAudio?, output: File, onProgress: (Float) -> Unit) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(intra.path)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/") }
                ?: throw ReverseException("The intra file has no picture")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val scan = scan(extractor)
            val span = ReverseMath.spanOf(scan.startsUs, FALLBACK_FRAME_US)
            val reversedStarts = ReverseMath.reversedStartsUs(scan.startsUs, span)
            muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (format.containsKey(MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION))
            val videoTrack = muxer.addTrack(format)
            val audioTrack = audio?.let { muxer.addTrack(it.format) } ?: -1
            muxer.start()
            val writer = InterleavedWriter(muxer, videoTrack, audioTrack, audio?.samples.orEmpty())
            writeBlocks(extractor, scan, reversedStarts, writer, onProgress)
            writer.finishAudio()
            muxer.stop()
        } catch (e: IOException) {
            throw ReverseException("The copy couldn't be written", noSpace = e.message?.contains("ENOSPC") == true || isLowOnSpace(output), cause = e)
        } catch (e: IllegalStateException) {
            // MediaMuxer reports a failed write (a full disk among them) this way.
            throw ReverseException("The copy couldn't be written", noSpace = isLowOnSpace(output), cause = e)
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    private fun isLowOnSpace(file: File): Boolean =
        try {
            StatFs((file.parentFile ?: file).path).availableBytes < LOW_SPACE_BYTES
        } catch (e: IllegalArgumentException) {
            false
        }

    // One walk over the file without reading any picture data: every frame's start and size, and the check that every one is a key frame.
    private suspend fun scan(extractor: MediaExtractor): Scan {
        val starts = ArrayList<Long>()
        val sizes = ArrayList<Int>()
        while (true) {
            coroutineContext.ensureActive()
            val timeUs = extractor.sampleTime
            if (timeUs < 0) break
            if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC == 0) {
                throw ReverseException("This phone's encoder didn't make every frame a key frame")
            }
            starts += timeUs
            sizes += extractor.sampleSize.toInt()
            extractor.advance()
        }
        if (starts.isEmpty()) throw ReverseException("The intra file has no frames")
        return Scan(starts.toLongArray(), sizes.toIntArray())
    }

    // Blocks of frames from the end of the file: each read forward (one seek) into memory and written back to front.
    private suspend fun writeBlocks(extractor: MediaExtractor, scan: Scan, reversedStarts: LongArray, writer: InterleavedWriter, onProgress: (Float) -> Unit) {
        val blocks = ReverseMath.blocksFromEnd(scan.sizes, BLOCK_BYTES.toLong())
        val buffer = ByteBuffer.allocateDirect(maxOf(BLOCK_BYTES, scan.sizes.max()))
        var written = 0
        for (block in blocks) {
            coroutineContext.ensureActive()
            val offsets = readBlock(extractor, scan, block, buffer)
            for (frame in block.reversed()) {
                writer.writeFrame(buffer, offsets[frame - block.first], scan.sizes[frame], reversedStarts[frame])
                written++
            }
            onProgress(written.toFloat() / scan.sizes.size)
        }
    }

    // Reads the frames of [block] one after the other into [buffer]; returns where each starts in it.
    private fun readBlock(extractor: MediaExtractor, scan: Scan, block: IntRange, buffer: ByteBuffer): IntArray {
        extractor.seekTo(scan.startsUs[block.first], MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val offsets = IntArray(block.count())
        var offset = 0
        for (frame in block) {
            if (extractor.sampleTime != scan.startsUs[frame]) throw ReverseException("A frame was not where the file said")
            offsets[frame - block.first] = offset
            buffer.clear()
            val size = extractor.readSampleData(buffer, offset)
            if (size != scan.sizes[frame]) throw ReverseException("A frame had the wrong size")
            offset += size
            extractor.advance()
        }
        return offsets
    }

    /** Writes the frames in reversed order and slips the sound's samples in by time, so the two tracks are interleaved on disk. */
    private class InterleavedWriter(private val muxer: MediaMuxer, private val videoTrack: Int, private val audioTrack: Int, private val audio: List<EncodedSample>) {
        private var nextAudio = 0
        private val info = MediaCodec.BufferInfo()

        fun writeFrame(buffer: ByteBuffer, offset: Int, size: Int, ptsUs: Long) {
            while (nextAudio < audio.size && audio[nextAudio].ptsUs <= ptsUs) writeAudio(audio[nextAudio++])
            info.set(offset, size, ptsUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
            muxer.writeSampleData(videoTrack, buffer, info)
        }

        fun finishAudio() {
            while (nextAudio < audio.size) writeAudio(audio[nextAudio++])
        }

        private fun writeAudio(sample: EncodedSample) {
            info.set(0, sample.data.size, sample.ptsUs, 0)
            muxer.writeSampleData(audioTrack, ByteBuffer.wrap(sample.data), info)
        }
    }

    internal companion object {
        /** The intra file's rate: 30 Mbps keeps 1080p30 clean through the second encode of the export. */
        const val INTRA_BITRATE_BPS = 30_000_000
        private const val BPS_PER_MBPS = 1_000_000
        private const val TAG = "MemixReverse"
        const val MAX_SHORT_SIDE_PX = 1080
        const val FRAME_RATE = 30
        private const val INTRA_SHARE = 0.85f
        private const val BLOCK_BYTES = 24 * 1024 * 1024
        private const val MARGIN_BYTES = 100L * 1024 * 1024
        private const val LOW_SPACE_BYTES = 50L * 1024 * 1024
        private const val FALLBACK_FRAME_US = 33_333L
        private const val SLACK_US = 1_000L
        private const val LOWER_BITRATE_BPS = 15_000_000
        private const val SHORT_INTERVAL_SECONDS = 1f / FRAME_RATE
        private const val BITS_PER_BYTE = 8
        private const val MICROS_PER_SECOND = 1_000_000L

        /** How big the copy of a [spanUs] range is, bytes (the intra file is as big again while it is made). */
        fun estimatedBytes(spanUs: Long): Long = spanUs * INTRA_BITRATE_BPS / BITS_PER_BYTE / MICROS_PER_SECOND
    }
}
