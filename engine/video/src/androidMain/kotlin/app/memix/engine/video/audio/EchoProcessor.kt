package app.memix.engine.video.audio

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.memix.core.model.project.EchoSpec

/** [EchoLine] in the chain. The line is cleared by a flush, so the first repeats after a seek are those of the sound played since. */
@OptIn(UnstableApi::class)
internal class EchoProcessor(private val spec: EchoSpec) : FramesProcessor() {
    private var line: EchoLine? = null

    override fun onFormat() {
        line = EchoLine(spec, sampleRate, channelCount)
    }

    override fun clearState(positionFrames: Long) {
        line?.reset()
    }

    override fun process(samples: FloatArray, frameCount: Int) {
        line?.process(samples, frameCount)
    }
}
