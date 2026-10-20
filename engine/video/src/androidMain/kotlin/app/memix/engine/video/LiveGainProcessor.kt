package app.memix.engine.video

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A gain the preview can change while it plays, so a Volume slider is heard as it moves (P1-08, P1-09) without
 * rebuilding the composition. One per item with sound in a preview composition (a sound, or a main clip playing its
 * own sound); [factor] multiplies the level the composition already applies (1 changes nothing). An item committed at
 * 0% has no volume processor, so its factor is the slider's level itself and starts at 0 (silent). Samples past full
 * scale are clamped, as Media3's ChannelMixingAudioProcessor does for the committed volume. The player's audio thread
 * reads [factor]; the main thread writes it.
 */
@OptIn(UnstableApi::class)
internal class LiveGainProcessor : BaseAudioProcessor() {
    @Volatile
    var factor = 1f

    // Active even at 1, so it's in place before the slider first moves.
    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat = inputAudioFormat

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining)
        val gain = factor
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        when {
            gain == 1f -> output.put(input)
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT -> while (input.remaining() >= Short.SIZE_BYTES) {
                output.putShort((input.short * gain).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort())
            }
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT -> while (input.remaining() >= Float.SIZE_BYTES) {
                output.putFloat((input.float * gain).coerceIn(-1f, 1f))
            }
            // Other encodings aren't mixed by Media3's audio graph; they pass through unchanged.
            else -> output.put(input)
        }
        // A stray byte at the end can't be a sample; the buffer counts as read either way.
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }
}
