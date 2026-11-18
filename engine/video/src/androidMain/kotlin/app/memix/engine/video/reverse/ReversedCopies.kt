package app.memix.engine.video.reverse

import android.net.Uri
import app.memix.core.domain.Logger
import app.memix.core.model.project.MediaRef
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Where one reversed range stands (P4-10). */
internal sealed interface CopyState {
    /** The copy is on disk: it holds [range] of the source, and [file] plays it from the end to the start of that range. */
    data class Ready(val file: File, val range: ReversalKey) : CopyState

    /** Being made; [percent] is 0 to 100. */
    data class Preparing(val percent: Int) : CopyState

    /** Making it failed; the clip stays a still until the user turns Reverse off ([noSpace]: the phone is full). */
    data class Failed(val noSpace: Boolean, val tooLong: Boolean = false) : CopyState
}

/**
 * The reversed copies of the app (P4-10): a cache folder of finished copies with stable names, and the one background worker that makes the missing ones.
 * The preview and the export both ask through [request]; a copy no one asks for any more is cancelled at once (un-reversing, Undo, closing the editor), a
 * finished one is kept for a week or until the folder passes [CACHE_LIMIT_BYTES]. Copies are made one at a time: each holds a decoder, an encoder and GL.
 */
internal class ReversedCopies(
    private val folder: File,
    ioDispatcher: CoroutineDispatcher,
    private val maker: ReverseCopyMaker,
    private val logger: Logger,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val oneAtATime = Mutex()
    private val lock = Any()
    private val wanted = HashMap<Any, Map<ReversalKey, Uri>>()
    private val jobs = HashMap<ReversalKey, Job>()
    private val percents = ConcurrentHashMap<ReversalKey, Int>()
    private val failures = ConcurrentHashMap<ReversalKey, CopyState.Failed>()
    @Volatile private var tidied = false

    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits when any copy's progress moved, one finished or one failed. */
    val changes: SharedFlow<Unit> = mutableChanges.asSharedFlow()

    /**
     * [owner] needs these ranges ([sources] has each one's file) and nothing else: missing copies start being made, and copies no owner needs any more are
     * cancelled and forgotten. Reads the cache folder: call off the main thread. Returns each range's state.
     */
    fun request(owner: Any, sources: Map<ReversalKey, Uri>): Map<ReversalKey, CopyState> {
        tidyOnce()
        synchronized(lock) {
            wanted[owner] = sources
            for ((key, uri) in sources) {
                if (stateOf(key) == null) startJob(key, uri)
            }
            dropUnwanted()
        }
        return sources.keys.associateWith { stateOf(it) ?: CopyState.Preparing(0) }
    }

    /** [owner] needs nothing: every copy only it wanted is cancelled. */
    fun release(owner: Any) {
        synchronized(lock) {
            wanted.remove(owner)
            dropUnwanted()
        }
    }

    /**
     * Waits until every range of [sources] is a finished copy, for the export; [onProgress] gets 0 to 1 of the slowest. Throws [ReverseException] when one
     * fails. Cancelling the caller releases what only it wanted.
     */
    suspend fun awaitAll(owner: Any, sources: Map<ReversalKey, Uri>, onProgress: (Float) -> Unit): Map<ReversalKey, CopyState.Ready> {
        try {
            while (true) {
                val states = request(owner, sources)
                states.values.filterIsInstance<CopyState.Failed>().firstOrNull()?.let { throw ReverseException("A reversed copy failed", noSpace = it.noSpace, tooLong = it.tooLong) }
                if (states.values.all { it is CopyState.Ready }) return states.mapValues { it.value as CopyState.Ready }
                val total = states.values.sumOf { (it as? CopyState.Preparing)?.percent ?: FULL_PERCENT }
                onProgress(total / (states.size * FULL_PERCENT.toFloat()))
                // A change that lands between the check above and this wait is not replayed, so the wait is short and the states are read again.
                withTimeoutOrNull(POLL_MS) { changes.first() }
            }
        } finally {
            release(owner)
        }
    }

    /** The state of [key]: a finished copy that holds its range, else its running job, else its failure; null when nothing has been started for it. */
    fun stateOf(key: ReversalKey): CopyState? {
        readyFor(key)?.let { return it }
        failures[key]?.let { return it }
        percents[key]?.let { return CopyState.Preparing(it) }
        return null
    }

    // A finished copy of the same source whose range holds [key]'s: trimming a reversed clip down reuses its copy.
    private fun readyFor(key: ReversalKey): CopyState.Ready? {
        val prefix = sourcePrefix(key.source)
        val files = folder.listFiles { file -> file.name.startsWith(prefix) && file.name.endsWith(COPY_SUFFIX) }.orEmpty()
        for (file in files) {
            val (start, end) = rangeOf(file.name) ?: continue
            if (start <= key.startUs && end >= key.endUs) {
                // Used now: the clean-up leaves it alone.
                file.setLastModified(System.currentTimeMillis())
                return CopyState.Ready(file, ReversalKey(key.source, start, end))
            }
        }
        return null
    }

    private fun startJob(key: ReversalKey, uri: Uri) {
        percents[key] = 0
        jobs[key] = scope.launch {
            val self = coroutineContext[Job]
            try {
                oneAtATime.withLock {
                    // Turned off and on again while an older job for this range was still running: its copy may be there now.
                    if (readyFor(key) == null) make(key, uri)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReverseException) {
                logger.error(TAG, "Reversing failed: ${e.message}", e)
                failures[key] = CopyState.Failed(e.noSpace, e.tooLong)
            } catch (e: Exception) {
                // A codec or file error of any kind: the clip stays a still and the editor turns Reverse off.
                logger.error(TAG, "Reversing failed", e)
                failures[key] = CopyState.Failed(noSpace = false)
            } finally {
                // Only this job's own entries: a newer job for the same range (after a cancel and a new request) keeps its.
                synchronized(lock) {
                    if (jobs[key] === self) {
                        jobs.remove(key)
                        percents.remove(key)
                    }
                }
                mutableChanges.tryEmit(Unit)
            }
        }
    }

    private suspend fun make(key: ReversalKey, uri: Uri) {
        val target = File(folder, copyName(key))
        var lastPercent = 0
        maker.make(key, uri, target) { fraction ->
            val percent = (fraction * FULL_PERCENT).toInt().coerceIn(0, FULL_PERCENT)
            if (percent != lastPercent) {
                lastPercent = percent
                percents[key] = percent
                mutableChanges.tryEmit(Unit)
            }
        }
        logger.debug(TAG, "Reversed ${key.spanUs / MICROS_PER_MS} ms into ${target.name} (${target.length() / BYTES_PER_KB} KB)")
        trimCache()
    }

    // Called with [lock] held: cancels the jobs no owner wants and forgets old failures, so turning Reverse on again tries again.
    private fun dropUnwanted() {
        val needed = wanted.values.flatMap { it.keys }.toSet()
        jobs.filterKeys { it !in needed }.forEach { (key, job) ->
            job.cancel()
            jobs.remove(key)
            percents.remove(key)
        }
        failures.keys.filter { it !in needed }.forEach { failures.remove(it) }
    }

    // Once per process: unfinished files from a killed run go, then copies not used for a week, then the oldest until the folder fits.
    private fun tidyOnce() {
        if (tidied) return
        tidied = true
        val files = folder.listFiles().orEmpty()
        files.filter { it.name.endsWith(TEMP_SUFFIX) }.forEach { it.delete() }
        val now = System.currentTimeMillis()
        val copies = files.filter { it.name.endsWith(COPY_SUFFIX) }
        copies.filter { now - it.lastModified() > MAX_AGE_MS }.forEach { it.delete() }
        trimCache()
    }

    // Oldest copies go until the folder fits; a copy a currently requested range reads from never does.
    private fun trimCache() {
        val inUse = synchronized(lock) { wanted.values.flatMap { it.keys } }
        val copies = folder.listFiles { file -> file.name.endsWith(COPY_SUFFIX) }.orEmpty()
        var total = copies.sumOf { it.length() }
        for (file in copies.sortedBy { it.lastModified() }) {
            if (total <= CACHE_LIMIT_BYTES) break
            if (inUse.any { key -> servedBy(file, key) }) continue
            total -= file.length()
            file.delete()
        }
    }

    private fun servedBy(file: File, key: ReversalKey): Boolean {
        if (!file.name.startsWith(sourcePrefix(key.source))) return false
        val (start, end) = rangeOf(file.name) ?: return false
        return start <= key.startUs && end >= key.endUs
    }

    // A name that is the same on every launch: a hash of the source file, then the range it holds.
    private fun copyName(key: ReversalKey): String = "${sourcePrefix(key.source)}${key.startUs}_${key.endUs}$COPY_SUFFIX"

    private fun sourcePrefix(source: MediaRef): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("${source.cachedCopyPath ?: source.origin}".toByteArray())
        return digest.take(HASH_BYTES).joinToString("") { "%02x".format(it) } + "_"
    }

    private fun rangeOf(name: String): Pair<Long, Long>? {
        val parts = name.removeSuffix(COPY_SUFFIX).substringAfter('_').split('_')
        if (parts.size != 2) return null
        return (parts[0].toLongOrNull() ?: return null) to (parts[1].toLongOrNull() ?: return null)
    }

    private companion object {
        const val TAG = "MemixReverse"
        const val COPY_SUFFIX = ".mp4"
        const val TEMP_SUFFIX = ".tmp"
        const val HASH_BYTES = 10
        const val FULL_PERCENT = 100
        const val POLL_MS = 500L
        const val MICROS_PER_MS = 1_000L
        const val BYTES_PER_KB = 1024
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val CACHE_LIMIT_BYTES = 1024L * 1024 * 1024
    }
}
