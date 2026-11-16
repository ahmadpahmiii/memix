package app.memix.engine.video.audio

import kotlin.math.tanh

/**
 * Distortion by waveshaping: the signal times [gain], bent by tanh, then divided by tanh(gain) so a full-scale input still ends at full
 * scale. Gain 1 is not clean (tanh bends even there), so the callers skip the shaper at gain 1. Quiet sound is lifted a lot at high gains:
 * that is the "deep-fried" sound, and the limiter after it keeps the file from clipping.
 */
internal object SoftClip {
    fun process(samples: FloatArray, sampleCount: Int, gain: Float) {
        val makeup = 1.0 / tanh(gain.toDouble())
        for (index in 0 until sampleCount) {
            samples[index] = (tanh(samples[index] * gain.toDouble()) * makeup).toFloat()
        }
    }
}
