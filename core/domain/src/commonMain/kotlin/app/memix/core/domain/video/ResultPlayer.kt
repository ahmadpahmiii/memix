package app.memix.core.domain.video

import kotlinx.coroutines.flow.StateFlow

/**
 * Plays the exported file on the Exported screen (spec docs/ux/specs/P1-12-export.md → Exported screen): once with
 * sound, no autoplay, no loop. Opened with [OpenResultPlayerUseCase]; the caller closes it, which frees the player.
 * Main thread only.
 */
interface ResultPlayer : AutoCloseable {
    /** True while the video plays; false when paused, and again once it has played to the end. */
    val isPlaying: StateFlow<Boolean>

    /** Plays from where it paused, or from the start after it ended; pauses when it's playing. */
    fun togglePlayback()

    /** Pauses; nothing when it isn't playing (the app left the screen). */
    fun pause()
}

/** Opens the player behind the Exported screen's play disc. The caller closes it. */
class OpenResultPlayerUseCase(private val videoEngine: VideoEngine) {
    operator fun invoke(location: String): ResultPlayer = videoEngine.openResultPlayer(location)
}
