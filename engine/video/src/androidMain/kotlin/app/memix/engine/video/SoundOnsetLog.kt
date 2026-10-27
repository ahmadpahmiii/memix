package app.memix.engine.video

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.memix.core.domain.Logger
import app.memix.core.domain.video.ExportSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * The preview's sound sync measure for hand checks (P1-08 Done when; builds with hand checks only): a pass-through
 * processor on the whole preview mix that logs each time sound starts after at least [SILENCE_BEFORE_US] of silence,
 * with the nearest sound start in the project, under the logcat tag `MemixPreview`:
 * `Sound onset: heard at 2.000 s, placed at 2.000 s, off by +0.3 ms (a frame is 33.3 ms)`.
 *
 * The time of each sample comes from Media3 itself: CompositionPlayer flushes the composition's audio processors with
 * the seek position (Media3 1.11.1 `AudioGraph.flush` → `StreamMetadata.positionOffsetUs`), and the samples after it
 * are counted from there. It measures where in the mixed audio the sound sits, which is what the project decides;
 * Media3 then shows each video frame by that same audio clock. The phone's own output delay (Bluetooth, for one) is
 * outside it, which the owner's ear check on the speaker covers.
 */
@OptIn(UnstableApi::class)
internal class SoundOnsetLog(
    /** Where each sound of the project starts on the timeline, µs. */
    private val soundStartsUs: LongArray,
    private val logger: Logger,
) : BaseAudioProcessor() {
    private var positionOffsetUs = 0L
    private var framesSinceFlush = 0L
    private var silentFrames = 0L

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Measures 16-bit and float PCM, the formats Media3 mixes in; anything else passes through unmeasured.
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        positionOffsetUs = streamMetadata.positionOffsetUs
        framesSinceFlush = 0
        // A sound already playing at the seek position isn't an onset, so silence has to come first.
        silentFrames = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        measure(inputBuffer.duplicate().order(ByteOrder.nativeOrder()))
        replaceOutputBuffer(remaining).put(inputBuffer).flip()
    }

    private fun measure(samples: ByteBuffer) {
        val format = inputAudioFormat
        val bytesPerSample = when (format.encoding) {
            C.ENCODING_PCM_16BIT -> Short.SIZE_BYTES
            C.ENCODING_PCM_FLOAT -> Float.SIZE_BYTES
            else -> return
        }
        val silenceNeeded = SILENCE_BEFORE_US * format.sampleRate / MICROS_PER_SECOND
        while (samples.remaining() >= format.bytesPerFrame) {
            var loudest = 0f
            repeat(format.channelCount) {
                val level = if (bytesPerSample == Short.SIZE_BYTES) abs(samples.short.toFloat()) / Short.MAX_VALUE else abs(samples.float)
                if (level > loudest) loudest = level
            }
            if (loudest < THRESHOLD) {
                silentFrames++
            } else {
                if (silentFrames >= silenceNeeded) logOnset(positionOffsetUs + framesSinceFlush * MICROS_PER_SECOND / format.sampleRate)
                silentFrames = 0
            }
            framesSinceFlush++
        }
    }

    private fun logOnset(heardAtUs: Long) {
        val placedAtUs = soundStartsUs.minByOrNull { abs(it - heardAtUs) }
        if (placedAtUs == null) {
            logger.debug(TAG, "Sound onset: heard at ${seconds(heardAtUs)}, no sound placed in the project (a clip's own sound)")
            return
        }
        val offByUs = heardAtUs - placedAtUs
        val sign = if (offByUs >= 0) "+" else "-"
        logger.debug(
            TAG,
            "Sound onset: heard at ${seconds(heardAtUs)}, placed at ${seconds(placedAtUs)}, off by $sign${millis(abs(offByUs))} " +
                "(a frame is ${millis(FRAME_US)})",
        )
    }

    // "2.000 s" and "0.3 ms", without String.format's locale.
    private fun seconds(us: Long): String = "${us / MICROS_PER_SECOND}.${(us % MICROS_PER_SECOND / MICROS_PER_MILLI).toString().padStart(3, '0')} s"

    private fun millis(us: Long): String = "${us / MICROS_PER_MILLI}.${us % MICROS_PER_MILLI / TENTH_OF_MILLI_US} ms"

    private companion object {
        const val TAG = "MemixPreview"

        /** -40 dBFS: quieter counts as silence. */
        const val THRESHOLD = 0.01f

        /** Silence needed before a sound counts as starting: 50 ms. */
        const val SILENCE_BEFORE_US = 50_000L

        const val MICROS_PER_SECOND = 1_000_000L
        const val MICROS_PER_MILLI = 1_000L
        const val TENTH_OF_MILLI_US = 100L
        const val FRAME_US = MICROS_PER_SECOND / ExportSettings.DEFAULT_FRAME_RATE
    }
}
