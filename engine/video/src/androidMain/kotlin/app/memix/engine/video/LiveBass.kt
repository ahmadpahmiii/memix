package app.memix.engine.video

import app.memix.core.model.project.AudioEffectsCatalog
import app.memix.core.model.project.BiquadSpec

/**
 * The Bass boost and Distortion of one sound that the preview can change while it plays (the Audio effects panel's Bass sliders,
 * heard as they move), like [LiveFades] for the fades. The player's audio thread reads them; the main thread writes them. Both are
 * 0 (off) to 1.
 */
internal class LiveBass(bassBoost: Float, distortion: Float) {
    @Volatile
    var bassBoost = bassBoost

    @Volatile
    var distortion = distortion

    // The audio thread asks for the filter once per buffer; it is rebuilt only when the slider has moved, so no buffer allocates.
    private var cachedBoost = Float.NaN
    private var cachedFilter: BiquadSpec? = null

    /** The shelf for the current [bassBoost], null while it is off. Audio thread only. */
    fun bassFilter(): BiquadSpec? {
        val boost = bassBoost
        if (boost != cachedBoost) {
            cachedFilter = AudioEffectsCatalog.bassFilter(boost)
            cachedBoost = boost
        }
        return cachedFilter
    }
}
