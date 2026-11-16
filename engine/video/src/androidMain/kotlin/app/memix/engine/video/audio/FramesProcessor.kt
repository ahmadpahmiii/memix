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
 * The base of the effects that change each frame without changing how many there are (filters, distortion, ring modulation, echo):
 * a buffer is read as floats, [process]ed in place and written back in the same encoding. Takes 16-bit and float PCM, what Media3's
 * audio graph feeds. A subclass keeps the state of its stream and clears it in [clearState], which runs on every flush (a seek).
 */
@OptIn(UnstableApi::class)
internal abstract class FramesProcessor : BaseAudioProcessor() {
    protected var sampleRate = 0
        private set
    protected var channelCount = 0
        private set
    private var samples = FloatArray(0)

    final override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (!PcmSamples.isSupported(inputAudioFormat.encoding)) throw UnhandledAudioFormatException(inputAudioFormat)
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        onFormat()
        return inputAudioFormat
    }

    final override fun onFlush(streamMetadata: StreamMetadata) {
        clearState(streamMetadata.positionOffsetUs * sampleRate / MICROS_PER_SECOND)
    }

    final override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val frameBytes = inputAudioFormat.bytesPerFrame
        val frameCount = remaining / frameBytes
        val output = replaceOutputBuffer(remaining)
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        if (frameCount == 0 || !startBuffer()) {
            output.put(input)
        } else {
            val sampleCount = frameCount * channelCount
            if (samples.size < sampleCount) samples = FloatArray(sampleCount)
            PcmSamples.read(input, inputAudioFormat.encoding, samples, sampleCount)
            process(samples, frameCount)
            PcmSamples.write(samples, sampleCount, inputAudioFormat.encoding, output)
            // A stray byte at the end can't be a sample; the buffer counts as read either way.
            input.position(input.limit())
        }
        output.flip()
    }

    /** The format is known: size the state for [sampleRate] and [channelCount]. */
    protected abstract fun onFormat()

    /** A flush: forget the sound so far. [positionFrames] is where in the stream the next frame is (0 at the start, the seek position after a seek). */
    protected abstract fun clearState(positionFrames: Long)

    /** Called once per buffer: read live settings here. Returns false when the buffer should pass unchanged (the effect is off). */
    protected open fun startBuffer(): Boolean = true

    /** Changes [frameCount] interleaved frames of [samples] in place. */
    protected abstract fun process(samples: FloatArray, frameCount: Int)

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
