package app.memix.feature.videoeditor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import app.memix.core.domain.Outcome
import app.memix.core.domain.video.ThumbnailReader
import app.memix.core.model.project.MediaRef
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The pictures the editor draws an overlay with while a finger moves it (spec P4-01b → Live feedback): the preview hides the
 * overlay and this stand-in follows the fingers, so the overlay never shows twice. Each shown overlay has one picture, its frame
 * at the paused playhead (the whole picture in its own shape, not a square tile), asked for ahead of time so a touch finds it
 * ready. [version] goes up with each picture that arrives, so only the stand-in's draw reads it. Main thread only; the editor's
 * ViewModel owns it and closes it, which closes the reader.
 */
@Stable
class OverlayFrames internal constructor(
    private val reader: ThumbnailReader,
    private val scope: CoroutineScope,
    private val decodeDispatcher: CoroutineDispatcher,
) : AutoCloseable {
    /** Goes up by one with every picture that arrives. */
    var version by mutableIntStateOf(0)
        private set

    // Oldest asked for first: [want] moves a frame it finds to the end, so eviction never drops one still shown.
    private val frames = LinkedHashMap<String, Frame>()
    private val loading = HashMap<String, Job>()
    private val pending = HashMap<String, Key>()

    // Pictures the reader couldn't give (a damaged file): not asked for again at that time and size.
    private val failed = HashSet<Key>()

    /** Asks for [overlay]'s frame at [playheadUs], [longSidePx] on its long side (its drawn size), unless that picture is already here or on its way. */
    internal fun want(overlay: OverlayUi, playheadUs: Long, longSidePx: Int) {
        val timeUs = overlay.sourceTimeAt(playheadUs)
        val key = Key(overlay.media, timeUs, longSidePx)
        if (key in failed) return
        val here = frames[overlay.itemId]
        if (here?.key == key) {
            frames.remove(overlay.itemId)
            frames[overlay.itemId] = here
            return
        }
        if (loading[overlay.itemId]?.isActive == true && pending[overlay.itemId] == key) return
        loading.remove(overlay.itemId)?.cancel()
        pending[overlay.itemId] = key
        loading[overlay.itemId] = scope.launch {
            val read = reader.frame(overlay.media, timeUs, longSidePx) as? Outcome.Success
            if (read == null) {
                failed += key
                return@launch
            }
            val image = try {
                withContext(decodeDispatcher) { read.value.jpegBytes.decodeToImageBitmap() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The engine wrote these bytes itself, so this is unexpected; the overlay just has no stand-in.
                failed += key
                return@launch
            }
            frames.remove(overlay.itemId)
            frames[overlay.itemId] = Frame(key, image)
            trimToLimit()
            version++
        }
    }

    /** The picture to draw [itemId] with, or null while none has arrived. */
    fun imageOf(itemId: String): ImageBitmap? = frames[itemId]?.image

    /**
     * Whether [overlay] can be drawn by the stand-in at [playheadUs]: the picture held is its frame at that time, from its current
     * source, so the preview may hide it. A picture from another time or an old trim isn't ready.
     */
    fun isReady(overlay: OverlayUi, playheadUs: Long): Boolean {
        val held = frames[overlay.itemId]?.key ?: return false
        return held.media == overlay.media && held.timeUs == overlay.sourceTimeAt(playheadUs)
    }

    override fun close() {
        loading.values.forEach(Job::cancel)
        loading.clear()
        frames.clear()
        reader.close()
    }

    private fun trimToLimit() {
        while (frames.size > MAX_FRAMES) frames.remove(frames.keys.first())
    }

    private data class Key(val media: MediaRef, val timeUs: Long, val longSidePx: Int)

    private class Frame(val key: Key, val image: ImageBitmap)

    private companion object {
        /** Five overlays at once is the target; a few more keep the ones just scrolled past. */
        const val MAX_FRAMES = 8
    }
}
