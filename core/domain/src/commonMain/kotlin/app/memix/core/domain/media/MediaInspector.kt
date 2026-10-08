package app.memix.core.domain.media

import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.PixelSize

/**
 * Checks that a copied file will play, and measures it. Implemented in :platform:services with the phone's own
 * media framework, so "plays" means this phone has a decoder for it. Runs on an I/O dispatcher; safe to call
 * from the main thread.
 */
interface MediaInspector {
    /**
     * For a video: a video track this phone can decode, and a length over zero. For a photo: the picture decodes.
     * For a sound: a sound track this phone can decode, and a length over zero. Returns null when the file fails
     * the check (missing, damaged, or a format the phone can't play). [path] is relative to the app's files
     * directory. A video whose sound can't be decoded still passes, with [MediaFacts.hasAudio] false, because its
     * picture is still usable.
     */
    suspend fun inspect(path: String, kind: MediaKind): MediaFacts?
}

/** What [MediaInspector.inspect] measured. */
data class MediaFacts(
    /** The whole file's length in microseconds (its longest track); null for photos. */
    val durationUs: Long?,
    /** As shown, rotation and EXIF orientation applied; null for sounds. */
    val pixelSize: PixelSize?,
    /** A sound track this phone can decode; always false for photos. */
    val hasAudio: Boolean,
)
