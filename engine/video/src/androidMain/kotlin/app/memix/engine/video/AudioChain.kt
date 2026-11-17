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
import app.memix.core.model.project.Keyframe
import app.memix.core.model.project.VoiceRecipe
import app.memix.core.model.project.VoiceRecipes
import app.memix.engine.video.audio.DistortionProcessor
import app.memix.engine.video.audio.EchoProcessor
import app.memix.engine.video.audio.FilterProcessor
import app.memix.engine.video.audio.GainSource
import app.memix.engine.video.audio.KeyframedGainProcessor
import app.memix.engine.video.audio.PeakLimiterProcessor
import app.memix.engine.video.audio.RingModulationProcessor
import app.memix.engine.video.audio.TailProcessor
import kotlin.math.ceil
import kotlin.math.pow

/**
 * The audio processors of one item with sound, in the order they run (spec docs/ux/specs/P3-09-audio-effects.md → rule 1):
 * the Audio effects of its clip ([AudioEffects]: speed and pitch, voice, bass and distortion, echo, fades, then the peak limiter),
 * then its volume, then the preview's live gain. [Media3CompositionBuilder] calls it for every item of a composition,
 * and the preview and the export both build their composition with that builder, so the two play the same chain. Nothing here
 * knows which of them it is; the preview's live gain is the one extra, and it changes nothing at 1.
 */
@OptIn(UnstableApi::class)
internal object AudioChain {
    /**
     * The chain of sound [clip]; [liveGain], [liveFades] and [liveBass] are the preview's hooks for the Volume, Fade and Bass and Distortion
     * sliders (null in an export, which builds a stage only when its effect is set).
     */
    fun forSound(clip: SoundClip, liveGain: LiveGainProcessor?, liveFades: LiveFades? = null, liveBass: LiveBass? = null): List<AudioProcessor> =
        effects(clip.effects, clip.clipLengthUs, clip.tailUs, liveFades, liveBass) + volume(clip.volume, clip.keyframes, liveGain, clip.tailUs)

    /**
     * The processors for [effects] on a clip that lasts [clipLengthUs] on the timeline and rings out for [tailUs] after it, in
     * [AudioEffectsCatalog.CHAIN_ORDER]. Fresh instances each call: a processor keeps the state of its stream, so no two items share
     * one. Empty when nothing is set.
     */
    fun effects(
        effects: AudioEffects,
        clipLengthUs: Long,
        tailUs: Long = 0,
        liveFades: LiveFades? = null,
        liveBass: LiveBass? = null,
    ): List<AudioProcessor> = AudioEffectsCatalog.CHAIN_ORDER.flatMap { stage ->
        when (stage) {
            AudioEffectStage.SPEED_AND_PITCH -> listOfNotNull(speedAndPitch(effects))
            AudioEffectStage.VOICE -> voice(effects, tailUs)
            AudioEffectStage.BASS_AND_DISTORTION -> bassAndDistortion(effects, liveBass)
            AudioEffectStage.ECHO -> listOfNotNull(effects.echoSpec?.let { EchoProcessor(it) })
            AudioEffectStage.FADES -> listOfNotNull(fades(effects, clipLengthUs, tailUs, liveFades))
            AudioEffectStage.LIMITER -> listOfNotNull(limiter(effects, liveBass))
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

    /**
     * [volume] with [keyframes] (P4-02): with none it is [volume] as above; with some, the keyframed level ([KeyframedGainProcessor], per
     * sample) replaces the fixed one, and the preview's live gain still multiplies it. [tailUs] as in [KeyframedGainProcessor].
     */
    fun volume(volume: Float, keyframes: List<Keyframe>, liveGain: LiveGainProcessor?, tailUs: Long = 0): List<AudioProcessor> {
        if (keyframes.isEmpty()) return volume(volume, liveGain)
        return listOf(KeyframedGainProcessor(keyframes, tailUs)) + listOfNotNull(liveGain)
    }

    // Sonic changes speed and pitch independently; the pitch factor of a semitone step is the twelfth root of two. Media3 knows
    // the length it makes (getDurationAfterProcessorApplied), so the item's length in the composition follows the speed.
    private fun speedAndPitch(effects: AudioEffects): AudioProcessor? {
        if (!effects.hasTone) return null
        return sonic(effects.speed, effects.pitchSemitones)
    }

    private fun sonic(speed: Float, pitchSemitones: Int) = SonicAudioProcessor().apply {
        setSpeed(speed)
        setPitch(2f.pow(pitchSemitones / SEMITONES_PER_OCTAVE))
    }

    // The voice preset's own pitch shift comes first (Sonic keeps the length), then the tail starts: from there on silence is added
    // after the clip so the filters' and echoes' last repeats ring out. Without a voice the same place starts the tail for the Echo stage.
    private fun voice(effects: AudioEffects, tailUs: Long): List<AudioProcessor> {
        val recipe = VoiceRecipes.of(effects.voice)
        val tail = listOfNotNull(if (tailUs > 0) TailProcessor(tailUs) else null)
        if (recipe == null) return tail
        val pitch = listOfNotNull(if (recipe.pitchSemitones != 0) sonic(1f, recipe.pitchSemitones) else null)
        return pitch + tail + recipeProcessors(recipe, tailUs)
    }

    private fun recipeProcessors(recipe: VoiceRecipe, tailUs: Long): List<AudioProcessor> =
        recipe.filters.map { filter -> FilterProcessor { filter } } +
            listOfNotNull(recipe.ringModulation?.let { RingModulationProcessor(it, tailUs) }) +
            listOfNotNull(if (recipe.drive > 0f) DistortionProcessor(GainSource { AudioEffectsCatalog.distortionGain(recipe.drive) }) else null) +
            listOfNotNull(recipe.echo?.let { EchoProcessor(it) })

    // Bass first, then distortion. The preview always has both, reading the sliders' live values, so a slider moved from 0 is heard
    // at once; at 0 they pass the sound through untouched. The export has each only when it is set.
    private fun bassAndDistortion(effects: AudioEffects, liveBass: LiveBass?): List<AudioProcessor> {
        if (liveBass != null) {
            return listOf(
                FilterProcessor { liveBass.bassFilter() },
                DistortionProcessor(GainSource { AudioEffectsCatalog.distortionGain(liveBass.distortion) }),
            )
        }
        val bass = effects.bassFilter
        return listOfNotNull(
            bass?.let { filter -> FilterProcessor { filter } },
            if (effects.distortion > 0f) DistortionProcessor(GainSource { AudioEffectsCatalog.distortionGain(effects.distortion) }) else null,
        )
    }

    // Ends the effects (spec P3-09 rule 7). The export has it when an effect that can raise the level is set; the preview always
    // has it, and it limits only while such an effect is set (a plain sound is never touched, as in the export).
    private fun limiter(effects: AudioEffects, liveBass: LiveBass?): AudioProcessor? {
        val ceiling = 10f.pow(AudioEffectsCatalog.LIMITER_CEILING_DB / DB_PER_AMPLITUDE_DECADE)
        if (liveBass == null) return if (effects.needsLimiter) PeakLimiterProcessor(ceiling) { true } else null
        val committed = effects.hasVoice || effects.hasEcho
        return PeakLimiterProcessor(ceiling) { committed || liveBass.bassBoost > 0f || liveBass.distortion > 0f }
    }

    // The preview's sound always has the stage (its fades can be set while it plays); an export only when a fade is set.
    private fun fades(effects: AudioEffects, clipLengthUs: Long, tailUs: Long, liveFades: LiveFades?): AudioProcessor? {
        if (!effects.hasFades && liveFades == null) return null
        return GainProcessor(FadeGains(liveFades ?: LiveFades(effects.fadeInUs, effects.fadeOutUs), clipLengthUs, isLive = liveFades != null, tailUs = tailUs))
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

    /** dB to a linear level: 10^(dB / 20). */
    private const val DB_PER_AMPLITUDE_DECADE = 20f

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
    // Media3 counts positions after the [TailProcessor] from its tail on (it maps the start position through the item's longer length); [tailUs] takes that out.
    private val tailUs: Long = 0,
) : GainProcessor.GainProvider {
    override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float {
        val timeUs = (samplePosition - tailFrames(sampleRate)) * MICROS_PER_SECOND / sampleRate
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
        val fadeOutStartFrame = ceil((clipLengthUs - fadeOutUs) * sampleRate / MICROS_PER_SECOND).toLong() + tailFrames(sampleRate)
        // Unity at samplePosition, so the range reaches at least one frame. A live fade may be set at any moment: look only a short way ahead.
        val end = maxOf(fadeOutStartFrame, samplePosition + 1)
        return if (isLive) minOf(end, samplePosition + liveWindowFrames(sampleRate)) else end
    }

    private fun tailFrames(sampleRate: Int): Long = (tailUs * sampleRate / MICROS_PER_SECOND).toLong()

    private fun liveWindowFrames(sampleRate: Int): Long = sampleRate * LIVE_WINDOW_US / MICROS_PER_SECOND.toLong()

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000.0
        const val END_MARGIN_US = 5_000L
        const val LIVE_WINDOW_US = 50_000L
    }
}
