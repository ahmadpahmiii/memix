package app.memix.engine.video.audio

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.memix.core.model.project.BiquadSpec

/**
 * One filter ([BiquadFilter]) in the chain. [spec] is asked once per buffer: null passes the sound through unchanged, a new value
 * retunes the filter on the spot (the preview's Bass slider moves it while it plays); the export's value never changes.
 */
@OptIn(UnstableApi::class)
internal class FilterProcessor(private val spec: () -> BiquadSpec?) : FramesProcessor() {
    private var filter: BiquadFilter? = null
    private var current: BiquadSpec? = null

    override fun onFormat() {
        filter = BiquadFilter(sampleRate, channelCount)
        current = null
    }

    override fun clearState(positionFrames: Long) {
        filter?.reset()
    }

    override fun startBuffer(): Boolean {
        val wanted = spec()
        if (wanted == null) {
            // Switched off: forget what the filter held, so switching it on again starts clean.
            if (current != null) filter?.reset()
            current = null
            return false
        }
        if (wanted != current) {
            filter?.configure(wanted)
            current = wanted
        }
        return true
    }

    override fun process(samples: FloatArray, frameCount: Int) {
        filter?.process(samples, frameCount)
    }
}
