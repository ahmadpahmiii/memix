package app.memix.engine.video.audio

import app.memix.core.model.project.EchoSpec
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max

/**
 * A feedback delay line over interleaved samples: the output is the sound plus [EchoSpec.wet] of what came out of the line
 * [EchoSpec.delayUs] ago, and [EchoSpec.feedback] of that goes back in, so the repeats fade. With [EchoSpec.dampingHz] each
 * pass through the line loses highs (a dull room). No latency: the first frame out is the first frame in. The line's content is the
 * sound before this moment, so a seek clears it ([reset]) and the repeats of what was said before the seek are not heard.
 */
internal class EchoLine(private val spec: EchoSpec, sampleRate: Int, private val channelCount: Int) {
    private val delayFrames = max(1, ((spec.delayUs * sampleRate + MICROS_PER_SECOND / 2) / MICROS_PER_SECOND).toInt())
    private val line = FloatArray(delayFrames * channelCount)
    private val damped = FloatArray(channelCount)
    private val dampingStep = if (spec.dampingHz > 0f) (1.0 - exp(-2.0 * PI * spec.dampingHz / sampleRate)).toFloat() else 1f
    private var position = 0

    /** The echo of [frameCount] interleaved frames of [samples], in place. */
    fun process(samples: FloatArray, frameCount: Int) {
        for (frame in 0 until frameCount) {
            val base = frame * channelCount
            val lineBase = position * channelCount
            for (channel in 0 until channelCount) {
                val repeat = line[lineBase + channel]
                damped[channel] += dampingStep * (repeat - damped[channel])
                val x = samples[base + channel]
                samples[base + channel] = x + spec.wet * damped[channel]
                line[lineBase + channel] = x + spec.feedback * damped[channel]
            }
            position = (position + 1) % delayFrames
        }
    }

    fun reset() {
        line.fill(0f)
        damped.fill(0f)
        position = 0
    }

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
