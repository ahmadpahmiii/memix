package app.memix.engine.video.audio

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lets the sound ring out after the clip (spec P3-09 → Echo, the tail): passes the sound through, then at the end of the stream adds
 * [tailUs] of silence (rounded up to a whole frame), which the effects after it (the echo's line, a voice's room) turn into their last repeats.
 * The item is that much longer in the composition, which Media3 learns from [getDurationAfterProcessorApplied]; the planner has already
 * shortened the tail where the video ends first, so nothing runs past the end of the video. The silence is handed out in chunks of
 * [CHUNK_US] from one reused buffer, so a 1.5 s tail never needs a 1.5 s allocation.
 *
 * Media3 also maps the stream's start position through that method when it flushes the chain, so every processor after this one counts
 * positions from [tailUs] on ([app.memix.engine.video.FadeGains] and [RingModulationProcessor] take it out again).
 */
@OptIn(UnstableApi::class)
internal class TailProcessor(private val tailUs: Long) : BaseAudioProcessor() {
    private var chunk: ByteBuffer = ByteBuffer.allocateDirect(0)
    private var tailFramesLeft = 0L
    private var tailStarted = false

    override fun getDurationAfterProcessorApplied(durationUs: Long): Long = durationUs + tailUs

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (!PcmSamples.isSupported(inputAudioFormat.encoding)) throw UnhandledAudioFormatException(inputAudioFormat)
        // Direct buffers start as zeros, and zeros are the same bytes in 16-bit and float.
        chunk = ByteBuffer.allocateDirect(chunkFrames(inputAudioFormat) * inputAudioFormat.bytesPerFrame).order(ByteOrder.nativeOrder())
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        replaceOutputBuffer(remaining).put(inputBuffer).flip()
    }

    // Media3 repeats the end-of-stream call on every pass; the tail starts once.
    override fun onQueueEndOfStream() {
        if (tailStarted) return
        tailStarted = true
        tailFramesLeft = (tailUs * inputAudioFormat.sampleRate + MICROS_PER_SECOND - 1) / MICROS_PER_SECOND
    }

    // The sound first, then the silence a chunk at a time. Media3 asks again only after it has read the chunk, so one buffer is enough.
    override fun getOutput(): ByteBuffer {
        val regular = super.getOutput()
        if (regular.hasRemaining() || tailFramesLeft <= 0) return regular
        val frames = minOf(tailFramesLeft, chunkFrames(inputAudioFormat).toLong()).toInt()
        tailFramesLeft -= frames
        chunk.clear()
        chunk.limit(frames * inputAudioFormat.bytesPerFrame)
        return chunk
    }

    override fun isEnded(): Boolean = super.isEnded() && tailFramesLeft <= 0

    override fun onFlush(streamMetadata: StreamMetadata) {
        tailFramesLeft = 0
        tailStarted = false
    }

    override fun onReset() {
        tailFramesLeft = 0
        tailStarted = false
        chunk = ByteBuffer.allocateDirect(0)
    }

    private fun chunkFrames(format: AudioFormat): Int = (format.sampleRate * CHUNK_US / MICROS_PER_SECOND).toInt().coerceAtLeast(1)

    private companion object {
        const val CHUNK_US = 50_000L
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
