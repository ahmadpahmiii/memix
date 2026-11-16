package app.memix.engine.video.audio

import app.memix.core.model.project.BiquadShape
import app.memix.core.model.project.BiquadSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A second-order filter (the audio EQ cookbook's shelves, high pass and low pass) over interleaved samples, one state per channel.
 * Pure Kotlin so Android and iOS run the same maths. [configure] may be called between buffers (the Bass slider moves live): the state
 * is kept, so a small step in gain makes no click. Not thread-safe; one instance per stream.
 */
internal class BiquadFilter(private val sampleRate: Int, private val channelCount: Int) {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private val z1 = DoubleArray(channelCount)
    private val z2 = DoubleArray(channelCount)

    fun configure(spec: BiquadSpec) {
        // A corner at or past the Nyquist frequency has no meaning; keep it just under.
        val w0 = 2.0 * PI * spec.frequencyHz.toDouble().coerceAtMost(sampleRate * MAX_CORNER_SHARE) / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * spec.quality)
        val a = 10.0.pow(spec.gainDb / DB_PER_AMPLITUDE_STEP)
        val shelfRoot = 2.0 * sqrt(a) * alpha
        val raw = when (spec.shape) {
            BiquadShape.LOW_PASS -> Coefficients((1 - cosW0) / 2, 1 - cosW0, (1 - cosW0) / 2, 1 + alpha, -2 * cosW0, 1 - alpha)
            BiquadShape.HIGH_PASS -> Coefficients((1 + cosW0) / 2, -(1 + cosW0), (1 + cosW0) / 2, 1 + alpha, -2 * cosW0, 1 - alpha)
            BiquadShape.LOW_SHELF -> Coefficients(
                a * ((a + 1) - (a - 1) * cosW0 + shelfRoot),
                2 * a * ((a - 1) - (a + 1) * cosW0),
                a * ((a + 1) - (a - 1) * cosW0 - shelfRoot),
                (a + 1) + (a - 1) * cosW0 + shelfRoot,
                -2 * ((a - 1) + (a + 1) * cosW0),
                (a + 1) + (a - 1) * cosW0 - shelfRoot,
            )
            BiquadShape.HIGH_SHELF -> Coefficients(
                a * ((a + 1) + (a - 1) * cosW0 + shelfRoot),
                -2 * a * ((a - 1) + (a + 1) * cosW0),
                a * ((a + 1) + (a - 1) * cosW0 - shelfRoot),
                (a + 1) - (a - 1) * cosW0 + shelfRoot,
                2 * ((a - 1) - (a + 1) * cosW0),
                (a + 1) - (a - 1) * cosW0 - shelfRoot,
            )
        }
        b0 = raw.b0 / raw.a0
        b1 = raw.b1 / raw.a0
        b2 = raw.b2 / raw.a0
        a1 = raw.a1 / raw.a0
        a2 = raw.a2 / raw.a0
    }

    /** Filters [frameCount] interleaved frames of [samples] in place. */
    fun process(samples: FloatArray, frameCount: Int) {
        for (frame in 0 until frameCount) {
            val base = frame * channelCount
            for (channel in 0 until channelCount) {
                val x = samples[base + channel].toDouble()
                val y = b0 * x + z1[channel]
                z1[channel] = b1 * x - a1 * y + z2[channel]
                z2[channel] = b2 * x - a2 * y
                samples[base + channel] = y.toFloat()
            }
        }
    }

    /** Forgets the sound filtered so far (a seek). */
    fun reset() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    private class Coefficients(val b0: Double, val b1: Double, val b2: Double, val a0: Double, val a1: Double, val a2: Double)

    private companion object {
        /** Shelf gains are amplitude ratios of the square root: dB / 40 (the cookbook's A). */
        const val DB_PER_AMPLITUDE_STEP = 40.0
        const val MAX_CORNER_SHARE = 0.45
    }
}
