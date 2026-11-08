package app.memix.core.domain.photo

import app.memix.core.domain.video.ExportError
import app.memix.core.model.project.PhotoExportFormat
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project

/** Save to the gallery, or put the picture on the clipboard (spec docs/ux/specs/P2-08-photo-export.md → Export sheet). */
enum class PhotoExportAction(val eventValue: String) {
    SAVE("save"),
    COPY("copy"),
}

/** What the sheet chose: the file [format], the JPG [quality] (ignored for PNG) and the canvas's shorter side in pixels. */
data class PhotoExportSettings(val format: PhotoExportFormat, val quality: Int, val shortSidePx: Int)

/** Where a rendered picture goes. */
enum class PhotoDestination {
    /** The gallery's "Memix" album: the user's saved pictures. */
    GALLERY,

    /** One file in the app's cache for the clipboard; the next start deletes it. */
    CLIPBOARD_FILE,

    /** The app's cache, for the debug export hook, whose owner steps pull the file with adb. */
    APP_CACHE,
}

/** One render: the [settings], where it goes, and whether the "Memix" mark is drawn (on for every export; only the debug hook turns it off). */
data class PhotoRenderRequest(val settings: PhotoExportSettings, val destination: PhotoDestination, val watermark: Boolean = true)

/** A finished picture: a content URI in the gallery, or an absolute path in the app's cache. */
data class RenderedPhoto(val location: String, val sizeBytes: Long, val pixelSize: PixelSize, val format: PhotoExportFormat)

/** What [PhotoRenderer.render] ends with. */
sealed interface PhotoRenderOutcome {
    data class Done(val photo: RenderedPhoto) : PhotoRenderOutcome

    data class Failed(val error: ExportError) : PhotoRenderOutcome
}

/**
 * Draws a photo project's scene at export size and writes the picture (spec P2-08). The scene is drawn by the one
 * scene-drawing function the editor uses, so the file equals what the user saw, plus the mark drawn last.
 */
interface PhotoRenderer {
    /**
     * Renders [project] as [request] says. Runs off the main thread and cancels at once: a partial file is deleted.
     * Failures come back as [PhotoRenderOutcome.Failed] (a full disk, a size the phone can't make, a picture that
     * can't be read); only cancellation is thrown.
     */
    suspend fun render(project: Project, request: PhotoRenderRequest): PhotoRenderOutcome

    /** Deletes the clipboard copies earlier runs left in the cache. Call at start-up, never while a copy is being made. */
    suspend fun deleteOldCopies()
}

/** Puts a picture on the clipboard so the user can paste it in a chat (Android: a FileProvider URI in a `ClipData`). */
interface PictureClipboard {
    /** True when the phone shows its own confirmation after a copy (Android 13 and up), so the app must not add a second. */
    val confirmsCopyItself: Boolean

    /** Copies the file at [path] as an image of [mimeType]. False when the clipboard refused it. */
    fun copyPicture(path: String, mimeType: String): Boolean
}
