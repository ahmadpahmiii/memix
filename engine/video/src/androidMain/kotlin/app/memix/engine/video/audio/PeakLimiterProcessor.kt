package app.memix.engine.video.audio

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The end of the effects (spec P3-09 rule 7): [PeakLimiter] at [ceiling] (a linear level) so stacked effects never clip the file. The
 * limiter holds back a few ms of sound, so the output of a buffer is shorter than its input; the held sound comes out at the end of the
 * stream and the sound is as long as it was. [isLimiting] is the preview's gate: false leaves the sound untouched, and the export
 * builds this stage only when an effect that can raise the level is set.
 */
@OptIn(UnstableApi::class)
internal class PeakLimiterProcessor(private val ceiling: Float, private val isLimiting: () -> Boolean) : EndOfStreamProcessor() {
    private var limiter: PeakLimiter? = null
    private var samples = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (!PcmSamples.isSupported(inputAudioFormat.encoding)) throw UnhandledAudioFormatException(inputAudioFormat)
        limiter = PeakLimiter(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, ceiling, isLimiting)
        return inputAudioFormat
    }

    override fun clearState() {
        limiter?.reset()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val limiter = limiter ?: return
        val frameCount = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
        if (frameCount == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val sampleCount = frameCount * inputAudioFormat.channelCount
        if (samples.size < sampleCount) samples = FloatArray(sampleCount)
        PcmSamples.read(inputBuffer.order(ByteOrder.nativeOrder()), inputAudioFormat.encoding, samples, sampleCount)
        inputBuffer.position(inputBuffer.limit())
        val producedFrames = limiter.process(samples, frameCount)
        // A buffer with nothing in it (the line is still filling) is not an output: the old, empty one stays.
        if (producedFrames == 0) return
        val output = replaceOutputBuffer(producedFrames * inputAudioFormat.bytesPerFrame)
        PcmSamples.write(samples, producedFrames * inputAudioFormat.channelCount, inputAudioFormat.encoding, output)
        output.flip()
    }

    override fun fillFinalOutput(allocate: (byteCount: Int) -> ByteBuffer): ByteBuffer? {
        val limiter = limiter ?: return null
        val held = FloatArray(limiter.lookaheadFrames * inputAudioFormat.channelCount)
        val frames = limiter.drain(held)
        if (frames == 0) return null
        val output = allocate(frames * inputAudioFormat.bytesPerFrame)
        PcmSamples.write(held, frames * inputAudioFormat.channelCount, inputAudioFormat.encoding, output)
        output.flip()
        return output
    }
}
