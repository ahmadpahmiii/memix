package app.memix.engine.video.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * A lookahead peak limiter over interleaved samples, the same on every platform: no sample of its output is louder than [ceiling] (a
 * linear level, 1 is full scale) while [isLimiting] is true, however the effects before it stack up.
 *
 * Each frame's needed gain (ceiling over its loudest channel, channels linked so the stereo image holds) is looked at
 * [LOOKAHEAD_US] before the frame is played: the lowest need in the window ahead is held, released slowly ([RELEASE_US], so a bass
 * wave isn't chopped), then averaged over the window, which turns the drop into a ramp that is finished before the peak arrives.
 * That costs [lookaheadFrames] of delay inside the limiter; it is paid back at the end of the stream ([drain]) and nothing shifts.
 * While [isLimiting] is false the gain stays 1 and the sound passes unchanged (the preview's gate, so a sound with no effect set is not touched).
 *
 * Not thread-safe; one instance per stream. [isLimiting] is read once per buffer.
 */
internal class PeakLimiter(sampleRate: Int, private val channelCount: Int, private val ceiling: Float, private val isLimiting: () -> Boolean) {
    /** Frames the limiter holds back; [process] returns this many fewer frames than it gets until [drain]. */
    val lookaheadFrames = max(1, (sampleRate * LOOKAHEAD_US / MICROS_PER_SECOND).toInt())
    private val releaseStep = (1.0 - exp(-1.0 / (sampleRate * RELEASE_US / MICROS_PER_SECOND))).toFloat()

    private val waiting = FloatArray(lookaheadFrames * channelCount)
    private val recentGains = FloatArray(lookaheadFrames) { 1f }
    private var recentGainSum = lookaheadFrames.toDouble()
    private var recentPosition = 0
    private var waitingPosition = 0
    private var previousGain = 1f

    // The lowest need over the last lookaheadFrames + 1 frames, as a monotonic queue: needs rise from the front to the back.
    private val queueCapacity = lookaheadFrames + 2
    private val queueNeeds = FloatArray(queueCapacity)
    private val queueFrames = LongArray(queueCapacity)
    private var queueHead = 0
    private var queueSize = 0

    private val incoming = FloatArray(channelCount)
    private var framesPushed = 0L
    private var framesIn = 0L
    private var framesOut = 0L
    private var limiting = true

    /**
     * Limits [frameCount] interleaved frames of [samples] in place and returns how many frames came out: the first call
     * after a [reset] returns [lookaheadFrames] fewer than it was given. The output is the input from [lookaheadFrames] earlier.
     */
    fun process(samples: FloatArray, frameCount: Int): Int {
        limiting = isLimiting()
        var written = 0
        for (frame in 0 until frameCount) {
            // The frame is copied before anything is written: the output index never passes the input index, but may equal it.
            samples.copyInto(incoming, 0, frame * channelCount, (frame + 1) * channelCount)
            if (push(samples, written * channelCount)) written++
        }
        framesIn += frameCount
        return written
    }

    /** The frames still held back at the end of the stream, into [out] (room for [lookaheadFrames]); returns how many. */
    fun drain(out: FloatArray): Int {
        incoming.fill(0f)
        var written = 0
        while (framesOut < framesIn) {
            if (push(out, written * channelCount)) written++
        }
        return written
    }

    /** Forgets the sound held (a seek). */
    fun reset() {
        waiting.fill(0f)
        recentGains.fill(1f)
        recentGainSum = lookaheadFrames.toDouble()
        recentPosition = 0
        waitingPosition = 0
        previousGain = 1f
        queueHead = 0
        queueSize = 0
        framesPushed = 0
        framesIn = 0
        framesOut = 0
    }

    // Takes the frame in [incoming]; once the line is full, writes the frame from lookaheadFrames ago to [outBase] of [out].
    private fun push(out: FloatArray, outBase: Int): Boolean {
        // At rest (not limiting and every recent gain is 1) the sound only passes through the delay: no need to track.
        val gain = if (!limiting && previousGain == 1f && recentGainSum == lookaheadFrames.toDouble()) 1f else trackedGain()

        val ready = framesPushed >= lookaheadFrames
        val waitingBase = waitingPosition * channelCount
        if (ready) {
            for (channel in 0 until channelCount) {
                val shaped = waiting[waitingBase + channel] * gain
                // The averaging already guarantees the ceiling; the clamp only catches rounding.
                val sample = if (limiting) shaped.coerceIn(-ceiling, ceiling) else shaped
                out[outBase + channel] = sample
            }
        }
        for (channel in 0 until channelCount) waiting[waitingBase + channel] = incoming[channel]
        waitingPosition = (waitingPosition + 1) % lookaheadFrames
        framesPushed++
        if (ready) framesOut++
        return ready
    }

    // The gain for the frame in [incoming]: the lowest need ahead, released, averaged over the window.
    private fun trackedGain(): Float {
        var peak = 0f
        for (channel in 0 until channelCount) peak = max(peak, abs(incoming[channel]))
        val need = if (limiting && peak > ceiling) ceiling / peak else 1f
        val held = lowestNeedAhead(need)
        val released = previousGain + (1f - previousGain) * releaseStep
        previousGain = min(held, if (released > RELEASED_FULLY) 1f else released)
        recentGainSum += previousGain - recentGains[recentPosition]
        recentGains[recentPosition] = previousGain
        recentPosition = (recentPosition + 1) % lookaheadFrames
        return (recentGainSum / lookaheadFrames).toFloat()
    }

    private fun lowestNeedAhead(need: Float): Float {
        while (queueSize > 0 && queueNeeds[(queueHead + queueSize - 1) % queueCapacity] >= need) queueSize--
        val tail = (queueHead + queueSize) % queueCapacity
        queueNeeds[tail] = need
        queueFrames[tail] = framesPushed
        queueSize++
        while (queueFrames[queueHead] < framesPushed - lookaheadFrames) {
            queueHead = (queueHead + 1) % queueCapacity
            queueSize--
        }
        return queueNeeds[queueHead]
    }

    private companion object {
        const val LOOKAHEAD_US = 5_000L
        const val RELEASE_US = 100_000L
        const val MICROS_PER_SECOND = 1_000_000L

        /** A release this close to 1 counts as done, so a gain that has recovered is exactly 1 and the sound passes bit for bit. */
        const val RELEASED_FULLY = 0.999999f
    }
}
