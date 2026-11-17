package app.memix.core.domain.project

import app.memix.core.model.project.EffectCatalog
import app.memix.core.model.project.EffectEnvelope
import app.memix.core.model.project.EffectItem
import kotlin.math.exp
import kotlin.math.min

/*
 * How strong an effect item is at a moment (spec docs/ux/specs/P4-06-video-effects.md → Envelope). Both engines, the editor's tiles and the
 * reference renders call this one function, so preview, export and picture agree.
 */

/**
 * The strength *s* of an effect item [durationUs] long, [elapsedUs] after its start: [intensity] times the [envelope]'s shape, 0 to 1.
 * Outside the item it is 0. Measured from the item's own start and end, so a shorter item plays the same shape faster.
 */
fun effectStrength(envelope: EffectEnvelope, intensity: Float, durationUs: Long, elapsedUs: Long): Float {
    if (durationUs <= 0 || elapsedUs < 0 || elapsedUs >= durationUs || intensity <= 0f) return 0f
    val length = durationUs.toDouble()
    val elapsed = elapsedUs.toDouble()
    val shape = when (envelope) {
        EffectEnvelope.STEADY -> steadyShape(elapsed, length)
        EffectEnvelope.PUNCH -> punchShape(elapsed, length)
        EffectEnvelope.DECAY -> exp(-EffectCatalog.DECAY_RATE * elapsed / length)
        EffectEnvelope.RAMP -> bezierProgress(0.42, 0.0, 0.58, 1.0, elapsed / length)
    }
    return (intensity * shape).toFloat().coerceIn(0f, 1f)
}

// Ramps in and out over min(80 ms, a quarter of the length), linearly.
private fun steadyShape(elapsed: Double, length: Double): Double {
    val ramp = min(EffectCatalog.ATTACK_US.toDouble(), length / 4)
    return min(1.0, min(elapsed / ramp, (length - elapsed) / ramp))
}

// Attack to full over 80 ms (Bézier 0, 0, 0.58, 1), hold to 70% of the length, ease back to 0 by the end (Bézier 0.42, 0, 0.58, 1).
private fun punchShape(elapsed: Double, length: Double): Double {
    val holdEnd = EffectCatalog.PUNCH_HOLD_SHARE * length
    val attack = min(EffectCatalog.ATTACK_US.toDouble(), holdEnd)
    return when {
        elapsed < attack -> bezierProgress(0.0, 0.0, 0.58, 1.0, elapsed / attack)
        elapsed <= holdEnd -> 1.0
        else -> 1.0 - bezierProgress(0.42, 0.0, 0.58, 1.0, (elapsed - holdEnd) / (length - holdEnd))
    }
}

/** The strength of [item] at project time [atUs]; 0 for an effect this build doesn't know. */
fun EffectItem.strengthAt(atUs: Long): Float {
    val def = EffectCatalog.of(effectId) ?: return 0f
    return effectStrength(def.envelope, intensity, durationUs, atUs - startUs)
}
