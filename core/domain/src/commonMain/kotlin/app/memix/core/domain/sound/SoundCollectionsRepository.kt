package app.memix.core.domain.sound

import app.memix.core.model.catalog.LibrarySound
import kotlinx.coroutines.flow.Flow

/** How many recently used sounds the phone remembers (spec P3-02 → Tab contents). */
const val RECENT_SOUNDS_STORED = 20

/** How many of them the Favorites tab shows; there is no "see all" in v1. */
const val RECENT_SOUNDS_SHOWN = 5

/**
 * What the user keeps of the sound library, on this phone only (PRD): the favorites and the recently used sounds.
 * Both are stored as sound ids and survive restarts and updates. Implemented in :core:data.
 *
 * `observe…` functions answer from the phone's copy at once and emit again after every change. Every function is safe
 * to call from the main thread.
 */
interface SoundCollectionsRepository {
    /** The ids of every favorite, including ones whose sound the phone has no details for. */
    fun observeFavoriteIds(): Flow<Set<String>>

    /** The favorites the phone has details for, last favorited first. */
    fun observeFavorites(): Flow<List<LibrarySound>>

    /** At most [limit] recently used sounds the phone has details for, last used first. */
    fun observeRecents(limit: Int): Flow<List<LibrarySound>>

    /** Adds the sound to the favorites, or removes it when it is one. Returns whether it is a favorite now. */
    suspend fun toggleFavorite(soundId: String): Boolean

    /**
     * Notes that the sound landed on a timeline: it moves to the top of the recents, and only the newest
     * [RECENT_SOUNDS_STORED] are kept.
     */
    suspend fun recordRecent(soundId: String)
}
