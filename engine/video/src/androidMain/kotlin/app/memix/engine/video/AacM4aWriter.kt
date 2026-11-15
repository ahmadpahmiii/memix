package app.memix.engine.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder

/**
 * Writes 16-bit mono PCM to an AAC-LC `.m4a` with the phone's own encoder and the platform muxer (no third-party codec).
 * Call [write] for each chunk in order, then [finish] (or [abort] to drop the file). One thread only: the capture thread.
 * [file] is written in place; the caller renames it when the take is complete.
 */
internal class AacM4aWriter(private val file: File, sampleRate: Int) {
    // The muxer first: if the encoder then fails to open, only the muxer has to be released.
    private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val codec: MediaCodec
    private val info = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var muxerStarted = false
    private var released = false

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        }
        var opened: MediaCodec? = null
        try {
            opened = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            opened.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            opened.start()
            codec = opened
        } catch (e: RuntimeException) {
            runCatching { opened?.release() }
            runCatching { muxer.release() }
            file.delete()
            throw e
        }
    }

    /** Queues the first [count] samples of [pcm], whose first sample is [firstPtsUs] into the take; writes whatever the encoder has ready. */
    fun write(pcm: ShortArray, count: Int, firstPtsUs: Long, sampleRate: Int) {
        var offset = 0
        var stalls = 0
        while (offset < count) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) {
                // An encoder that takes no input for about a second is stuck: fail the take rather than spin.
                check(++stalls <= MAX_INPUT_STALLS) { "The encoder took no input" }
                drain()
                continue
            }
            stalls = 0
            val input = codec.getInputBuffer(index) ?: throw IllegalStateException("No encoder input buffer")
            input.clear()
            val room = input.capacity() / BYTES_PER_SAMPLE
            val take = minOf(count - offset, room)
            input.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, offset, take)
            val ptsUs = firstPtsUs + offset * MICROS_PER_SECOND / sampleRate
            codec.queueInputBuffer(index, 0, take * BYTES_PER_SAMPLE, ptsUs, 0)
            offset += take
            drain()
        }
    }

    /** Ends the stream, writes the last encoded data and closes the file. */
    fun finish(endPtsUs: Long) {
        try {
            val index = codec.dequeueInputBuffer(EOS_TIMEOUT_US)
            if (index >= 0) codec.queueInputBuffer(index, 0, 0, endPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(untilEndOfStream = true)
            if (muxerStarted) muxer.stop()
        } finally {
            release()
        }
    }

    /** Drops the encoder and the file without finishing it. */
    fun abort() {
        release()
        file.delete()
    }

    private fun drain(untilEndOfStream: Boolean = false) {
        var emptyPolls = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (untilEndOfStream) EOS_TIMEOUT_US else 0)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> startMuxer()
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // While finishing, the last buffers may take a moment; give up after a few polls rather than hang.
                    if (!untilEndOfStream || ++emptyPolls >= MAX_EMPTY_POLLS) return
                }
                index >= 0 -> {
                    if (writeOutput(index)) return
                }
            }
        }
    }

    // Returns true at the end of the stream.
    private fun writeOutput(index: Int): Boolean {
        val output = codec.getOutputBuffer(index)
        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
        if (output != null && info.size > 0 && !isConfig && muxerStarted) {
            output.position(info.offset)
            output.limit(info.offset + info.size)
            muxer.writeSampleData(trackIndex, output, info)
        }
        codec.releaseOutputBuffer(index, false)
        return info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
    }

    private fun startMuxer() {
        trackIndex = muxer.addTrack(codec.outputFormat)
        muxer.start()
        muxerStarted = true
    }

    private fun release() {
        if (released) return
        released = true
        runCatching { codec.stop() }
        codec.release()
        runCatching { muxer.release() }
    }

    companion object {
        /**
         * The AAC encoder's start-up delay: one frame of 1024 samples at [sampleRate], in µs. The file carries it as leading
         * silence unless its edit list removes it (docs/qa/phase-3/engineer-hand-checks.md → P3-08 checks this with `ffprobe`).
         */
        fun primingUs(sampleRate: Int): Long = AAC_PRIMING_SAMPLES * MICROS_PER_SECOND / sampleRate

        private const val AAC_PRIMING_SAMPLES = 1024L
        private const val CHANNELS = 1
        private const val BIT_RATE = 96_000
        private const val BYTES_PER_SAMPLE = 2
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val TIMEOUT_US = 10_000L
        private const val EOS_TIMEOUT_US = 50_000L
        private const val MAX_EMPTY_POLLS = 40

        // 100 waits of 10 ms: a second.
        private const val MAX_INPUT_STALLS = 100
    }
}
