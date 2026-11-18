package app.memix.engine.video

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.SpeedParameters
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem

/**
 * A clip's speed for the Media3 composition (P4-09a). [setSpeed][EditedMediaItem.Builder.setSpeed] with a [SpeedProvider] is the one hook that works
 * the same in CompositionPlayer and Transformer: both wrap the clip's source in `SpeedChangingMediaSource` (video and audio timestamps mapped through
 * the provider) and run `SpeedChangingAudioProcessor` on the sound; with [SpeedParameters.shouldMaintainPitch] set the sound is time-stretched, not
 * resampled, like P3-09's speed (spec P4-09 → The video clip's own sound, Keep). Checked against the Media3 1.11.1 source (docs/TECHNICAL_DESIGN.md → Speed).
 *
 * The provider's time is counted from the clip's start in the source (the clipping start is subtracted), so P4-09b's curve is the same call with a
 * provider of piecewise-constant segments. Media3 forbids other speed-changing effects on an item that has one; the items here have none.
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
internal fun EditedMediaItem.Builder.setClipSpeed(speed: Float): EditedMediaItem.Builder =
    if (speed == 1f) this else setSpeed(SpeedParameters(ConstantSpeed(speed), /* shouldMaintainPitch= */ true))

/** One speed for the whole clip: no change ever comes. */
@OptIn(UnstableApi::class)
internal class ConstantSpeed(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}
