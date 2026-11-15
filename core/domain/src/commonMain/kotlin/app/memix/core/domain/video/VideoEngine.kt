package app.memix.core.domain.video

import app.memix.core.domain.Outcome
import app.memix.core.domain.sound.SoundPlayer
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project

/**
 * Renders video projects with the phone's own media hardware: Media3 on Android, AVFoundation on iOS
 * (P7-03 to P7-05). Features reach it through use cases only (CLAUDE.md rule 2).
 *
 * Rendered today: the main video track (videos and photos, trims, fit or fill, each clip's own audio
 * and volume), the canvas background around fitted clips (P1-11), captions (P1-10) and the meme sound
 * and audio tracks (trims, volume, muted tracks, detached audio). Overlay, sticker and effect tracks are
 * skipped with a log line until their tickets add them (P4-01, P4-12, P4-06).
 *
 * Meme sounds and audio clips also play with the Audio effects of P3-09 (fades, pitch, speed): the preview and the export
 * build the same chain from the clip's [app.memix.core.model.project.AudioEffects].
 */
interface VideoEngine {
    /**
     * Writes [project] to a new MP4 file at [ExportSettings.destination]: [ExportSettings.codec] video at
     * [ExportSettings.frameSize] (or the canvas scaled to [ExportSettings.shortSidePx], or the canvas) and
     * [ExportSettings.frameRate], plus AAC audio when anything in the project is heard. The file is as long as the
     * main video track; sounds that run past its end are cut off. A clip whose file is already missing exports black
     * and silent (the sheet says so first); one that goes missing during the export fails it.
     *
     * Main-safe; suspends until the file is complete. Cancelling the calling coroutine stops the export and deletes
     * the partial file. [onProgress] receives 0.0 to 1.0 about every 100 ms on the engine's export thread, so keep it
     * cheap. A gallery file is visible to other apps only once it's complete.
     */
    suspend fun export(project: Project, settings: ExportSettings, onProgress: (Float) -> Unit): ExportOutcome

    /**
     * What this phone can export for a canvas of [canvasSize] (P1-12): each resolution and frame rate's codec, aligned
     * frame size and bitrate. The phone's encoders are read once per process, off the main thread; later calls are
     * quick. Main-safe.
     */
    suspend fun exportCapabilities(canvasSize: PixelSize): ExportCapabilities

    /**
     * Deletes gallery files an earlier run started but never finished (the app was stopped mid-export). Main-safe;
     * nothing happens when there are none.
     */
    suspend fun discardUnfinishedExports()

    /**
     * Starts a live preview of [project], rendered like [export] (same tracks, trims, fit and volumes) at
     * [ExportSettings.DEFAULT_FRAME_RATE] or less. Returns at once, paused at 0; the first frame follows when the
     * media is read. Main thread only. A clip whose media file is missing or unreadable plays as black and
     * silence and sets [PreviewPlayback.missingMedia]; a project that can't be rendered at all ends in
     * [PreviewStatus.FAILED]. The caller closes the session.
     */
    fun createPreview(project: Project): PreviewSession

    /**
     * Opens a [ThumbnailReader] for the timeline's thumbnail strips (P1-05). Returns at once; nothing is read until
     * the first [ThumbnailReader.thumbnail]. The caller closes it.
     */
    fun openThumbnails(): ThumbnailReader

    /**
     * Opens a [SoundPlayer] for playing single sounds outside a project, such as the sounds sheet's previews (P1-08).
     * Returns at once; nothing loads until the first [SoundPlayer.play]. Main thread only. The caller closes it.
     */
    fun openSoundPlayer(): SoundPlayer

    /**
     * The peaks of the sound in [source], for the waveform on the timeline (P3-09): about 100 a second over the whole file,
     * decoded on the engine's background thread and kept on disk, so the second call for a file is quick. Null when the file
     * is missing ([app.memix.core.domain.AppError.NotFound]: the Audio effects tool explains) or can't be decoded
     * ([app.memix.core.domain.AppError.Unexpected]: no bars, nothing else). Main-safe; cancelling drops the result.
     */
    suspend fun waveform(source: MediaRef): Outcome<AudioPeaks>

    /**
     * Opens a [ResultPlayer] for a finished export at [location] (P1-12's Exported screen). Returns at once; the first
     * frame shows when the file is ready. Main thread only. The caller closes it.
     */
    fun openResultPlayer(location: String): ResultPlayer
}
