package app.memix.core.domain.catalog

import app.memix.core.domain.Outcome
import app.memix.core.model.catalog.LibrarySound
import app.memix.core.model.catalog.SoundCategory
import app.memix.core.model.catalog.SoundFeed
import app.memix.core.model.catalog.SoundFeedContent
import app.memix.core.model.catalog.Waveform
import kotlinx.coroutines.flow.Flow

/** Sounds listed per page on every feed and in search results. */
const val SOUND_PAGE_SIZE = 30

/**
 * The online sound catalog with a copy on the phone, so the Sounds screen opens at once and still works offline.
 * Implemented in :core:data.
 *
 * Reading is separate from refreshing: `observe…` always answers from the phone's copy and never waits for the network;
 * the `load…`/`refresh…` calls go to the network when the copy is older than its refresh time (categories 24 h, pages
 * and trending 6 h) or when forced, and keep the copy untouched when they fail. Every function is safe to call from
 * the main thread.
 */
interface SoundCatalogRepository {
    /** The sound categories in catalog order; empty until the first refresh has ever succeeded. */
    fun observeCategories(): Flow<List<SoundCategory>>

    /**
     * Refreshes the categories when they are older than 24 hours, or always when [force] is true. Fails with
     * [app.memix.core.domain.AppError.Offline] when the catalog can't be reached.
     */
    suspend fun refreshCategories(force: Boolean): Outcome<Unit>

    /** The pages of [feed] saved so far, in feed order; emits again after each load. */
    fun observeFeed(feed: SoundFeed): Flow<SoundFeedContent>

    /**
     * Makes page [pageIndex] (0 is the first) of [feed] current. Does nothing when the saved page is under 6 hours old
     * and [force] is false. Loading page N replaces every saved page from N on, so refreshing page 0 restarts the feed
     * at its first page. Trending is one page; asking for a later page does nothing.
     */
    suspend fun loadFeedPage(feed: SoundFeed, pageIndex: Int, force: Boolean): Outcome<Unit>

    /**
     * Typo-tolerant search over the whole catalog, [SOUND_PAGE_SIZE] results from page [pageIndex], weighted toward
     * [regionCode] (null: global only). Never saved as a list. Fails with Offline when the catalog can't be reached.
     */
    suspend fun search(query: String, regionCode: String?, pageIndex: Int): Outcome<List<LibrarySound>>

    /**
     * Contains-match (not typo-tolerant, any language, tags too) over the sounds saved on the phone whose id is in
     * [savedSoundIds]: the offline search. At most [SOUND_PAGE_SIZE] results, best title match first.
     */
    suspend fun searchSaved(query: String, savedSoundIds: Set<String>): List<LibrarySound>

    /** The sound's waveform, or null when it has none, the file is missing or damaged, or it can't be fetched now. */
    suspend fun waveformOf(sound: LibrarySound): Waveform?
}
