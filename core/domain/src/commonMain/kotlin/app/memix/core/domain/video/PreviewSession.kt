package app.memix.core.domain.video

import app.memix.core.domain.voiceover.ClockSample
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.PixelSize
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

    /**
     * Shows the project at [positionUs] again after [showTrimFrame]; does nothing otherwise. The trim's [update] may
     * come just before it (P1-06 P8): both land in one build.
     */
    fun endTrimFrames()

    /**
     * While caption [itemId] is typed or moved on the preview (P1-10), the preview leaves it out and the editor's
     * stand-in draws it instead, so it never shows twice. Takes effect on the paused frame without rebuilding. null
     * shows every caption again; the [update] that commits the edit usually comes first.
     */
    fun hideCaption(itemId: String?)

    /**
     * While a finger moves overlay [itemId] (P4-01b): the preview draws it invisible, because the editor's stand-in follows
     * the finger instead, so it never shows twice. Takes effect on the paused frame without rebuilding. null draws every
     * overlay again; the [update] that commits the gesture usually comes first and ends the hiding by itself.
     */
    fun hideOverlay(itemId: String?)

    /**
     * While the Opacity slider moves (P4-01b): draws overlay [itemId] at [opacity] (0.1 to 1) at once, paused or playing; null
     * ends it. The [update] that commits the change ends it by itself, since the new composition draws that opacity.
     */
    fun previewOpacity(itemId: String, opacity: Float?)

    /**
     * While the Volume panel's slider moves (P1-08, P1-09): plays sound or clip [itemId] at [volume] (1 = its own
     * level) right away, without rebuilding, so the change is heard live, also when raising it from 0%. Nothing plays
     * for a clip whose sound is muted with every clip's, or detached. The next [update] replaces it with the
     * project's volume.
     */
    fun previewVolume(itemId: String, volume: Float)

    /**
     * While an Audio effects fade slider moves (P3-09): sets the two fades of sound [itemId] (µs of timeline time) on the playing
     * sound right away, without rebuilding. Pitch and speed can't be changed this way: they are heard after the release, when the
     * composition is rebuilt. The next [update] replaces it with the project's fades.
     */
    fun previewFades(itemId: String, fadeInUs: Long, fadeOutUs: Long)

    /**
     * While an Audio effects Bass boost or Distortion slider moves (P3-09b): sets both ([bassBoost] and [distortion], 0 to 1) of sound
     * [itemId] on the playing sound right away, without rebuilding. The echo, the echo length and the voice change the sound's length or
     * the chain's shape, so they are heard after the release, when the composition is rebuilt. The next [update] replaces it with the project's values.
     */
    fun previewBass(itemId: String, bassBoost: Float, distortion: Float)

    /**
     * While a voiceover records without headphones (P3-08): plays the picture but none of the project's sound, so the
     * speaker's sound doesn't go into the take. Engine only: the project, its volumes and its mutes are not touched, and
     * [update] and seeks keep it. false plays the sound again.
     */
    fun setSilent(silent: Boolean)

    /**
     * The position the player is at, with the monotonic clock's reading at that instant, for placing a voiceover take
     * (P3-08, [app.memix.core.domain.voiceover.placeTake]). Meaningful only while [PreviewPlayback.isPlaying]; null before the
     * first composition is on the player.
     */
    fun sampleClock(): ClockSample?

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
     * Overlay clips (P4-01) the preview leaves out because the phone can't decode that many videos at once; the export leaves out
     * the same ones. Empty when all draw. The editor can say so (copy and policy: P4-01b).
     */
    val skippedOverlayIds: List<String> = emptyList(),
    /** [missingMedia] and every file that is missing belongs to an overlay, not to a main clip (P4-01b: the editor words it differently). */
    val onlyOverlaysMissing: Boolean = false,
    /**
     * True from an [PreviewSession.update] that changed the project until the changed project's first frame shows
     * (or the preview fails). Meanwhile the picture is the last frame before the edit (P1-06 → Preview catching up).
     */
    val isCatchingUp: Boolean = false,
    /**
     * The canvas size of the picture on screen (P1-11): after a ratio change it stays the old size until the first frame
     * at the new size shows, so the editor can cover a picture of the wrong shape. Null until the first frame.
     */
    val shownCanvasSize: PixelSize? = null,
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
