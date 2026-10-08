package app.memix.core.domain.video

import app.memix.core.model.project.Project
import kotlinx.coroutines.flow.StateFlow

/**
 * A live preview of one video project, from [VideoEngine.createPreview]. It renders the project the way export
 * would (same composition plan), plays its sound through the phone's media audio, and shows its picture on the
 * surface the platform UI attaches to it.
 *
 * Main thread only: call every member from the main thread. Nothing here blocks; reading the media files and
 * building the composition run on a background thread, and the result arrives through [playback].
 *
 * Close it when the editor goes away: it holds video decoders, an audio track and GPU memory.
 */
interface PreviewSession : AutoCloseable {
    /** Where the preview stands. Changes a few times per second at most; safe to copy into UI state. */
    val playback: StateFlow<PreviewPlayback>

    /**
     * The time on screen in microseconds, from 0 to the project's length. While playing it changes with every
     * displayed frame, so read it where it's drawn (layout or draw), never into a screen-wide UI state.
     */
    val positionUs: StateFlow<Long>

    /** Plays from [positionUs]. Before the first frame is ready, playback starts as soon as it is. */
    fun play()

    /** Stops on the current frame. */
    fun pause()

    /** Shows the frame at [positionUs], clamped to the project. Keeps playing if it was playing. Silent. */
    fun seekTo(positionUs: Long)

    /**
     * Renders [project] from now on, keeping the position (clamped to the new length) and play or pause. Call it
     * after every committed edit, undo or redo. Several quick calls only render the last project.
     */
    fun update(project: Project)

    /** Stops playback and frees the decoders and the surface. Further calls do nothing. */
    override fun close()
}

/** What a [PreviewSession] reports. */
data class PreviewPlayback(
    val status: PreviewStatus,
    /**
     * True from [PreviewSession.play] until [PreviewSession.pause], the end of the project, or an interruption
     * (a call or another app taking the sound, headphones unplugged). It follows the request at once, so a play
     * button can switch before the first frame plays.
     */
    val isPlaying: Boolean,
    /** True once a picture has been shown; until then the editor shows its empty frame. */
    val firstFrameShown: Boolean,
    /** True when at least one clip's media file is missing or unreadable; such clips play as black and silence. */
    val missingMedia: Boolean,
) {
    companion object {
        val Preparing = PreviewPlayback(PreviewStatus.PREPARING, isPlaying = false, firstFrameShown = false, missingMedia = false)
    }
}

enum class PreviewStatus {
    /** Reading the media files and building the composition; the first frame isn't ready yet. */
    PREPARING,

    /** Playing or ready to play. */
    READY,

    /**
     * The project can't be rendered or played (a damaged project, a decoder failure). Edits still work; a new
     * session from [VideoEngine.createPreview] tries again.
     */
    FAILED,
}
