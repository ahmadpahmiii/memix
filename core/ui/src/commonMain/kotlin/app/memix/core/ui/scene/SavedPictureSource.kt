package app.memix.core.ui.scene

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes a picture the export saved (spec docs/ux/specs/P2-08-photo-export.md → Exported screen) so the result screen shows
 * the file itself, at the size it is shown. Implemented in `:engine:photo` and handed to the screen by the composition root.
 */
interface SavedPictureSource {
    /**
     * The picture at [location] (a gallery content URI) scaled down so its long side is at most [maxLongSidePx], never scaled up.
     * Null when it can't be read. Runs off the main thread; safe to call from it.
     */
    suspend fun decode(location: String, maxLongSidePx: Int): ImageBitmap?
}
