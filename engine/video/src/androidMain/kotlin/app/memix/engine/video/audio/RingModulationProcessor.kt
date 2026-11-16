package app.memix.engine.video.audio

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.memix.core.model.project.RingModulation

/**
 * [RingModulator] in the chain: the carrier follows the stream's position, also after a seek. [tailUs] is the tail added before this
 * processor ([TailProcessor] shifts the positions Media3 flushes with by it); it is taken out, so the carrier follows the clip's own time.
 */
@OptIn(UnstableApi::class)
internal class RingModulationProcessor(private val spec: RingModulation, private val tailUs: Long = 0) : FramesProcessor() {
    private var modulator: RingModulator? = null

    override fun onFormat() {
        modulator = RingModulator(spec, sampleRate, channelCount)
    }

    override fun clearState(positionFrames: Long) {
        modulator?.moveTo(positionFrames - tailUs * sampleRate / MICROS_PER_SECOND)
    }

    override fun process(samples: FloatArray, frameCount: Int) {
        modulator?.process(samples, frameCount)
    }

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
