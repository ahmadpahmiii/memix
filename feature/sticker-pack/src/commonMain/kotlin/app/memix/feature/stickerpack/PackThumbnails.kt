package app.memix.feature.stickerpack

import androidx.compose.ui.graphics.ImageBitmap
import app.memix.core.ui.scene.SceneImageSource
import app.memix.core.ui.scene.SceneImageUse

/**
 * Thumbnails of the stickers and tray icons on screen, decoded once each at [THUMBNAIL_PX] (a tile is under 128 dp wide) and kept while the
 * screen is open, so scrolling the grid back never decodes again. Read and written on the main thread; the decoder works off it.
 */
internal class PackThumbnails(private val source: SceneImageSource) {
    private val decoded = HashMap<String, ImageBitmap?>()

    /** The picture of [path] if it was decoded before. A null inside means it was tried and can't be read. */
    fun cached(path: String): Pair<Boolean, ImageBitmap?> = (path in decoded) to decoded[path]

    suspend fun load(path: String): ImageBitmap? {
        if (path in decoded) return decoded[path]
        val image = source.decode(path, THUMBNAIL_PX, SceneImageUse.SCREEN)?.image
        decoded[path] = image
        return image
    }

    private companion object {
        const val THUMBNAIL_PX = 128
    }
}
