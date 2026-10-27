package app.memix.core.domain.video

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.model.project.MediaRef

/**
 * Reads still pictures of a project's media for the timeline's thumbnail strip (P1-05), from
 * [VideoEngine.openThumbnails]. Each picture is one square tile, already cropped and sized, so the UI only decodes and
 * draws it.
 *
 * Main-safe: the decoding runs on the engine's own background thread, one picture at a time, in the order asked.
 * Cancelling the calling coroutine drops the result; a picture already being decoded finishes first (the platform
 * decoders can't be interrupted), so ask for the tiles nearest the playhead first and cancel the rest when they
 * scroll away.
 *
 * Close it when the editor goes away: it keeps media files open. Calls after [close] fail with [AppError.Unexpected].
 */
interface ThumbnailReader : AutoCloseable {
    /**
     * The picture of [source] at [timeUs] in the source file, center-cropped to a square of [sizePx] pixels. For a
     * video it's the frame shown at [timeUs] (the closest frame, not just the closest keyframe), clamped to the file;
     * a photo ignores [timeUs].
     *
     * Fails with [AppError.NotFound] when the media file isn't on the phone, and [AppError.Unexpected] when it can't
     * be decoded (damaged, or a format this phone can't read). Neither is shown as an error: the tile stays empty.
     */
    suspend fun thumbnail(source: MediaRef, timeUs: Long, sizePx: Int): Outcome<Thumbnail>

    /** Frees the open media files. Further calls do nothing. */
    override fun close()
}

/**
 * One square thumbnail, [sizePx] wide and tall, as JPEG bytes. Encoded rather than a platform bitmap so shared code
 * can hold it; the UI decodes it once into its own image cache.
 */
class Thumbnail(val jpegBytes: ByteArray, val sizePx: Int)
