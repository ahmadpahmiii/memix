package app.memix.core.domain.sound

import app.memix.core.model.catalog.LibrarySound
import kotlinx.coroutines.flow.Flow

/** The most the downloaded sounds may take on the phone before the least recently used ones are removed (P3-04). */
const val SOUND_CACHE_LIMIT_BYTES: Long = 500L * 1024 * 1024

/** Where a sound's download is. A flow of these ends with exactly one [Done] or [Failed]. */
sealed interface DownloadState {
    /** [fraction] is 0 to 1, or null while the server has not said how big the file is. */
    data class Progress(val fraction: Float?) : DownloadState

    /** The file is complete and in place at [path] (relative to the app's files directory). */
    data class Done(val path: String) : DownloadState

    data class Failed(val reason: DownloadFailure) : DownloadState
}

enum class DownloadFailure {
    /** No connection, a timeout, a server error, or no bytes for the stall time. A new try may work. */
    NETWORK,

    /** The phone has no room for the file. */
    STORAGE_FULL,

    /** The catalog has no address for it, the server says it is gone, the file is not sound-sized, or it can't be saved. */
    UNAVAILABLE,
}

/**
 * The sounds downloaded from the library and kept on the phone (P3-04). Implemented in :core:data. The bundled starter
 * pack is not here: it ships inside the app (`SoundLibrary`).
 *
 * A downloaded sound is one shared file, `sounds/<catalog id>.<extension>`. Projects point at it (they keep no copy of
 * their own), so removing a file a draft uses would break that draft: the cleanup never removes a file a saved draft
 * uses ([SoundsInUse]) or one that is [pin]ned. Every suspend function runs its disk work off the main thread.
 */
interface SoundDownloads {
    /** The ids of the downloaded sounds. Emits again after every download and removal. */
    fun observeDownloadedIds(): Flow<Set<String>>

    /**
     * The downloaded file of [soundId], or null when there is none (never downloaded, removed, or the file went
     * missing). Never touches the network, so it works offline. Counts as a use for the cleanup order.
     */
    suspend fun downloadedFile(soundId: String): String?

    /**
     * Downloads [sound], or joins the download already running for it: one download per sound, whoever asks. The flow
     * ends with [DownloadState.Done] or [DownloadState.Failed]. A file is written under a temporary name and renamed
     * when complete, so a partial file is never used. Network failures are retried twice with a growing wait before
     * [DownloadState.Failed]; a stalled transfer counts as a network failure. Stops when the last collector is gone.
     * After a success the cache is trimmed to [SOUND_CACHE_LIMIT_BYTES].
     */
    fun download(sound: LibrarySound): Flow<DownloadState>

    /** Counts a play or a landing as a use: the sound moves to the back of the cleanup order. */
    suspend fun markUsed(soundId: String)

    /** Keeps the sound's file out of the cleanup while it plays or is being added. Calls nest. Safe from any thread. */
    fun pin(soundId: String)

    /** Undoes one [pin]. */
    fun unpin(soundId: String)

    /** Deletes the downloaded file of [soundId] (a file that would not play) unless a saved draft uses it. A missing one is fine. */
    suspend fun discard(soundId: String)

    /** Deletes the downloaded files of these sounds (removed from the catalog) unless a draft uses them or they are pinned. */
    suspend fun discardUnused(soundIds: Set<String>)
}

/** The sounds that saved drafts use, which the cleanup must not delete. */
fun interface SoundsInUse {
    /** Catalog ids used by saved drafts, or null when that can't be known (a draft saved by a newer app). */
    suspend fun ids(): Set<String>?
}

/** Where sound files live, relative to the app's files directory. Shared by the starter pack and the downloads. */
object SoundPaths {
    const val FOLDER = "sounds"
    const val DEFAULT_EXTENSION = "m4a"

    private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,64}")
    private val SAFE_EXTENSION = Regex("[a-z0-9]{1,8}")

    /** True when [soundId] can be part of a file name; catalog ids are UUIDs. */
    fun isSafeId(soundId: String): Boolean = SAFE_ID.matches(soundId)

    /** `sounds/<id>.<extension>`, with the extension taken from the catalog key (`sounds/2026/11/<id>.m4a`). */
    fun fileOf(soundId: String, catalogKey: String): String {
        val extension = catalogKey.substringAfterLast('.', DEFAULT_EXTENSION).lowercase().takeIf { SAFE_EXTENSION.matches(it) }
        return "$FOLDER/$soundId.${extension ?: DEFAULT_EXTENSION}"
    }

    /** The catalog id in a sound file path, or null for any other path. */
    fun soundIdOf(path: String): String? {
        if (!path.startsWith("$FOLDER/")) return null
        return path.removePrefix("$FOLDER/").substringBeforeLast('.').takeIf { it.isNotEmpty() && '/' !in it }
    }
}
