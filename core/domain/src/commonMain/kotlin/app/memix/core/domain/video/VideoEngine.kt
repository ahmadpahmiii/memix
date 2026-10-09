package app.memix.core.domain.video

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.model.project.Project

/**
 * Renders video projects with the phone's own media hardware: Media3 on Android, AVFoundation on iOS
 * (P7-03 to P7-05). Features reach it through use cases only (CLAUDE.md rule 2).
 *
 * Rendered today: the main video track (videos and photos, trims, fit or fill, each clip's own audio
 * and volume) and the meme sound and audio tracks (trims, volume, muted tracks, detached audio).
 * Overlay, text, sticker and effect tracks and the canvas background are skipped with a log line
 * until their tickets add them (P4-01, P1-10, P4-12, P4-06, P1-11).
 *
 * Waveforms (P1-08 / P3-09) and device capabilities (P1-12) join this interface with the tickets
 * that first use them.
 */
interface VideoEngine {
    /**
     * Writes [project] to a new MP4 file: H.264 video at the canvas size and [ExportSettings.frameRate],
     * plus AAC audio when anything in the project is heard. The file is as long as the main video track;
     * sounds that run past its end are cut off.
     *
     * Main-safe; suspends until the file is complete. Cancelling the calling coroutine stops the export
     * and deletes the partial file. [onProgress] receives 0.0 to 1.0 about every 100 ms on the engine's
     * export thread, so keep it cheap.
     *
     * Fails with [AppError.NotFound] when a clip's media file is missing or can't be read,
     * [AppError.StorageFull] when the disk fills up, and [AppError.Unexpected] for anything else,
     * including a project with nothing on its main video track.
     */
    suspend fun export(project: Project, settings: ExportSettings, onProgress: (Float) -> Unit): Outcome<ExportedVideo>

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
}
