package app.memix.engine.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.AudioFormat
import android.os.Process
import app.memix.core.domain.AppError
import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.video.AudioPeaks
import app.memix.core.model.project.MediaRef
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Reads the peaks of a sound for the timeline's waveform (P3-09, [app.memix.core.domain.video.VideoEngine.waveform]): decodes
 * the file's audio once with the phone's own decoder and keeps the peaks in `cache/waveforms/`, so the next open of the project
 * reads a few kilobytes instead of decoding again. Media copies are never changed in place, so a cached file stays right; the
 * file's size and time are in its name anyway. The cache may be cleared by the system: the peaks are then read again.
 */
internal class AudioPeaksReader(
    private val filesDir: File,
    cacheDir: File,
    private val dispatcher: CoroutineDispatcher,
    private val logger: Logger,
) {
    private val peaksDir = File(cacheDir, "waveforms")

    /** The peaks of [source]; [AppError.NotFound] when its file isn't on the phone, [AppError.Unexpected] when it can't be decoded. */
    suspend fun read(source: MediaRef): Outcome<AudioPeaks> = withContext(dispatcher) {
        val file = source.cachedCopyPath?.let { File(filesDir, it) }?.takeIf { it.isFile } ?: return@withContext Outcome.Failure(AppError.NotFound)
        val cached = File(peaksDir, cacheNameOf(file))
        val peaks = readCached(cached) ?: decodeInBackground(file)?.also { writeCached(cached, it) }
        if (peaks != null) Outcome.Success(peaks) else Outcome.Failure(AppError.Unexpected(IllegalStateException("The sound can't be decoded")))
    }

    // Decoding is a long CPU run that must not compete with the editor's own threads; the dispatcher's thread gets its priority back after.
    private suspend fun decodeInBackground(file: File): AudioPeaks? {
        val before = Process.getThreadPriority(Process.myTid())
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        try {
            return decode(file)
        } finally {
            Process.setThreadPriority(before)
        }
    }

    private suspend fun decode(file: File): AudioPeaks? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).mimeType().startsWith("audio/") } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            decoder = MediaCodec.createDecoderByType(format.mimeType()).also {
                it.configure(format, null, null, 0)
                it.start()
            }
            return decodeAll(extractor, decoder, format)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            logger.debug(TAG, "Waveform: ${file.name} can't be read (${e::class.simpleName})")
            return null
        } catch (e: IllegalStateException) {
            // MediaCodec and MediaExtractor report a file they can't decode like this; no waveform shows, as the spec says.
            logger.debug(TAG, "Waveform: ${file.name} can't be decoded (${e::class.simpleName})")
            return null
        } catch (e: IllegalArgumentException) {
            logger.debug(TAG, "Waveform: ${file.name} has no usable audio (${e::class.simpleName})")
            return null
        } finally {
            decoder?.let {
                runCatching { it.stop() }
                it.release()
            }
            extractor.release()
        }
    }

    // The usual synchronous decode loop: feed the extractor's samples in, read PCM out, until the decoder says it's done.
    private suspend fun decodeAll(extractor: MediaExtractor, decoder: MediaCodec, inputFormat: MediaFormat): AudioPeaks {
        val info = MediaCodec.BufferInfo()
        var accumulator = PeakAccumulator(inputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE), inputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, 1))
        var samples = ShortArray(0)
        var floatSamples = FloatArray(0)
        var isFloat = false
        var inputDone = false
        var outputDone = false
        var idleOutputs = 0
        val started = TimeSource.Monotonic.markNow()
        while (!outputDone) {
            currentCoroutineContext().ensureActive()
            // A decoder that stops answering, or a file that never ends, must not hold the thread for good.
            check(idleOutputs < MAX_IDLE_OUTPUTS && started.elapsedNow() < MAX_DECODE_TIME) { "the decoder stalled" }
            if (!inputDone) inputDone = feedInput(extractor, decoder)
            val index = decoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            idleOutputs = if (index == MediaCodec.INFO_TRY_AGAIN_LATER) idleOutputs + 1 else 0
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // The decoder's own numbers win over the file header's (HE-AAC reports its real rate here).
                    val output = decoder.outputFormat
                    isFloat = output.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) == AudioFormat.ENCODING_PCM_FLOAT
                    accumulator = PeakAccumulator(output.intOr(MediaFormat.KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE), output.intOr(MediaFormat.KEY_CHANNEL_COUNT, 1))
                }
                index >= 0 -> {
                    val buffer = decoder.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset).limit(info.offset + info.size)
                        val count = info.size / (if (isFloat) Float.SIZE_BYTES else Short.SIZE_BYTES)
                        if (samples.size < count) samples = ShortArray(count)
                        if (isFloat) {
                            if (floatSamples.size < count) floatSamples = FloatArray(count)
                            buffer.order(ByteOrder.nativeOrder()).asFloatBuffer().get(floatSamples, 0, count)
                            for (i in 0 until count) samples[i] = (floatSamples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                        } else {
                            buffer.order(ByteOrder.nativeOrder()).asShortBuffer().get(samples, 0, count)
                        }
                        accumulator.add(samples, count)
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(index, false)
                }
            }
        }
        return accumulator.finish()
    }

    /** Queues the next compressed sample, or the end of the stream; true once the end was queued. */
    private fun feedInput(extractor: MediaExtractor, decoder: MediaCodec): Boolean {
        val index = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (index < 0) return false
        val buffer = decoder.getInputBuffer(index) ?: return false
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }
        decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
        extractor.advance()
        return false
    }

    // Header: a tag, the peaks per second, then one byte per peak.
    private fun readCached(file: File): AudioPeaks? {
        if (!file.isFile) return null
        return try {
            val bytes = file.readBytes()
            val header = ByteBuffer.wrap(bytes)
            if (bytes.size < HEADER_BYTES || header.int != CACHE_TAG) return null
            AudioPeaks(header.int, bytes.copyOfRange(HEADER_BYTES, bytes.size))
        } catch (e: IOException) {
            logger.debug(TAG, "Waveform cache unreadable (${e::class.simpleName}); decoding again")
            null
        }
    }

    // Written to a temporary name and moved, so a process killed mid-write never leaves half a file to be read.
    private fun writeCached(file: File, peaks: AudioPeaks) {
        try {
            peaksDir.mkdirs()
            val temporary = File(peaksDir, "${file.name}.tmp")
            val levels = peaks.levelBytes()
            pruneCache()
            temporary.writeBytes(ByteBuffer.allocate(HEADER_BYTES + levels.size).putInt(CACHE_TAG).putInt(peaks.peaksPerSecond).put(levels).array())
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            // Only a cache: the waveform still shows, and the next open decodes again.
            logger.debug(TAG, "Waveform cache not written (${e::class.simpleName})")
        }
    }

    // Only a cache: the oldest files go when the folder is over its cap, so it can't grow with every project ever opened.
    private fun pruneCache() {
        val files = peaksDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= MAX_CACHE_BYTES) return
            total -= file.length()
            file.delete()
        }
    }

    private fun cacheNameOf(file: File): String = "${file.path.hashCode().toUInt().toString(HEX)}-${file.length()}-${file.lastModified()}.peaks"

    private fun MediaFormat.mimeType(): String = getString(MediaFormat.KEY_MIME).orEmpty()

    private fun MediaFormat.intOr(key: String, fallback: Int): Int = if (containsKey(key)) getInteger(key) else fallback

    private companion object {
        const val TAG = "MemixWaveform"
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val HEADER_BYTES = 8
        const val CACHE_TAG = 0x4D585031 // "MXP1"
        const val HEX = 16
        const val MAX_CACHE_BYTES = 5L * 1024 * 1024
        const val MAX_IDLE_OUTPUTS = 500
        val MAX_DECODE_TIME = 90.seconds
    }
}
