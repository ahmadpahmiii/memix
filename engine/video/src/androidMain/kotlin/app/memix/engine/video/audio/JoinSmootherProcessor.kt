package app.memix.engine.video.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.SpeedChangingAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.SpeedProviderUtil
import androidx.media3.common.util.UnstableApi
import kotlin.math.PI
import kotlin.math.cos

/**
 * Takes the clicks out of a speed curve's sound (P4-09b). Media3's speed processor stretches each piece of a curve on its own and restarts at every change,
 * so the waves of two pieces don't meet (the JVM test measured steps of up to 62% of full scale at the joins), and the end of a piece is not clean either:
 * its time stretch is flushed against padding, which leaves a glitch up to about 4 ms before the join, and up to about 21 ms before it after a piece slower
 * than [SLOW_PIECE]. Media3 has no crossfade and the pieces cannot overlap, so this stage runs right after it and brings the level down to nothing around
 * every join and back up with a raised cosine. The joins are where Media3's own processor puts them for [speedProvider] (its sample counts, not the video's
 * microseconds, since Sonic's output of a piece is not exactly its length over the speed). Time counts from the item's start; Media3 flushes with the
 * position inside the item, so a seek lands at the right place ([KeyframedGainProcessor] relies on the same).
 */
@OptIn(UnstableApi::class)
internal class JoinSmootherProcessor(private val speedProvider: SpeedProvider) : FramesProcessor() {
    private var dips = emptyList<Dip>()  // an ArrayList: indexed, not iterated
    private var nextFrame = 0L

    /** A join at [frame]: silent from [holdBefore] frames before it to [holdAfter] after, rising over [rampBefore] and [rampAfter] frames outside that. */
    private class Dip(val frame: Long, val holdBefore: Int, val rampBefore: Int, val holdAfter: Int, val rampAfter: Int) {
        val start: Long get() = frame - holdBefore - rampBefore
        val end: Long get() = frame + holdAfter + rampAfter

        fun gainAt(position: Long): Float {
            val distance = position - frame
            return when {
                distance in -holdBefore..holdAfter -> 0f
                distance < 0 -> rise((-distance - holdBefore).toFloat() / rampBefore)
                else -> rise((distance - holdAfter).toFloat() / rampAfter)
            }
        }

        private fun rise(progress: Float): Float = 0.5f * (1f - cos(PI * progress).toFloat())
    }

    override fun onFormat() {
        dips = dipsOf(speedProvider, sampleRate)
    }

    override fun clearState(positionFrames: Long) {
        nextFrame = positionFrames
    }

    override fun process(samples: FloatArray, frameCount: Int) {
        val endFrame = nextFrame + frameCount
        for (index in dips.indices) {
            val dip = dips[index]
            if (dip.end <= nextFrame || dip.start >= endFrame) continue
            for (frame in maxOf(nextFrame, dip.start) until minOf(endFrame, dip.end)) {
                val gain = dip.gainAt(frame)
                val base = ((frame - nextFrame) * channelCount).toInt()
                for (channel in 0 until channelCount) samples[base + channel] *= gain
            }
        }
        nextFrame = endFrame
    }

    // The output frame at which each piece after the first starts, with the dip its slow or fast predecessor needs.
    private fun dipsOf(provider: SpeedProvider, sampleRate: Int): List<Dip> {
        val dips = ArrayList<Dip>()
        var inputFrame = 0L
        while (true) {
            val next = SpeedProviderUtil.getNextSpeedChangeSamplePosition(provider, inputFrame, sampleRate)
            if (next == C.INDEX_UNSET.toLong()) break
            val speedBefore = SpeedProviderUtil.getSampleAlignedSpeed(provider, inputFrame, sampleRate)
            val frame = SpeedChangingAudioProcessor.getSampleCountAfterProcessorApplied(provider, sampleRate, next)
            val slow = speedBefore < SLOW_PIECE
            dips.add(
                Dip(
                    frame,
                    holdBefore = frames(if (slow) SLOW_HOLD_MS else HOLD_MS, sampleRate),
                    rampBefore = frames(RAMP_MS, sampleRate),
                    holdAfter = frames(HOLD_AFTER_MS, sampleRate),
                    rampAfter = frames(RAMP_AFTER_MS, sampleRate),
                ),
            )
            inputFrame = next
        }
        return dips
    }

    private fun frames(milliseconds: Int, sampleRate: Int): Int = sampleRate * milliseconds / MILLIS_PER_SECOND

    private companion object {
        /** A piece slower than this ends with its glitch further from the join. */
        const val SLOW_PIECE = 0.45f
        const val HOLD_MS = 4
        const val SLOW_HOLD_MS = 18
        const val RAMP_MS = 6
        const val HOLD_AFTER_MS = 1
        const val RAMP_AFTER_MS = 3
        const val MILLIS_PER_SECOND = 1000
    }
}
