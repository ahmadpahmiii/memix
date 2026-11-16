package app.memix.engine.video.audio

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

/** The distortion gain, asked once per buffer on the audio thread; a plain interface so a Float is never boxed. */
internal fun interface GainSource {
    fun gain(): Float
}

/** [SoftClip] in the chain. [gain] 1 or less passes the sound through unchanged (distortion off). */
@OptIn(UnstableApi::class)
internal class DistortionProcessor(private val gain: GainSource) : FramesProcessor() {
    private var current = 1f

    override fun onFormat() = Unit

    override fun clearState(positionFrames: Long) = Unit

    override fun startBuffer(): Boolean {
        current = gain.gain()
        return current > 1f
    }

    override fun process(samples: FloatArray, frameCount: Int) {
        SoftClip.process(samples, frameCount * channelCount, current)
    }
}
