package app.memix.engine.video.audio

import app.memix.core.domain.project.volumeAt
import app.memix.core.model.project.Keyframe
import kotlin.math.floor

/**
 * The gain of an item with keyframed volume (P4-02), per sample frame, shared by the Android processor and the iOS audio mix. The curve
 * ([volumeAt]) is evaluated at the edges of blocks of [BLOCK_FRAMES] frames, aligned to the stream's frame numbers, and the gain is
 * linear between the two edges of a block: the ramp is continuous to the sample (no click, a hold's jump becomes a ramp of about a
 * millisecond), costs one curve evaluation per block instead of one per sample, and doesn't depend on where Media3 cuts its buffers or
 * where a seek landed. Not thread-safe: one instance per stream, used by the audio thread.
 */
internal class KeyframedGain(private val keyframes: List<Keyframe>, private val sampleRate: Int) {
    private var block = Long.MIN_VALUE
    private var startGain = 1f
    private var endGain = 1f

    /** Multiplies [frameCount] interleaved frames of [samples] by the gain of their own time; [firstFrame] is the stream position of the first one. */
    fun apply(samples: FloatArray, frameCount: Int, channelCount: Int, firstFrame: Long) {
        for (frame in 0 until frameCount) {
            val position = firstFrame + frame
            val gain = gainAt(position)
            val first = frame * channelCount
            for (index in first until first + channelCount) samples[index] = (samples[index] * gain).coerceIn(-1f, 1f)
        }
    }

    /** The gain at stream frame [position]. */
    fun gainAt(position: Long): Float {
        val wanted = floor(position.toDouble() / BLOCK_FRAMES).toLong()
        if (wanted != block) {
            // Moving on to the next block reuses its start edge, which was the last block's end.
            startGain = if (wanted == block + 1) endGain else edgeGain(wanted * BLOCK_FRAMES)
            endGain = edgeGain((wanted + 1) * BLOCK_FRAMES)
            block = wanted
        }
        val fraction = (position - wanted * BLOCK_FRAMES).toFloat() / BLOCK_FRAMES
        return startGain + (endGain - startGain) * fraction
    }

    private fun edgeGain(frame: Long): Float = volumeAt(keyframes, frame * MICROS_PER_SECOND / sampleRate)

    companion object {
        /** About 1.3 ms at 48 kHz. */
        const val BLOCK_FRAMES = 64
        private const val MICROS_PER_SECOND = 1_000_000L
    }
}
