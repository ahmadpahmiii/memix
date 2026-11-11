package app.memix.core.domain.sound

import app.memix.core.model.project.MediaRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Plays one sound at a time on its own, outside any project: the Meme sounds sheet's play buttons (spec P1-06 → Meme
 * sounds sheet) and the Sounds screen's previews (P3-02, P3-03). Opened with [OpenSoundPlayerUseCase]; the caller
 * closes it, which stops and frees the player. Main thread only.
 */
interface SoundPlayer : AutoCloseable {
    /** The sound playing now, or null. Goes back to null when it ends, stops, pauses or fails. */
    val playing: StateFlow<MediaRef?>

    /** The sound held part-way by [pause], or null. Never set together with [playing]; stopping or ending clears it. */
    val paused: StateFlow<MediaRef?>

    /** Emits a sound that started but could not play (its file is missing or damaged); [playing] goes back to null too. */
    val failures: Flow<MediaRef>

    /** Gets the player ready so the next [play] starts at once. Optional; does nothing when it already is. */
    fun warmUp()

    /** Plays [sound] from its start, stopping whatever was playing. */
    fun play(sound: MediaRef)

    /** Holds the playing sound where it is. Does nothing when nothing plays. */
    fun pause()

    /** Carries on from where [pause] held the sound. Does nothing when nothing is paused. */
    fun resume()

    fun stop()

    /**
     * How far the playing or paused sound has played, in microseconds; 0 when there is none. Cheap to call every frame,
     * so a waveform can read it in its draw step.
     */
    fun positionUs(): Long
}
