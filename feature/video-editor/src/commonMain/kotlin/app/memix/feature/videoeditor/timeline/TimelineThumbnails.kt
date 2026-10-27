package app.memix.feature.videoeditor.timeline

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.video.Thumbnail
import app.memix.core.domain.video.ThumbnailReader
import app.memix.core.model.project.MediaRef
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The thumbnail tiles of the timeline's strips (spec P1-05 → Thumbnail strip). The timeline says which tiles it
 * wants ([want], nearest the playhead first); one worker asks the engine's [ThumbnailReader] for them in that order,
 * decodes each once off the main thread, and keeps them in a memory-bounded cache. A tile that leaves the wanted
 * window while it is being read is dropped.
 *
 * Draw code reads [tileAt] and [version] (bumped when a tile arrives, so only the strip redraws) and
 * [missingSources] (files the reader couldn't find: their clips say "File missing"). Main thread only. The editor's
 * ViewModel owns it and closes it, which closes the reader.
 */
@Stable
class TimelineThumbnails internal constructor(
    private val reader: ThumbnailReader,
    scope: CoroutineScope,
    private val decodeDispatcher: CoroutineDispatcher,
) : AutoCloseable {
    /** Goes up by one with every tile that arrives. */
    internal var version by mutableIntStateOf(0)
        private set

    /** Sources whose file isn't on the phone. */
    internal var missingSources: PersistentSet<MediaRef> by mutableStateOf(persistentSetOf())
        private set

    private val wanted = MutableStateFlow(WantedTiles.None)
    private val tilesBySource = HashMap<MediaRef, SourceTiles>()

    // Least recently wanted first (insertion order, refreshed by [want]), so eviction never drops a tile still shown.
    private val recentlyWanted = LinkedHashMap<TileRequest, ThumbnailTile>()
    private val failed = HashSet<TileRequest>()
    private var cachedBytes = 0L
    private val worker: Job = scope.launch { loadWantedTiles() }

    /**
     * The tiles the timeline needs now, most urgent first, each [sizePx] square. Replaces the previous list. A new
     * size (the screen density changed) clears the cache.
     */
    internal fun want(requests: List<TileRequest>, sizePx: Int) {
        if (sizePx != wanted.value.sizePx) clear()
        // Re-inserting a tile moves it to the most recently wanted end, out of eviction's way.
        requests.forEach { request -> recentlyWanted.remove(request)?.let { tile -> recentlyWanted[request] = tile } }
        wanted.value = WantedTiles(requests, requests.toHashSet(), sizePx)
    }

    /**
     * The tile of [source] at [timeUs] if it has arrived, else the cached tile nearest to that time (shown stretched
     * while the exact one loads), else null.
     */
    internal fun tileAt(source: MediaRef, timeUs: Long): ThumbnailTile? = tilesBySource[source]?.nearest(timeUs)

    /** The cached tile of [source] nearest to [timeUs] other than the one at [timeUs]: shown under it while it fades in. */
    internal fun standInFor(source: MediaRef, timeUs: Long): ThumbnailTile? = tilesBySource[source]?.nearestOtherThan(timeUs)

    override fun close() {
        worker.cancel()
        reader.close()
        clear()
    }

    private suspend fun loadWantedTiles() {
        while (true) {
            val next = wanted.value.requests.firstOrNull(::needsLoading)
            if (next == null) {
                // Nothing left to read: wait for a window with something new.
                wanted.first { window -> window.requests.any(::needsLoading) }
                continue
            }
            val sizePx = wanted.value.sizePx
            when (val read = readWhileWanted(next, sizePx)) {
                null -> Unit
                is Outcome.Success -> store(next, read.value, sizePx)
                is Outcome.Failure -> if (read.error == AppError.NotFound) missingSources = missingSources.adding(next.source) else failed += next
            }
        }
    }

    /** Reads [request], or gives up (null) as soon as it leaves the wanted window. */
    private suspend fun readWhileWanted(request: TileRequest, sizePx: Int): Outcome<Thumbnail>? = coroutineScope {
        val read = async { reader.thumbnail(request.source, request.timeUs, sizePx) }
        val leftWindow = launch {
            wanted.first { window -> request !in window.requestSet || window.sizePx != sizePx }
            read.cancel()
        }
        try {
            read.await()
        } catch (e: CancellationException) {
            // Our own cancellation goes on up; only the read's own cancellation means "no longer wanted".
            currentCoroutineContext().ensureActive()
            null
        } finally {
            leftWindow.cancel()
        }
    }

    private suspend fun store(request: TileRequest, thumbnail: Thumbnail, sizePx: Int) {
        val image = try {
            withContext(decodeDispatcher) { thumbnail.jpegBytes.decodeToImageBitmap() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The engine wrote these bytes itself, so this is unexpected; the tile just stays empty.
            failed += request
            return
        }
        if (sizePx != wanted.value.sizePx) return
        val tile = ThumbnailTile(request.timeUs, image, TimeSource.Monotonic.markNow())
        tilesBySource.getOrPut(request.source) { SourceTiles() }.add(tile)
        recentlyWanted[request] = tile
        cachedBytes += tile.byteCount
        evictOverBudget()
        version++
    }

    private fun evictOverBudget() {
        val iterator = recentlyWanted.entries.iterator()
        while (cachedBytes > MAX_CACHED_BYTES && iterator.hasNext()) {
            val (request, tile) = iterator.next()
            if (request in wanted.value.requestSet) break // everything after this is wanted too
            iterator.remove()
            tilesBySource[request.source]?.remove(tile)
            cachedBytes -= tile.byteCount
        }
    }

    private fun needsLoading(request: TileRequest): Boolean =
        request.source !in missingSources && request !in failed && tilesBySource[request.source]?.exact(request.timeUs) == null

    private fun clear() {
        tilesBySource.clear()
        recentlyWanted.clear()
        failed.clear()
        cachedBytes = 0
    }

    private class WantedTiles(val requests: List<TileRequest>, val requestSet: Set<TileRequest>, val sizePx: Int) {
        companion object {
            val None = WantedTiles(emptyList(), emptySet(), sizePx = 0)
        }
    }

    private companion object {
        /** About 280 tiles at 3× density: every tile of three screens at any zoom, plus what was seen just before. */
        const val MAX_CACHED_BYTES = 16L * 1024 * 1024
    }
}

/** A decoded tile. [arrivedAt] times its 120 ms fade-in. */
internal class ThumbnailTile(val timeUs: Long, val image: ImageBitmap, val arrivedAt: TimeMark) {
    val byteCount: Long get() = image.width.toLong() * image.height * BYTES_PER_PIXEL
}

/** One source's tiles, sorted by time, so the draw code finds a tile or its nearest neighbour without allocating. */
private class SourceTiles {
    private val tiles = ArrayList<ThumbnailTile>()

    fun add(tile: ThumbnailTile) {
        val index = indexOf(tile.timeUs)
        if (index >= 0) tiles[index] = tile else tiles.add(-index - 1, tile)
    }

    fun remove(tile: ThumbnailTile) {
        val index = indexOf(tile.timeUs)
        if (index >= 0 && tiles[index] === tile) tiles.removeAt(index)
    }

    fun exact(timeUs: Long): ThumbnailTile? = indexOf(timeUs).takeIf { it >= 0 }?.let(tiles::get)

    fun nearest(timeUs: Long): ThumbnailTile? {
        val index = indexOf(timeUs)
        if (index >= 0) return tiles[index]
        val after = -index - 1
        return closerOf(timeUs, before = after - 1, after = after)
    }

    fun nearestOtherThan(timeUs: Long): ThumbnailTile? {
        val index = indexOf(timeUs)
        if (index < 0) return nearest(timeUs)
        return closerOf(timeUs, before = index - 1, after = index + 1)
    }

    /** Whichever of the tiles at [before] and [after] is closer to [timeUs]; either index may be out of range. */
    private fun closerOf(timeUs: Long, before: Int, after: Int): ThumbnailTile? {
        val earlier = tiles.getOrNull(before)
        val later = tiles.getOrNull(after)
        return when {
            earlier == null -> later
            later == null -> earlier
            timeUs - earlier.timeUs <= later.timeUs - timeUs -> earlier
            else -> later
        }
    }

    // binarySearch's contract: the index if found, else -(insertion point) - 1.
    private fun indexOf(timeUs: Long): Int = tiles.binarySearch { it.timeUs.compareTo(timeUs) }
}

private const val BYTES_PER_PIXEL = 4
