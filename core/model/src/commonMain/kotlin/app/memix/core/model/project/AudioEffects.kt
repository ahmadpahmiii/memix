package app.memix.core.model.project

import kotlin.math.floor
import kotlin.math.roundToLong
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the user did to a meme sound or an audio clip in the Audio effects panel (spec docs/ux/specs/P3-09-audio-effects.md).
 * A declarative spec like every effect (CLAUDE.md rule 5): ids and numbers, no engine types, so each engine renders the
 * same sound from it. The source file is never changed; the effects live in the clip.
 *
 * The whole model of P3-09a and P3-09b is here, so the second half needs no schema bump. Every group can be on at once,
 * in one fixed order, the same in preview and export: speed and pitch, voice, bass and distortion, echo, fades, then the
 * clip's volume ([AudioEffectsCatalog.CHAIN_ORDER]). All fields are saved, with defaults that mean "off".
 */
@Serializable
data class AudioEffects(
    /** Fade in over this much of the clip's start, µs of timeline time (after [speed]). 0 is off. */
    val fadeInUs: Long = 0,
    /** Fade out over this much of the clip's end, µs of timeline time (after [speed]). 0 is off. */
    val fadeOutUs: Long = 0,
    /** Whole semitones up (+) or down (-), from -12 to +12, without changing the speed. 0 is off. */
    val pitchSemitones: Int = 0,
    /** Playback speed from 0.5 to 2, without changing the pitch. 1 is off. It changes the clip's length on the timeline. */
    val speed: Float = 1f,
    /** P3-09b: the voice preset that plays first in the chain. */
    val voice: VoicePreset = VoicePreset.NONE,
    /** P3-09b: low-frequency boost, 0 (off) to 1. */
    val bassBoost: Float = 0f,
    /** P3-09b: distortion, 0 (off) to 1. */
    val distortion: Float = 0f,
    /** P3-09b: how loud the echo's repeats are, 0 (off) to 1. */
    val echoAmount: Float = 0f,
    /** P3-09b: the time between echo repeats. Stored even while [echoAmount] is 0. */
    val echoLength: EchoLength = EchoLength.MEDIUM,
) {
    /** True when nothing is set: the clip plays as the file does. */
    // The echo length is stored even while the echo is off (spec P3-09), so it alone doesn't make a clip "set".
    val isDefault: Boolean get() = copy(echoLength = if (echoAmount == 0f) EchoLength.MEDIUM else echoLength) == AudioEffects()

    /** The Tone tab's mark: pitch or speed is set. */
    val hasTone: Boolean get() = pitchSemitones != 0 || speed != 1f

    /** The Fades tab's mark. */
    val hasFades: Boolean get() = fadeInUs > 0 || fadeOutUs > 0

    /** The Voice tab's mark (P3-09b). */
    val hasVoice: Boolean get() = voice != VoicePreset.NONE

    /** The Bass tab's mark (P3-09b). */
    val hasBass: Boolean get() = bassBoost > 0f || distortion > 0f

    /** The Echo tab's mark (P3-09b). */
    val hasEcho: Boolean get() = echoAmount > 0f

    /** True when the voice, bass, distortion or echo can raise the level: the chain then ends in the peak limiter ([AudioEffectStage.LIMITER]). */
    val needsLimiter: Boolean get() = hasVoice || hasBass || hasEcho

    /** The bass shelf of [bassBoost], or null while it is off. */
    val bassFilter: BiquadSpec? get() = AudioEffectsCatalog.bassFilter(bassBoost)

    /** The echo of [echoAmount] and [echoLength], or null while the amount is 0. */
    val echoSpec: EchoSpec? get() = AudioEffectsCatalog.echoSpec(echoAmount, echoLength)

    /**
     * How long the sound keeps ringing after the clip's last sample (the echo's repeats and a voice's room), µs: up to
     * [AudioEffectsCatalog.ECHO_TAIL_MAX_US], 0 when nothing rings. The engine extends the clip by this much, less where the video ends first.
     * A set Fade out means no tail: the fade ends the sound at the clip's end, so a tail would be silence that only reserves a lane.
     */
    val tailUs: Long get() = if (fadeOutUs > 0) 0L else minOf(AudioEffectsCatalog.ECHO_TAIL_MAX_US, (VoiceRecipes.of(voice)?.echo?.tailUs ?: 0L) + (echoSpec?.tailUs ?: 0L))

    /** How long a stretch of the source of [sourceSpanUs] lasts on the timeline at this [speed]. */
    fun timelineLengthOf(sourceSpanUs: Long): Long =
        if (speed == 1f) sourceSpanUs else (sourceSpanUs / speed.toDouble()).roundToLong()

    /** How much timeline time [sourceSpanUs] of the source covers, rounded down: the limit of a trim must never reach before 0 in the source. */
    fun timelineLengthFloorOf(sourceSpanUs: Long): Long =
        if (speed == 1f) sourceSpanUs else floor(sourceSpanUs / speed.toDouble()).toLong()

    /**
     * How much of the source plays in [timelineLengthUs] of timeline time at this [speed]: rounded down, so the timeline
     * length of the result is never longer than [timelineLengthUs] and two pieces of a split never overlap.
     */
    fun sourceSpanOf(timelineLengthUs: Long): Long =
        if (speed == 1f) timelineLengthUs else floor(timelineLengthUs * speed.toDouble()).toLong()

    /**
     * These effects with the fades shortened until both fit in a clip [clipLengthUs] long (a trim, a split or a faster
     * speed made it shorter): each is cut by the same share, so neither one pushes the other.
     */
    fun withFadesFitted(clipLengthUs: Long): AudioEffects {
        val total = fadeInUs + fadeOutUs
        if (total <= clipLengthUs) return this
        val room = clipLengthUs.coerceAtLeast(0)
        return copy(fadeInUs = fadeInUs * room / total, fadeOutUs = fadeOutUs * room / total)
    }
}

/** The voice presets of P3-09b; ids are what `tool_use` sends as `value` (never anything about the file). */
@Serializable
enum class VoicePreset(val eventValue: String) {
    @SerialName("none") NONE("none"),
    @SerialName("chipmunk") CHIPMUNK("chipmunk"),
    @SerialName("giant") GIANT("giant"),
    @SerialName("robot") ROBOT("robot"),
    @SerialName("megaphone") MEGAPHONE("megaphone"),
    @SerialName("cave") CAVE("cave"),
}

/** The time between echo repeats (P3-09b): the panel's Short, Medium and Long. */
@Serializable
enum class EchoLength(val delayUs: Long) {
    @SerialName("short") SHORT(150_000),
    @SerialName("medium") MEDIUM(300_000),
    @SerialName("long") LONG(500_000),
}

/**
 * The Audio effects ranges and the chain's order, shared by the panel, the edits and both engines (spec P3-09 → The rules
 * behind the panel). Changing one after P3 changes old drafts, so it needs a schema bump and a compensating migration.
 */
object AudioEffectsCatalog {
    val PITCH_RANGE_SEMITONES: IntRange = -12..12
    const val MIN_SPEED = 0.5f
    const val MAX_SPEED = 2f
    const val SPEED_STEP = 0.1f

    /** A fade is at most 5 s, and the two never add up to more than the clip. */
    const val MAX_FADE_US = 5_000_000L
    const val FADE_STEP_US = 100_000L

    /** A clip shorter than this is too short to fade (spec P3-09 → Fades). */
    const val MIN_FADEABLE_US = 300_000L

    /** The echo's tail rings out after the clip, up to this long, and is cut at the end of the video (P3-09b). */
    const val ECHO_TAIL_MAX_US = 1_500_000L

    /** Bass boost: a low shelf at [BASS_SHELF_HZ] that lifts everything below it by up to [BASS_MAX_GAIN_DB] (spec: about +12 dB below a few hundred hertz). */
    const val BASS_SHELF_HZ = 250f
    const val BASS_MAX_GAIN_DB = 12f

    /** Distortion: a soft clip (tanh) whose input gain goes from 1 (clean) at 0% to [DISTORTION_MAX_GAIN] (deep-fried) at 100%. */
    const val DISTORTION_MAX_GAIN = 25f

    /** The loudest first repeat of the echo, as a share of the sound, and the share of each repeat that comes back (at 100%). */
    const val ECHO_MAX_WET = 0.8f
    const val ECHO_MAX_FEEDBACK = 0.45f

    /** The peak limiter's ceiling, dBFS: stacked effects never reach full scale (spec P3-09 rule 7). */
    const val LIMITER_CEILING_DB = -1f

    fun bassFilter(bassBoost: Float): BiquadSpec? =
        if (bassBoost <= 0f) null else BiquadSpec(BiquadShape.LOW_SHELF, BASS_SHELF_HZ, gainDb = BASS_MAX_GAIN_DB * bassBoost.coerceAtMost(1f))

    /** The multiplier a distortion of [distortion] (0 to 1) puts on the signal before it is soft-clipped. */
    fun distortionGain(distortion: Float): Float = 1f + (DISTORTION_MAX_GAIN - 1f) * distortion.coerceIn(0f, 1f)

    fun echoSpec(amount: Float, length: EchoLength): EchoSpec? {
        if (amount <= 0f) return null
        val level = amount.coerceAtMost(1f)
        return EchoSpec(length.delayUs, feedback = ECHO_MAX_FEEDBACK * level, wet = ECHO_MAX_WET * level)
    }

    /** The stages in the one order every engine applies them; the clip's volume comes after the last one. */
    val CHAIN_ORDER: List<AudioEffectStage> = AudioEffectStage.entries

    /** The Bass, Distortion and Echo sliders move in 10% steps. */
    const val LEVEL_STEP = 0.1f

    /** [level] snapped to the sliders' 10% grid and kept inside 0 to 1; a whole number of steps over [LEVEL_STEPS], so 0.3 is exactly 0.3f. */
    fun cleanLevel(level: Float): Float = (level.coerceIn(0f, 1f) * LEVEL_STEPS).roundToLong() / LEVEL_STEPS

    private const val LEVEL_STEPS = 10f

    /** [speed] snapped to the panel's 0.1 grid and kept inside the range: 1.2000000476837158 is 1.2. */
    fun cleanSpeed(speed: Float): Float = (speed.coerceIn(MIN_SPEED, MAX_SPEED) * 10f).roundToLong() / 10f
}

/**
 * One stage of the chain, in the order of [AudioEffectsCatalog.CHAIN_ORDER]. LIMITER (P3-09b, the peak limiter at
 * [AudioEffectsCatalog.LIMITER_CEILING_DB]) ends the effects, before the clip's volume.
 */
enum class AudioEffectStage { SPEED_AND_PITCH, VOICE, BASS_AND_DISTORTION, ECHO, FADES, LIMITER }
