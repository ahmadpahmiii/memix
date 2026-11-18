package app.memix.engine.video.reverse

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.IOException
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** The sound of a reversed copy, already AAC-encoded: [samples] in presentation order and the track's [format] for the muxer. */
internal class EncodedAudio(val format: MediaFormat, val samples: List<EncodedSample>)

/** One AAC access unit. */
internal class EncodedSample(val data: ByteArray, val ptsUs: Long)

/**
 * The sound of a clip's trimmed range played backward (P4-10): decoded to PCM by the phone's own codec, turned around in memory (a clip is at most 60 s, so
 * a stereo PCM is about 11.5 MB), and encoded to AAC. Done from the original file, so the sound is one generation of AAC, like the picture's.
 */
internal class ReversedAudio(private val context: Context) {

    /** The reversed sound of [startUs] to [endUs] of [uri], or null when the file has no sound track (or one this reverser can't carry: more than two channels). */
    suspend fun reverse(uri: Uri, startUs: Long, endUs: Long): EncodedAudio? {
        val pcm = decode(uri, startUs, endUs) ?: return null
        if (pcm.channels !in 1..MAX_CHANNELS) return null
        ReverseMath.reverseFrames(pcm.samples, pcm.channels, pcm.frameCount)
        return encode(pcm)
    }

    private class Pcm(val samples: ShortArray, val frameCount: Int, val sampleRate: Int, val channels: Int)

    private suspend fun decode(uri: Uri, startUs: Long, endUs: Long): Pcm? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).mime().startsWith("audio/") } ?: return null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val decoder = MediaCodec.createDecoderByType(format.mime()).also { codec = it }
            decoder.configure(format, null, null, 0)
            decoder.start()
            return drainDecoder(extractor, decoder, format, startUs, endUs)
        } catch (e: IOException) {
            throw ReverseException("The sound couldn't be read", cause = e)
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    // Feeds the extractor into the decoder and keeps the frames inside [startUs, endUs).
    private suspend fun drainDecoder(extractor: MediaExtractor, decoder: MediaCodec, input: MediaFormat, startUs: Long, endUs: Long): Pcm {
        var sampleRate = input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = input.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        var samples = ShortArray(initialCapacity(startUs, endUs, sampleRate, channels))
        var frames = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            coroutineContext.ensureActive()
            if (!inputDone) inputDone = feedDecoder(extractor, decoder, endUs)
            val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val out = decoder.outputFormat
                    sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) out.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                }
                index >= 0 -> {
                    val buffer = decoder.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        val shorts = shortsOf(buffer, info, encoding)
                        val kept = keptFrames(shorts.size / channels, info.presentationTimeUs, startUs, endUs, sampleRate)
                        val needed = (frames + kept.count) * channels
                        if (needed > samples.size) samples = samples.copyOf(maxOf(needed, samples.size * 2))
                        System.arraycopy(shorts, kept.first * channels, samples, frames * channels, kept.count * channels)
                        frames += kept.count
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(index, false)
                }
            }
        }
        return Pcm(samples, frames, sampleRate, channels)
    }

    // Queues the next compressed sample; true once the end of the range or the file was queued.
    private fun feedDecoder(extractor: MediaExtractor, decoder: MediaCodec, endUs: Long): Boolean {
        val index = decoder.dequeueInputBuffer(TIMEOUT_US)
        if (index < 0) return false
        val buffer = decoder.getInputBuffer(index) ?: return false
        val size = extractor.readSampleData(buffer, 0)
        val timeUs = extractor.sampleTime
        if (size < 0 || timeUs >= endUs) {
            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }
        decoder.queueInputBuffer(index, 0, size, timeUs, 0)
        extractor.advance()
        return false
    }

    private class Kept(val first: Int, val count: Int)

    // Which of a buffer's [frameCount] frames, the first at [ptsUs], lie inside the range.
    private fun keptFrames(frameCount: Int, ptsUs: Long, startUs: Long, endUs: Long, sampleRate: Int): Kept {
        fun frameAt(timeUs: Long): Int = ((timeUs - ptsUs) * sampleRate / MICROS_PER_SECOND).toInt().coerceIn(0, frameCount)
        val first = frameAt(startUs)
        val end = frameAt(endUs)
        return Kept(first, (end - first).coerceAtLeast(0))
    }

    private fun shortsOf(buffer: java.nio.ByteBuffer, info: MediaCodec.BufferInfo, encoding: Int): ShortArray {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        if (encoding != AudioFormat.ENCODING_PCM_FLOAT) return ShortArray(info.size / BYTES_PER_SHORT).also { buffer.asShortBuffer().get(it) }
        val floats = FloatArray(info.size / BYTES_PER_FLOAT).also { buffer.asFloatBuffer().get(it) }
        return ShortArray(floats.size) { (floats[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
    }

    private fun initialCapacity(startUs: Long, endUs: Long, sampleRate: Int, channels: Int): Int =
        (((endUs - startUs) * sampleRate / MICROS_PER_SECOND) * channels + sampleRate * channels).toInt().coerceAtLeast(MIN_CAPACITY)

    private suspend fun encode(pcm: Pcm): EncodedAudio {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, pcm.sampleRate, pcm.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE_BPS_PER_CHANNEL * pcm.channels)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            return pumpEncoder(encoder, pcm)
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
        }
    }

    private suspend fun pumpEncoder(encoder: MediaCodec, pcm: Pcm): EncodedAudio {
        val out = mutableListOf<EncodedSample>()
        var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var offsetFrames = 0
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            coroutineContext.ensureActive()
            if (!inputDone) {
                val index = encoder.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val buffer = encoder.getInputBuffer(index) ?: throw ReverseException("No encoder input buffer")
                    buffer.clear()
                    val take = minOf(pcm.frameCount - offsetFrames, buffer.capacity() / (BYTES_PER_SHORT * pcm.channels))
                    val ptsUs = offsetFrames * MICROS_PER_SECOND / pcm.sampleRate
                    if (take <= 0) {
                        encoder.queueInputBuffer(index, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm.samples, offsetFrames * pcm.channels, take * pcm.channels)
                        encoder.queueInputBuffer(index, 0, take * pcm.channels * BYTES_PER_SHORT, ptsUs, 0)
                        offsetFrames += take
                    }
                }
            }
            val index = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = encoder.outputFormat
                index >= 0 -> {
                    val buffer = encoder.getOutputBuffer(index)
                    // The codec-specific data is already in the output format; only real samples go to the file.
                    if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val bytes = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.get(bytes)
                        out += EncodedSample(bytes, info.presentationTimeUs)
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(index, false)
                }
            }
        }
        return EncodedAudio(outFormat ?: throw ReverseException("The sound encoder gave no format"), out)
    }

    private fun MediaFormat.mime(): String = getString(MediaFormat.KEY_MIME).orEmpty()

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val MICROS_PER_SECOND = 1_000_000L
        const val MAX_CHANNELS = 2
        const val BITRATE_BPS_PER_CHANNEL = 64_000
        const val BYTES_PER_SHORT = 2
        const val BYTES_PER_FLOAT = 4
        const val MIN_CAPACITY = 48_000
    }
}
