package app.memix.core.data.sound

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.memix.core.data.db.MemixDatabase
import app.memix.core.data.nowEpochUs
import app.memix.core.domain.AppConfig
import app.memix.core.domain.Logger
import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.mediaUrl
import app.memix.core.domain.sound.DownloadFailure
import app.memix.core.domain.sound.DownloadState
import app.memix.core.domain.sound.SOUND_CACHE_LIMIT_BYTES
import app.memix.core.domain.sound.SoundDownloads
import app.memix.core.domain.sound.SoundPaths
import app.memix.core.domain.sound.SoundsInUse
import app.memix.core.model.catalog.LibrarySound
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "SoundDownloads"
private const val MAX_FILE_BYTES = 8L * 1024 * 1024
private const val SPACE_RESERVE_BYTES = 1L * 1024 * 1024

/** Past this many times the limit the cache is trimmed whatever drafts use (never a playing sound). */
private const val CEILING_FACTOR = 2

/** The numbers behind the download rules; the app uses the defaults, the hand checks shorten the times. */
internal class DownloadRules(
    val cacheLimitBytes: Long = SOUND_CACHE_LIMIT_BYTES,
    /** A file larger than this is not a sound; it is refused. */
    val maxFileBytes: Long = MAX_FILE_BYTES,
    /** The wait before each retry; its size is the number of retries (two, then Failed). */
    val retryWaits: List<Duration> = listOf(500.milliseconds, 1_500.milliseconds),
    /** No bytes for this long counts as a network failure (spec P3-04 → Stalled). */
    val stallTime: Duration = 15.seconds,
    /** Space left for the system after the file is written. */
    val spaceReserveBytes: Long = SPACE_RESERVE_BYTES,
)

/**
 * The downloaded sounds as files under `sounds/` plus the `sound_file` rows (SoundFiles.sq) that remember their size and
 * last use. The bytes come from [source], are written with [MediaFiles.write] (temporary name, renamed when complete),
 * and only then get a row, so a row always has a whole file behind it.
 *
 * One [Run] exists per sound being downloaded; everyone who asks joins it, and it stops when the last one leaves.
 */
internal class FileSoundDownloads(
    database: Lazy<MemixDatabase>,
    private val source: SoundFileSource,
    private val mediaFiles: MediaFiles,
    private val appConfig: AppConfig,
    private val inUse: SoundsInUse,
    private val clock: Clock,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher,
    private val rules: DownloadRules = DownloadRules(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
) : SoundDownloads {
    private val db by lazy { database.value }
    private val queries by lazy { db.soundFilesQueries }

    private val mutex = Mutex()
    private val trimMutex = Mutex()
    private val running = HashMap<String, Run>()
    private val pins = MutableStateFlow<Map<String, Int>>(emptyMap())

    init {
        // A copy cut short by a crash or kill leaves its temporary file behind; like LeftoverMediaCleaner for media/.
        // The singleton is made with the Sounds tab, before anything plays, so no write of this run is in progress.
        scope.launch { guarded("remove unfinished sound files") { mediaFiles.deleteUnfinishedCopies(SoundPaths.FOLDER) } }
    }

    private class Run(val states: MutableStateFlow<DownloadState> = MutableStateFlow(DownloadState.Progress(0f))) {
        var watchers = 0
        var job: Job? = null
    }

    override fun observeDownloadedIds(): Flow<Set<String>> =
        flow { emitAll(queries.selectFileIds().asFlow().mapToList(ioDispatcher).map { it.toSet() }) }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)

    override suspend fun downloadedFile(soundId: String): String? = guarded("read $soundId") {
        val row = queries.selectFile(soundId).executeAsOneOrNull() ?: return@guarded null
        if (mediaFiles.sizeOf(row.path) == null) {
            // The file went missing (cleared by hand, restored without it): forget the row so the sound downloads again.
            queries.deleteFile(soundId)
            return@guarded null
        }
        queries.touchFile(clock.nowEpochUs(), soundId)
        row.path
    }

    override suspend fun markUsed(soundId: String) {
        guarded("mark $soundId used") { queries.touchFile(clock.nowEpochUs(), soundId) }
    }

    override fun pin(soundId: String) = pins.update { it + (soundId to ((it[soundId] ?: 0) + 1)) }

    override fun unpin(soundId: String) = pins.update {
        val left = (it[soundId] ?: 0) - 1
        if (left > 0) it + (soundId to left) else it - soundId
    }

    override suspend fun discard(soundId: String) {
        guarded("discard $soundId") {
            // A draft that uses the file keeps it (it may be a player fault, not a damaged file); null means unknown, so keep it.
            if (inUse.ids()?.contains(soundId) != false) return@guarded
            deleteFileAndRow(soundId)
        }
    }

    override suspend fun discardUnused(soundIds: Set<String>) {
        guarded("discard removed sounds") {
            val protected = protectedIds() ?: return@guarded
            soundIds.filter { it !in protected }.forEach { deleteFileAndRow(it) }
        }
    }

    // ---- Downloading ----

    override fun download(sound: LibrarySound): Flow<DownloadState> = flow {
        val run = join(sound)
        try {
            emitAll(run.states.transformWhile { state -> emit(state); state is DownloadState.Progress })
        } finally {
            withContext(NonCancellable) { leave(sound.id, run) }
        }
    }

    private suspend fun join(sound: LibrarySound): Run = mutex.withLock {
        running[sound.id]?.let { existing ->
            existing.watchers++
            return@withLock existing
        }
        val run = Run().also { it.watchers = 1 }
        running[sound.id] = run
        run.job = scope.launch { execute(sound, run) }
        run
    }

    private suspend fun leave(soundId: String, run: Run) = mutex.withLock {
        run.watchers--
        if (run.watchers > 0) return@withLock
        if (running[soundId] === run) running.remove(soundId)
        run.job?.cancel()
    }

    private suspend fun execute(sound: LibrarySound, run: Run) {
        val outcome = try {
            fetchAndKeep(sound, run.states)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A database or disk error: nothing may escape this scope, whose uncaught errors would end the app.
            logger.error(TAG, "Download could not be kept", e)
            DownloadState.Failed(DownloadFailure.UNAVAILABLE)
        }
        // Out of the table before the answer is published, so a new request starts afresh instead of joining a finished run.
        mutex.withLock { if (running[sound.id] === run) running.remove(sound.id) }
        if (outcome is DownloadState.Failed) logger.error(TAG, "Download failed: ${outcome.reason}")
        // Trimmed before the answer, so "Done" always means the cache is back within its limit. Not cancellable: the
        // file is already in place, and a half-done trim would leave the cache over its limit until the next download.
        if (outcome is DownloadState.Done) withContext(NonCancellable) { guarded("trim the cache") { trimCache(justDownloaded = sound.id) } }
        run.states.value = outcome
    }

    private suspend fun fetchAndKeep(sound: LibrarySound, states: MutableStateFlow<DownloadState>): DownloadState {
        downloadedFile(sound.id)?.let { return DownloadState.Done(it) }
        if (!SoundPaths.isSafeId(sound.id)) return DownloadState.Failed(DownloadFailure.UNAVAILABLE)
        val url = mediaUrl(appConfig.mediaBaseUrl, sound.filePath) ?: return DownloadState.Failed(DownloadFailure.UNAVAILABLE)
        val bytes = when (val fetched = fetchWithRetries(url, states)) {
            is Fetched.Bytes -> fetched.bytes
            is Fetched.Failure -> return DownloadState.Failed(fetched.reason)
        }
        // Not cancellable: a cancel between the write and the row would leave a finished file nobody knows about.
        return withContext(ioDispatcher + NonCancellable) { keep(sound, bytes) }
    }

    private sealed interface Fetched {
        class Bytes(val bytes: ByteArray) : Fetched
        class Failure(val reason: DownloadFailure) : Fetched
    }

    private suspend fun fetchWithRetries(url: String, states: MutableStateFlow<DownloadState>): Fetched {
        var retries = 0
        while (true) {
            val fetched = fetchOnce(url, states)
            val canRetry = fetched is Fetched.Failure && fetched.reason == DownloadFailure.NETWORK && retries < rules.retryWaits.size
            if (!canRetry) return fetched
            delay(rules.retryWaits[retries++])
            states.value = DownloadState.Progress(0f)
        }
    }

    private suspend fun fetchOnce(url: String, states: MutableStateFlow<DownloadState>): Fetched = try {
        Fetched.Bytes(withStallWatch { sawBytes ->
            source.fetch(url, rules.maxFileBytes) { received, total ->
                sawBytes(received)
                states.value = DownloadState.Progress(total?.takeIf { it > 0 }?.let { (received.toFloat() / it).coerceIn(0f, 1f) })
            }
        })
    } catch (e: CancellationException) {
        throw e
    } catch (e: SoundFileUnavailableException) {
        Fetched.Failure(DownloadFailure.UNAVAILABLE)
    } catch (e: Exception) {
        // The class only: a message can hold the address. A stall is also a network failure.
        logger.debug(TAG, "Attempt failed: ${e::class.simpleName}")
        Fetched.Failure(DownloadFailure.NETWORK)
    }

    private class StalledException : Exception("No bytes for too long")

    // Runs [body] and fails it with StalledException when no bytes arrive for the stall time. Each report of progress
    // restarts the wait (collectLatest cancels the old delay), and the first wait starts with the request.
    private suspend fun <T> withStallWatch(body: suspend (sawBytes: (Long) -> Unit) -> T): T = coroutineScope {
        val bytesSeen = MutableStateFlow(-1L)
        val watchdog = launch {
            bytesSeen.collectLatest {
                delay(rules.stallTime)
                throw StalledException()
            }
        }
        try {
            body { received -> bytesSeen.value = received }
        } finally {
            watchdog.cancel()
        }
    }

    // Checks for room, writes the file whole, then records it. Returns the state to publish.
    private suspend fun keep(sound: LibrarySound, bytes: ByteArray): DownloadState {
        if (mediaFiles.allocatableBytes() < bytes.size + rules.spaceReserveBytes) return DownloadState.Failed(DownloadFailure.STORAGE_FULL)
        val path = SoundPaths.fileOf(sound.id, sound.filePath)
        return when (mediaFiles.write(bytes, path)) {
            CopyResult.COPIED -> {
                queries.insertFile(sound.id, path, bytes.size.toLong(), clock.nowEpochUs())
                DownloadState.Done(path)
            }
            CopyResult.STORAGE_FULL -> DownloadState.Failed(DownloadFailure.STORAGE_FULL)
            CopyResult.UNREADABLE -> DownloadState.Failed(DownloadFailure.UNAVAILABLE)
        }
    }

    // ---- The 500 MB cache ----

    // Removes the least recently used files until the cache fits its limit. Skips the sound just downloaded, what a draft
    // uses and what is pinned; when a newer app's draft can't be read nothing is removed (a later download tries again),
    // unless the cache is past twice its limit.
    private suspend fun trimCache(justDownloaded: String) = trimMutex.withLock {
        var total = queries.selectTotalBytes().executeAsOne()
        if (total <= rules.cacheLimitBytes) return@withLock
        // Far over the limit (drafts or pins hold too much, or one can't be read): the cap wins over drafts, never over what plays.
        val overCeiling = total > rules.cacheLimitBytes * CEILING_FACTOR
        val protected = (if (overCeiling) pins.value.keys else protectedIds() ?: return@withLock) + justDownloaded
        for (row in queries.selectFilesOldestFirst().executeAsList()) {
            if (total <= rules.cacheLimitBytes) break
            if (row.sound_id in protected) continue
            deleteFileAndRow(row.sound_id)
            total -= row.size_bytes
        }
    }

    private suspend fun protectedIds(): Set<String>? {
        val usedByDrafts = inUse.ids()
        if (usedByDrafts == null) {
            logger.debug(TAG, "A draft can't be read, so no sound file is removed")
            return null
        }
        return usedByDrafts + pins.value.keys
    }

    private suspend fun deleteFileAndRow(soundId: String) {
        val path = queries.selectFile(soundId).executeAsOneOrNull()?.path
        // The row goes first: a crash in between leaves a stray file (overwritten by the next download), never a row without a file.
        queries.deleteFile(soundId)
        if (path != null) mediaFiles.delete(listOf(path))
    }

    // Disk and database work on the I/O dispatcher; a failure is logged and the call acts as if there was nothing to do.
    private suspend fun <T> guarded(what: String, block: suspend () -> T): T? = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "Could not $what", e)
            null
        }
    }
}
