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

    /** The stages in the one order every engine applies them; the clip's volume comes after the last one. */
    val CHAIN_ORDER: List<AudioEffectStage> = AudioEffectStage.entries

    /** [speed] snapped to the panel's 0.1 grid and kept inside the range: 1.2000000476837158 is 1.2. */
    fun cleanSpeed(speed: Float): Float = (speed.coerceIn(MIN_SPEED, MAX_SPEED) * 10f).roundToLong() / 10f
}

/**
 * One stage of the chain, in the order of [AudioEffectsCatalog.CHAIN_ORDER]. P3-09b adds LIMITER (the peak limiter at about -1 dBFS)
 * after FADES: it ends the effects, before the clip's volume.
 */
enum class AudioEffectStage { SPEED_AND_PITCH, VOICE, BASS_AND_DISTORTION, ECHO, FADES }
