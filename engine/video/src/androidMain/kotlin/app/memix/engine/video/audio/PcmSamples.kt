package app.memix.engine.video.audio

import androidx.media3.common.C
import java.nio.ByteBuffer

/** Moves PCM between a [ByteBuffer] (16-bit or float, native order) and the interleaved floats the effect kernels work on. */
internal object PcmSamples {
    fun isSupported(encoding: Int): Boolean = encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT

    /** Reads [sampleCount] samples from [input]'s position into [out] as -1..1 floats. */
    fun read(input: ByteBuffer, encoding: Int, out: FloatArray, sampleCount: Int) {
        if (encoding == C.ENCODING_PCM_16BIT) {
            for (index in 0 until sampleCount) out[index] = input.short / SHORT_SCALE
        } else {
            for (index in 0 until sampleCount) out[index] = input.float
        }
    }

    /** Writes [sampleCount] floats to [output]; 16-bit output is rounded and clamped, so a sample past full scale clips instead of wrapping. */
    fun write(samples: FloatArray, sampleCount: Int, encoding: Int, output: ByteBuffer) {
        if (encoding == C.ENCODING_PCM_16BIT) {
            for (index in 0 until sampleCount) {
                output.putShort(Math.round(samples[index] * SHORT_SCALE).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        } else {
            for (index in 0 until sampleCount) output.putFloat(samples[index])
        }
    }

    private const val SHORT_SCALE = 32768f
}
