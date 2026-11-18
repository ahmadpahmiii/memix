package app.memix.engine.video

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.SpeedParameters
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import app.memix.core.model.project.Retime
import app.memix.core.model.project.SpeedSegments

/**
 * A clip's speed for the Media3 composition (P4-09a). [setSpeed][EditedMediaItem.Builder.setSpeed] with a [SpeedProvider] is the one hook that works
 * the same in CompositionPlayer and Transformer: both wrap the clip's source in `SpeedChangingMediaSource` (video and audio timestamps mapped through
 * the provider) and run `SpeedChangingAudioProcessor` on the sound; with [SpeedParameters.shouldMaintainPitch] set the sound is time-stretched, not
 * resampled, like P3-09's speed (spec P4-09 → The video clip's own sound, Keep). Checked against the Media3 1.11.1 source (docs/TECHNICAL_DESIGN.md → Speed).
 *
 * The provider's time is counted from the clip's start in the source (the clipping start is subtracted), so P4-09b's curve is the same call with a
 * provider of piecewise-constant segments ([SegmentedSpeed], P4-09b). Media3 forbids other speed-changing effects on an item that has one; the items here have none.
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
internal fun EditedMediaItem.Builder.setClipSpeed(speed: Float, retime: Retime): EditedMediaItem.Builder = when {
    retime is SpeedSegments -> setSpeed(SpeedParameters(SegmentedSpeed(retime), /* shouldMaintainPitch= */ true))
    speed == 1f -> this
    else -> setSpeed(SpeedParameters(ConstantSpeed(speed), /* shouldMaintainPitch= */ true))
}

/** One speed for the whole clip: no change ever comes. */
@OptIn(UnstableApi::class)
internal class ConstantSpeed(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}

/**
 * A speed curve (P4-09b) as the piecewise-constant pieces [SpeedSegments] holds: the same pieces the plan sums for the clip's length, so Media3's length and the
 * editor's agree to a µs. Counted from the clip's start in its source, as Media3 asks. Media3 restarts its time stretch at each change and adds no crossfade;
 * the sound is checked for clicks in docs/TECHNICAL_DESIGN.md → Speed curves.
 */
@OptIn(UnstableApi::class)
internal class SegmentedSpeed(private val segments: SpeedSegments) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = segments.speeds[pieceAt(timeUs)]

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
        val next = pieceAt(timeUs) + 1
        return if (next >= segments.count) C.TIME_UNSET else segments.sourceStartsUs[next]
    }

    // The piece [timeUs] falls in; the last piece for a time past the end.
    private fun pieceAt(timeUs: Long): Int {
        var low = 0
        var high = segments.count - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (segments.sourceStartsUs[middle] <= timeUs) low = middle else high = middle - 1
        }
        return low
    }
}
