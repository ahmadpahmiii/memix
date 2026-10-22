package app.memix.feature.drafts

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The drafts list's pictures: each thumbnail file read and decoded once, off the main thread, and kept in memory by
 * path (a new cover gets a new path). The files are 192 px squares, about the row's 56 dp on a 3× screen, so nothing
 * is decoded above display size. Call from the main thread.
 */
@Stable
class DraftThumbnailImages internal constructor(
    private val read: suspend (path: String) -> ByteArray?,
    private val decodeDispatcher: CoroutineDispatcher,
) {
    // Least recently used first.
    private val cache = LinkedHashMap<String, ImageBitmap>()

    /** The picture if it's already decoded, so a row that comes back on screen shows it at once. */
    fun cached(path: String): ImageBitmap? = cache[path]

    /** Reads and decodes [path]; null when the file is missing or can't be decoded (the row keeps its placeholder). */
    suspend fun load(path: String): ImageBitmap? {
        cache.remove(path)?.let { image ->
            cache[path] = image
            return image
        }
        val bytes = read(path) ?: return null
        val image = withContext(decodeDispatcher) { decodeOrNull(bytes) } ?: return null
        cache[path] = image
        if (cache.size > MAX_CACHED) cache.remove(cache.keys.first())
        return image
    }

    // Not suspending, so catching every exception can't swallow a cancellation.
    private fun decodeOrNull(bytes: ByteArray): ImageBitmap? = try {
        bytes.decodeToImageBitmap()
    } catch (e: Exception) {
        null
    }

    private companion object {
        /** About 150 KB each: a long list scrolled end to end stays under 10 MB. */
        const val MAX_CACHED = 60
    }
}
