package app.memix.core.domain.mysound

import kotlinx.coroutines.flow.Flow

/**
 * The phone's list of My sounds (P3-07). Implemented in :core:data on SQLDelight; never synced. Every function runs its
 * disk work off the main thread. The audio files are not touched here: [MySoundPaths] and `MediaFiles` own those.
 */
interface MySoundsRepository {
    /** Every sound, newest first; emits again after each change. */
    fun observeAll(): Flow<List<MySound>>

    suspend fun get(id: String): MySound?

    suspend fun add(sound: MySound)

    /** Writes the name only. False when there is no such sound. */
    suspend fun rename(id: String, name: String): Boolean

    /** Removes the row (not the file) and returns it, or null when there is none. */
    suspend fun remove(id: String): MySound?

    /** Puts a removed row back exactly as it was, so it returns to its place in the list. */
    suspend fun putBack(sound: MySound)

    /** Removes the rows that point at these paths (their files are gone). */
    suspend fun removeByPaths(paths: Set<String>)

    /** Every file path a row points at, for the launch cleanup. */
    suspend fun allPaths(): Set<String>
}
