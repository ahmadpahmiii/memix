package app.memix.engine.video.audio

import app.memix.core.model.project.RingModulation
import kotlin.math.PI
import kotlin.math.sin

/**
 * The robot's buzz: each frame times a sine, [RingModulation.mix] of it against the plain sound. The sine is a function of the frame's
 * position in the stream, not of what was processed before, so a preview that starts at a seek and an export that runs from the start
 * hear the same carrier at the same moment.
 */
internal class RingModulator(private val spec: RingModulation, private val sampleRate: Int, private val channelCount: Int) {
    private var positionFrames = 0L

    /** Where in the stream the next frame is, after a flush (0 at the start, the seek position after a seek). */
    fun moveTo(frame: Long) {
        positionFrames = frame
    }

    fun process(samples: FloatArray, frameCount: Int) {
        val radiansPerFrame = 2.0 * PI * spec.frequencyHz / sampleRate
        for (frame in 0 until frameCount) {
            val carrier = sin(radiansPerFrame * (positionFrames + frame))
            val gain = (1.0 - spec.mix + spec.mix * carrier).toFloat()
            val base = frame * channelCount
            for (channel in 0 until channelCount) samples[base + channel] *= gain
        }
        positionFrames += frameCount
    }
}
