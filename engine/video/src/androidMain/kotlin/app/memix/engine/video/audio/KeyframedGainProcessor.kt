package app.memix.engine.video.audio

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.memix.core.model.project.Keyframe

/**
 * The volume of an item with keyframes (P4-02): every sample frame is multiplied by the level [KeyframedGain] gives for its own
 * time (the keyframes' volume curve, joined linearly across blocks of 64 frames), so a ramp is smooth to the sample, with no step and no click, and the preview and the export play the same curve. Media3's own
 * `GainProcessor` only takes gains from 0 to 1, and Memix volumes go to 200%, hence this stage; samples past full scale are clipped
 * like the committed volume's. Time counts from the item's start; [tailUs] is the tail added before this processor, which shifts the
 * positions Media3 flushes with and is taken out here ([RingModulationProcessor] does the same).
 */
@OptIn(UnstableApi::class)
internal class KeyframedGainProcessor(
    private val keyframes: List<Keyframe>,
    private val tailUs: Long = 0,
) : FramesProcessor() {
    private var gain: KeyframedGain? = null
    private var nextFrame = 0L

    override fun onFormat() {
        gain = KeyframedGain(keyframes, sampleRate)
    }

    // Media3 flushes with the position inside this item (hand check P4-02a, read from 1.11.1: AudioGraphInputAudioSink passes presentationTimeUs - offsetToEditedMediaItemStartUs, and an export item change passes 0), so a clip later in a sequence needs no correction.
    override fun clearState(positionFrames: Long) {
        nextFrame = positionFrames - tailUs * sampleRate / MICROS_PER_SECOND
    }

    override fun process(samples: FloatArray, frameCount: Int) {
        gain?.apply(samples, frameCount, channelCount, nextFrame)
        nextFrame += frameCount
    }

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
