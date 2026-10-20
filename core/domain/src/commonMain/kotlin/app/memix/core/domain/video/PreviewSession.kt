package app.memix.core.domain.video

import app.memix.core.model.project.MediaClip
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
     * True while the user drags or flings the timeline (P1-05): many quick [seekTo] calls follow, and the preview may
     * show the nearest frame it can decode fast. False at rest, which shows the exact frame of the last seek. Pause
     * before turning it on: playback stays suppressed while it's on.
     */
    fun setScrubbing(scrubbing: Boolean)

    /**
     * Renders [project] from now on, keeping the position (clamped to the new length) and play or pause. Call it
     * after every committed edit, undo or redo. Several quick calls only render the last project. Until the new
     * project's first frame shows, the last frame stays on screen and [PreviewPlayback.isCatchingUp] is true.
     */
    fun update(project: Project)

    /**
     * While a trim handle of a video or photo clip is dragged (P1-06): shows [clip]'s source at [sourceTimeUs]
     * (µs in the source file, not the project) instead of the project, framed and fitted as the project shows it,
     * silent. Many quick calls follow: the first one takes about as long as an edit, later ones are fast seeks.
     * [positionUs] and play or pause don't change. Call [endTrimFrames] when the finger lifts.
     */
    fun showTrimFrame(clip: MediaClip, sourceTimeUs: Long)

    /** Shows the project at [positionUs] again after [showTrimFrame]; does nothing otherwise. Commit the trim after it. */
    fun endTrimFrames()

    /**
     * While the Volume panel's slider moves (P1-08, P1-09): plays sound or clip [itemId] at [volume] (1 = its own
     * level) right away, without rebuilding, so the change is heard live, also when raising it from 0%. Nothing plays
     * for a clip whose sound is muted with every clip's, or detached. The next [update] replaces it with the
     * project's volume.
     */
    fun previewVolume(itemId: String, volume: Float)

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
    /**
     * True from an [PreviewSession.update] that changed the project until the changed project's first frame shows
     * (or the preview fails). Meanwhile the picture is the last frame before the edit (P1-06 → Preview catching up).
     */
    val isCatchingUp: Boolean = false,
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
