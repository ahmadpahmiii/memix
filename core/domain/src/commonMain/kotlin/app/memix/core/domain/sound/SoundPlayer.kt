package app.memix.core.domain.sound

import app.memix.core.model.project.MediaRef
import kotlinx.coroutines.flow.StateFlow

/**
 * Plays one sound at a time on its own, outside any project: the Meme sounds sheet's play buttons (spec P1-06 → Meme
 * sounds sheet). Opened with [OpenSoundPlayerUseCase]; the caller closes it, which stops and frees the player. Main
 * thread only.
 */
interface SoundPlayer : AutoCloseable {
    /** The sound playing now, or null. Goes back to null when it ends, stops or fails. */
    val playing: StateFlow<MediaRef?>

    /** Plays [sound] from its start, stopping whatever was playing. */
    fun play(sound: MediaRef)

    fun stop()
}
