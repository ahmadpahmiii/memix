package app.memix.engine.video.audio

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The base of the processors that have more sound to give when the stream ends (the limiter's held frames).
 * Media3 tells the next processor the stream has ended while this processor's last regular output may not have been read yet,
 * and [replaceOutputBuffer] would reuse and overwrite that buffer. So the final sound is kept in a buffer of its own and handed out after
 * the regular output, and the processor only counts as ended once it is gone.
 */
@OptIn(UnstableApi::class)
internal abstract class EndOfStreamProcessor : BaseAudioProcessor() {
    private var finalOutput: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var finalBuilt = false

    /** The sound that comes after everything else, [byteCount] bytes of a native-order buffer ready to be filled; null or empty when there is none. */
    protected abstract fun fillFinalOutput(allocate: (byteCount: Int) -> ByteBuffer): ByteBuffer?

    // Media3 repeats the end-of-stream call on every pass until the stream is read to the end; the final sound is built once.
    final override fun onQueueEndOfStream() {
        if (finalBuilt) return
        finalBuilt = true
        finalOutput = fillFinalOutput { byteCount -> ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder()) } ?: AudioProcessor.EMPTY_BUFFER
    }

    final override fun getOutput(): ByteBuffer {
        val regular = super.getOutput()
        if (regular.hasRemaining() || !finalOutput.hasRemaining()) return regular
        return finalOutput.also { finalOutput = AudioProcessor.EMPTY_BUFFER }
    }

    final override fun isEnded(): Boolean = super.isEnded() && !finalOutput.hasRemaining()

    protected abstract fun clearState()

    final override fun onReset() {
        finalOutput = AudioProcessor.EMPTY_BUFFER
        finalBuilt = false
        clearState()
    }

    final override fun onFlush(streamMetadata: StreamMetadata) {
        finalOutput = AudioProcessor.EMPTY_BUFFER
        finalBuilt = false
        clearState()
    }
}
