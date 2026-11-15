package app.memix.engine.video

import app.memix.core.domain.video.AudioPeaks
import kotlin.math.abs

/**
 * Turns decoded 16-bit PCM into [AudioPeaks] (P3-09): the loudest sample, of any channel, in each 1/[peaksPerSecond] s. Feed it
 * the decoder's output in order with [add], then [finish]. Pure, so the same numbers come out on every platform and the
 * bucket maths can be checked on its own.
 */
internal class PeakAccumulator(
    private val sampleRate: Int,
    private val channelCount: Int,
    private val peaksPerSecond: Int = AudioPeaks.DEFAULT_PEAKS_PER_SECOND,
) {
    private var loudestPerPeak = IntArray(INITIAL_PEAKS)
    private var peakCount = 0
    private var framesRead = 0L

    /** Adds the first [sampleCount] values of [samples] (interleaved by channel, whole frames). */
    fun add(samples: ShortArray, sampleCount: Int) {
        var index = 0
        while (index + channelCount <= sampleCount) {
            val peak = (framesRead * peaksPerSecond / sampleRate).toInt()
            ensureRoomFor(peak)
            var loudest = loudestPerPeak[peak]
            for (channel in 0 until channelCount) {
                val level = abs(samples[index + channel].toInt())
                if (level > loudest) loudest = level
            }
            loudestPerPeak[peak] = loudest
            peakCount = maxOf(peakCount, peak + 1)
            framesRead++
            index += channelCount
        }
    }

    /** The peaks, scaled so the loudest one is 255; a silent file is all 0. */
    fun finish(): AudioPeaks {
        val loudest = (0 until peakCount).maxOfOrNull { loudestPerPeak[it] } ?: 0
        val levels = ByteArray(peakCount) { index ->
            if (loudest == 0) 0 else (loudestPerPeak[index] * MAX_LEVEL / loudest).toByte()
        }
        return AudioPeaks(peaksPerSecond, levels)
    }

    private fun ensureRoomFor(peak: Int) {
        if (peak >= loudestPerPeak.size) loudestPerPeak = loudestPerPeak.copyOf(maxOf(peak + 1, loudestPerPeak.size * 2))
    }

    private companion object {
        // A minute and a half at 100 a second; longer files double from here.
        const val INITIAL_PEAKS = 9_000
        const val MAX_LEVEL = 255
    }
}
