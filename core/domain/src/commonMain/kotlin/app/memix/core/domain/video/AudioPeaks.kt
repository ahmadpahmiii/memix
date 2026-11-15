package app.memix.core.domain.video

import kotlin.math.ceil
import kotlin.math.floor

/**
 * The shape of a sound file for the timeline's waveform (P3-09): its loudest sample in each 1/[peaksPerSecond] s of the whole
 * source file, scaled so the loudest of the file is 1 (every sound shows its own shape, like the P3-03 waveform). The engine
 * decodes them once per file and keeps them; the timeline asks for the stretch a clip plays ([peakBetween]).
 *
 * [levels] hold one byte per peak, 0 to 255, in source time order.
 */
class AudioPeaks(val peaksPerSecond: Int, private val levels: ByteArray) {
    /** How many peaks the file has. */
    val count: Int get() = levels.size

    /** The raw bytes, for the engine to keep on disk. */
    fun levelBytes(): ByteArray = levels.copyOf()

    /**
     * The loudest peak from [startUs] to [endUs] of the source, 0 to 1; 0 outside the file. A range shorter than one peak
     * still reads the peak it falls in.
     */
    fun peakBetween(startUs: Long, endUs: Long): Float {
        if (levels.isEmpty() || endUs < startUs) return 0f
        val first = floor(startUs * peaksPerSecond / MICROS_PER_SECOND).toInt().coerceAtLeast(0)
        // A decode a few ms shorter than the clip's measured length leaves the last bars past the peaks: nothing there.
        if (first >= levels.size) return 0f
        val last = ceil(endUs * peaksPerSecond / MICROS_PER_SECOND).toInt().coerceIn(first + 1, levels.size)
        var loudest = 0
        for (index in first until last) loudest = maxOf(loudest, levels[index].toInt() and BYTE_MASK)
        return loudest / MAX_LEVEL
    }

    /** The peaks of a range, for checks by eye. */
    override fun toString(): String = "AudioPeaks($count at $peaksPerSecond/s)"

    companion object {
        /** About 100 a second (spec P3-09 → Waveforms on the timeline → Peaks). */
        const val DEFAULT_PEAKS_PER_SECOND = 100
        private const val MICROS_PER_SECOND = 1_000_000.0
        private const val BYTE_MASK = 0xFF
        private const val MAX_LEVEL = 255f
    }
}
