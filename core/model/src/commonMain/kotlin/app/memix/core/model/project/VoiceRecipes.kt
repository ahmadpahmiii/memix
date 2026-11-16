package app.memix.core.model.project

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToLong

/** The shapes of a second-order (biquad) filter (the audio EQ cookbook's): the voice presets and the Bass tab are built from them. */
enum class BiquadShape { LOW_SHELF, HIGH_SHELF, HIGH_PASS, LOW_PASS }

/** One filter: [gainDb] only matters to a shelf; [quality] is the filter's Q (0.707 is the flat, Butterworth one). */
data class BiquadSpec(val shape: BiquadShape, val frequencyHz: Float, val gainDb: Float = 0f, val quality: Float = FLAT_QUALITY) {
    companion object {
        const val FLAT_QUALITY = 0.707f
    }
}

/**
 * A feedback delay: the sound plus [wet] times what it was [delayUs] ago, and [feedback] of that comes back again. [dampingHz] above 0
 * rolls the highs off each repeat (a dull room); 0 leaves them.
 */
data class EchoSpec(val delayUs: Long, val feedback: Float, val wet: Float, val dampingHz: Float = 0f) {
    /** How long the repeats take to fall under [AUDIBLE_FLOOR] of the sound, µs; the engine rings the clip out this far. */
    val tailUs: Long
        get() {
            if (wet <= AUDIBLE_FLOOR) return delayUs
            val repeats = if (feedback <= 0f) 1.0 else ceil(ln(AUDIBLE_FLOOR / wet.toDouble()) / ln(feedback.toDouble()))
            return ((repeats + 1) * delayUs).roundToLong()
        }

    private companion object {
        /** About -50 dB. */
        const val AUDIBLE_FLOOR = 0.003
    }
}

/** A ring modulator: the sound times a sine of [frequencyHz], [mix] of it (0 to 1) against the plain sound. */
data class RingModulation(val frequencyHz: Float, val mix: Float)

/**
 * A voice preset as the parts of the chain it plays first (P3-09b): a pitch shift, filters, a ring modulator, a soft clip
 * ([drive] 0 to 1, the Distortion slider's curve) and an echo, in that order. Declarative like every effect, so each engine builds the same voice.
 */
data class VoiceRecipe(
    val pitchSemitones: Int = 0,
    val filters: List<BiquadSpec> = emptyList(),
    val ringModulation: RingModulation? = null,
    val drive: Float = 0f,
    val echo: EchoSpec? = null,
)

/**
 * The six presets. They start a character and never touch a slider (spec P3-09 → Voice); the numbers are tuned by ear and the owner
 * listens at QA, so changing one changes how old drafts sound (not their shape: no schema change).
 */
object VoiceRecipes {
    private val CHIPMUNK = VoiceRecipe(pitchSemitones = 7, filters = listOf(BiquadSpec(BiquadShape.HIGH_SHELF, 3_000f, gainDb = 5f)))
    private val GIANT = VoiceRecipe(
        pitchSemitones = -6,
        filters = listOf(BiquadSpec(BiquadShape.LOW_SHELF, 200f, gainDb = 6f), BiquadSpec(BiquadShape.LOW_PASS, 6_000f)),
    )
    private val ROBOT = VoiceRecipe(
        ringModulation = RingModulation(frequencyHz = 50f, mix = 0.85f),
        echo = EchoSpec(delayUs = 6_000, feedback = 0.55f, wet = 0.9f),
    )
    private val MEGAPHONE = VoiceRecipe(
        filters = listOf(BiquadSpec(BiquadShape.HIGH_PASS, 500f, quality = 0.9f), BiquadSpec(BiquadShape.LOW_PASS, 3_500f)),
        drive = 0.15f,
    )
    private val CAVE = VoiceRecipe(
        filters = listOf(BiquadSpec(BiquadShape.LOW_PASS, 2_800f)),
        echo = EchoSpec(delayUs = 180_000, feedback = 0.5f, wet = 0.55f, dampingHz = 2_200f),
    )

    /** The recipe of [preset]; null for None. */
    fun of(preset: VoicePreset): VoiceRecipe? = when (preset) {
        VoicePreset.NONE -> null
        VoicePreset.CHIPMUNK -> CHIPMUNK
        VoicePreset.GIANT -> GIANT
        VoicePreset.ROBOT -> ROBOT
        VoicePreset.MEGAPHONE -> MEGAPHONE
        VoicePreset.CAVE -> CAVE
    }
}
