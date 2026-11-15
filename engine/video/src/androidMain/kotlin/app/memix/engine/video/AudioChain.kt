package app.memix.engine.video

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.memix.core.model.project.AudioEffectStage
import app.memix.core.model.project.AudioEffects
import app.memix.core.model.project.AudioEffectsCatalog
import kotlin.math.ceil
import kotlin.math.pow

/**
 * The audio processors of one item with sound, in the order they run (spec docs/ux/specs/P3-09-audio-effects.md → rule 1):
 * the Audio effects of its clip ([AudioEffects]: speed and pitch, then fades; P3-09b adds voice, bass and distortion, echo and the
 * limiter), then its volume, then the preview's live gain. [Media3CompositionBuilder] calls it for every item of a composition,
 * and the preview and the export both build their composition with that builder, so the two play the same chain. Nothing here
 * knows which of them it is; the preview's live gain is the one extra, and it changes nothing at 1.
 */
@OptIn(UnstableApi::class)
internal object AudioChain {
    /** The chain of sound [clip]; [liveGain] is the preview's volume slider hook (null in an export). */
    fun forSound(clip: SoundClip, liveGain: LiveGainProcessor?, liveFades: LiveFades? = null): List<AudioProcessor> =
        effects(clip.effects, clip.clipLengthUs, liveFades) + volume(clip.volume, liveGain)

    /**
     * The processors for [effects] on a clip that lasts [clipLengthUs] on the timeline, in [AudioEffectsCatalog.CHAIN_ORDER].
     * Fresh instances each call: a processor keeps the state of its stream, so no two items share one. Empty when nothing is set.
     */
    fun effects(effects: AudioEffects, clipLengthUs: Long, liveFades: LiveFades? = null): List<AudioProcessor> =
        AudioEffectsCatalog.CHAIN_ORDER.mapNotNull { stage ->
            when (stage) {
                AudioEffectStage.SPEED_AND_PITCH -> speedAndPitch(effects)
                AudioEffectStage.FADES -> fades(effects, clipLengthUs, liveFades)
                // TODO(P3-09b): voice, bass and distortion, echo, then the limiter before the volume.
                AudioEffectStage.VOICE, AudioEffectStage.BASS_AND_DISTORTION, AudioEffectStage.ECHO -> null
            }
        }

    /**
     * The committed volume, then the preview's live gain if it has one. A 0% item's silence comes from its live gain alone,
     * since a fixed 0 step before it would leave nothing to raise.
     */
    fun volume(volume: Float, liveGain: LiveGainProcessor?): List<AudioProcessor> {
        if (liveGain == null) return volumeProcessors(volume)
        return if (volume == 0f) listOf(liveGain) else volumeProcessors(volume) + liveGain
    }

    // Sonic changes speed and pitch independently; the pitch factor of a semitone step is the twelfth root of two. Media3 knows
    // the length it makes (getDurationAfterProcessorApplied), so the item's length in the composition follows the speed.
    private fun speedAndPitch(effects: AudioEffects): AudioProcessor? {
        if (!effects.hasTone) return null
        return SonicAudioProcessor().apply {
            setSpeed(effects.speed)
            setPitch(2f.pow(effects.pitchSemitones / SEMITONES_PER_OCTAVE))
        }
    }

    // The preview's sound always has the stage (its fades can be set while it plays); an export only when a fade is set.
    private fun fades(effects: AudioEffects, clipLengthUs: Long, liveFades: LiveFades?): AudioProcessor? {
        if (!effects.hasFades && liveFades == null) return null
        return GainProcessor(FadeGains(liveFades ?: LiveFades(effects.fadeInUs, effects.fadeOutUs), clipLengthUs, isLive = liveFades != null))
    }

    // Scales every channel by the volume. Media3 clamps samples past full scale, so volumes above 1 get louder without wrapping around.
    private fun volumeProcessors(volume: Float): List<AudioProcessor> {
        if (volume == 1f) return emptyList()
        val processor = ChannelMixingAudioProcessor()
        for (channelCount in 1..MAX_CHANNEL_COUNT) {
            processor.putChannelMixingMatrix(ChannelMixingMatrix(channelCount, channelCount, scaledIdentity(channelCount, volume)))
        }
        return listOf(processor)
    }

    // Row-major: the diagonal of an n x n matrix sits at every (n + 1)th index.
    private fun scaledIdentity(channelCount: Int, scale: Float) =
        FloatArray(channelCount * channelCount) { index -> if (index % (channelCount + 1) == 0) scale else 0f }

    private const val SEMITONES_PER_OCTAVE = 12f

    /** 7.1 surround; phone recordings are mono or stereo. */
    private const val MAX_CHANNEL_COUNT = 8
}

/**
 * The two fades of one sound that the preview can change while it plays (the Audio effects panel's fade sliders, heard as they move),
 * like [LiveGainProcessor] for the volume. The player's audio thread reads them; the main thread writes them.
 */
internal class LiveFades(fadeInUs: Long, fadeOutUs: Long) {
    @Volatile
    var fadeInUs = fadeInUs

    @Volatile
    var fadeOutUs = fadeOutUs
}

/**
 * The gain of a clip with a linear fade in over its first [LiveFades.fadeInUs] and a linear fade out over its last
 * [LiveFades.fadeOutUs], for [GainProcessor]. Positions count from the clip's start, after speed and pitch (the fades are after them
 * in the chain), and Media3 resets them to the right place after a seek. The fade out reaches 0 a few ms before the clip's planned end
 * ([END_MARGIN_US]) and anything after is silent, so Sonic's output being a sample or two shorter than the plan can't leave a click.
 * With [isLive] the values may change while it plays: the stage then never reports "unity to the end", which would switch it off.
 */
@OptIn(UnstableApi::class)
internal class FadeGains(
    private val fades: LiveFades,
    private val clipLengthUs: Long,
    private val isLive: Boolean = false,
) : GainProcessor.GainProvider {
    override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float {
        val timeUs = samplePosition * MICROS_PER_SECOND / sampleRate
        val fadeInUs = fades.fadeInUs
        val fadeOutUs = fades.fadeOutUs
        val rampEndUs = clipLengthUs - minOf(END_MARGIN_US, fadeOutUs / 2)
        return when {
            fadeInUs > 0 && timeUs < fadeInUs -> (timeUs / fadeInUs.toDouble()).toFloat()
            fadeOutUs > 0 && timeUs >= clipLengthUs - fadeOutUs -> ((rampEndUs - timeUs) / (rampEndUs - (clipLengthUs - fadeOutUs)).toDouble()).toFloat().coerceIn(0f, 1f)
            else -> 1f
        }
    }

    override fun isUnityUntil(samplePosition: Long, sampleRate: Int): Long {
        if (getGainFactorAtSamplePosition(samplePosition, sampleRate) != 1f) return C.TIME_UNSET
        val fadeOutUs = fades.fadeOutUs
        if (fadeOutUs <= 0) return if (isLive) samplePosition + liveWindowFrames(sampleRate) else C.TIME_END_OF_SOURCE
        val fadeOutStartFrame = ceil((clipLengthUs - fadeOutUs) * sampleRate / MICROS_PER_SECOND).toLong()
        // Unity at samplePosition, so the range reaches at least one frame. A live fade may be set at any moment: look only a short way ahead.
        val end = maxOf(fadeOutStartFrame, samplePosition + 1)
        return if (isLive) minOf(end, samplePosition + liveWindowFrames(sampleRate)) else end
    }

    private fun liveWindowFrames(sampleRate: Int): Long = sampleRate * LIVE_WINDOW_US / MICROS_PER_SECOND.toLong()

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000.0
        const val END_MARGIN_US = 5_000L
        const val LIVE_WINDOW_US = 50_000L
    }
}
