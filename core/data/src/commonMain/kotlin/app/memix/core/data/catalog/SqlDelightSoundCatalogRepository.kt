package app.memix.core.data.catalog

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.memix.core.data.db.MemixDatabase
import app.memix.core.data.db.SelectFeedPages
import app.memix.core.data.nowEpochUs
import app.memix.core.domain.AppConfig
import app.memix.core.domain.AppError
import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.catalog.SOUND_PAGE_SIZE
import app.memix.core.domain.catalog.SoundCatalogRepository
import app.memix.core.domain.media.mediaUrl
import app.memix.core.model.catalog.LibrarySound
import app.memix.core.model.catalog.SoundCategory
import app.memix.core.model.catalog.SoundFeed
import app.memix.core.model.catalog.SoundFeedContent
import app.memix.core.model.catalog.Waveform
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val TAG = "SoundCatalog"
private const val CATEGORIES_STAMP = "sound_categories"
private const val REMOVED_CHECK_STAMP = "sound_removed_check"
private const val MICROS_PER_MILLI = 1_000L
private const val MAX_TRENDING_SOUNDS = 50

// SQLite allows 999 variables in one statement; the pattern takes one of them.
private const val SAVED_IDS_PER_QUERY = 500

// One request asks for this many ids; the ids travel in the URL, so a favorites list stays under its length limit.
private const val STILL_LISTED_PER_REQUEST = 50

/**
 * The sound catalog in the SQLDelight tables of Catalog.sq, filled from [remote]. Reads come only from the tables;
 * the network is touched by the refresh calls and search, and a failed call leaves the tables as they were.
 */
internal class SqlDelightSoundCatalogRepository(
    database: Lazy<MemixDatabase>,
    private val remote: SoundCatalogRemote,
    private val appConfig: AppConfig,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : SoundCatalogRepository {
    // Opening the database file is disk I/O, so it waits for the first call, which runs on ioDispatcher.
    private val db by lazy { database.value }
    private val queries by lazy { db.catalogQueries }

    override fun observeCategories(): Flow<List<SoundCategory>> =
        flow {
            emitAll(
                queries.selectCategories { id, nameJson, sort -> SoundCategory(id, localizedTextOf(nameJson), sort.toInt()) }
                    .asFlow()
                    .mapToList(ioDispatcher),
            )
        }.flowOn(ioDispatcher)

    override suspend fun refreshCategories(force: Boolean): Outcome<Unit> = runOnIo("refresh categories") {
        if (!force && isFresh(queries.selectStamp(CATEGORIES_STAMP).executeAsOneOrNull(), CatalogRefreshRules.categories)) {
            return@runOnIo Outcome.Success(Unit)
        }
        val categories = remote.categories()
        // An empty answer over saved categories is more likely a hiccup than an emptied catalog: keep the old rows and
        // leave the stamp, so the next open asks again.
        if (categories.isEmpty() && queries.selectCategories { _, _, _ -> 1 }.executeAsList().isNotEmpty()) {
            logger.error(TAG, "The catalog answered with no categories; keeping the saved ones")
            return@runOnIo Outcome.Success(Unit)
        }
        db.transaction {
            queries.deleteCategories()
            categories.forEach { queries.insertCategory(it.id, it.name.toJson(), it.sort.toLong()) }
            queries.insertStamp(CATEGORIES_STAMP, clock.nowEpochUs())
        }
        Outcome.Success(Unit)
    }

    override fun observeFeed(feed: SoundFeed): Flow<SoundFeedContent> {
        val key = feed.cacheKey()
        return flow {
            // Wakeups: every write to the sound or feed-entry tables wakes this query, including a search that saves rows
            // or another feed's page load. That is cheap (a join of at most a few hundred rows on the IO dispatcher), and
            // distinctUntilChanged below stops an unchanged list from reaching the screen.
            // The sounds query is the trigger; the pages are read after it fires, from the same committed state, because
            // a page load writes both in one transaction.
            emitAll(
                queries.selectFeedSounds(key, ::toLibrarySound).asFlow().mapToList(ioDispatcher)
                    .map { sounds -> contentOf(feed, sounds, queries.selectFeedPages(key).executeAsList()) },
            )
        }.distinctUntilChanged().flowOn(ioDispatcher)
    }

    override suspend fun loadFeedPage(feed: SoundFeed, pageIndex: Int, force: Boolean): Outcome<Unit> = runOnIo("load feed page") {
        val key = feed.cacheKey()
        val pages = queries.selectFeedPages(key).executeAsList()
        when {
            pageIndex < 0 || pageIndex > pages.size -> Outcome.Failure(AppError.Unexpected(IllegalArgumentException("No page $pageIndex yet")))
            feed.isSinglePage && pageIndex > 0 -> Outcome.Success(Unit)
            !force && pages.getOrNull(pageIndex)?.let { isFresh(it.fetched_at_epoch_us, feed.refreshTime()) } == true -> Outcome.Success(Unit)
            else -> {
                val sounds = fetchFeedPage(feed, pageIndex)
                saveFeedPage(key, pageIndex, sounds, isLast = feed.isSinglePage || sounds.size < SOUND_PAGE_SIZE)
                Outcome.Success(Unit)
            }
        }
    }

    override suspend fun search(query: String, regionCode: String?, pageIndex: Int): Outcome<List<LibrarySound>> = runOnIo("search") {
        val found = remote.search(query, regionCode?.uppercase() ?: GLOBAL_REGION_TAG, pageIndex * SOUND_PAGE_SIZE, SOUND_PAGE_SIZE)
        // The rows are kept so a result can open its details and be found offline once its audio is saved.
        db.transaction { found.forEach(::insertSound) }
        Outcome.Success(found.map { it.sound })
    }

    override suspend fun searchSaved(query: String, savedSoundIds: Set<String>): List<LibrarySound> = withContext(ioDispatcher) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty() || savedSoundIds.isEmpty()) return@withContext emptyList()
        val pattern = containsPattern(needle)
        try {
            savedSoundIds.chunked(SAVED_IDS_PER_QUERY)
                .flatMap { ids -> queries.selectSavedSoundsMatching(ids, pattern, ::toLibrarySound).executeAsList() }
                .sortedWith(compareBy({ titleMatchPosition(it, needle) }, { it.title.inLanguage("en") }, { it.id }))
                .take(SOUND_PAGE_SIZE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "Saved search failed", e)
            emptyList()
        }
    }

    // Never throws: a sound without a readable waveform shows the rail.
    override suspend fun waveformOf(sound: LibrarySound): Waveform? = withContext(ioDispatcher) {
        val path = sound.waveformPath ?: return@withContext null
        try {
            savedWaveform(sound.id, path) ?: fetchWaveform(sound.id, path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.debug(TAG, "No waveform for ${sound.id}: ${e.message}")
            null
        }
    }

    override fun observeRemovedSoundIds(): Flow<Set<String>> =
        flow {
            emitAll(db.soundFilesQueries.selectRemovedIds().asFlow().mapToList(ioDispatcher).map { it.toSet() })
        }.distinctUntilChanged().flowOn(ioDispatcher)

    override suspend fun checkStillListed(soundIds: Set<String>, force: Boolean): Outcome<Set<String>> = runOnIo("check removed sounds") {
        fun savedRemoved(): Set<String> = db.soundFilesQueries.selectRemovedIds().executeAsList().filterTo(HashSet()) { it in soundIds }
        if (!force && isFresh(queries.selectStamp(REMOVED_CHECK_STAMP).executeAsOneOrNull(), CatalogRefreshRules.removedCheck)) {
            return@runOnIo Outcome.Success(savedRemoved())
        }
        // Fetch every answer first: a failure half-way leaves the saved answers as they were.
        val answers = soundIds.chunked(STILL_LISTED_PER_REQUEST).map { chunk ->
            val listed = remote.soundsByIds(chunk)
            listed to (chunk.toSet() - listed.mapTo(HashSet()) { it.sound.id })
        }
        // None of several listed is more likely a hiccup than a catalog emptied of every favorite: mark nothing and ask
        // again next time (as refreshCategories does for an empty answer).
        if (soundIds.size > 1 && answers.all { it.first.isEmpty() }) {
            logger.error(TAG, "The catalog listed none of ${soundIds.size} saved sounds; not marking them removed")
            return@runOnIo Outcome.Success(savedRemoved())
        }
        val removed = HashSet<String>()
        db.transaction {
            answers.forEach { (listed, missing) ->
                listed.forEach { found ->
                    insertSound(found)
                    db.soundFilesQueries.deleteRemoved(found.sound.id)
                }
                missing.forEach { id -> db.soundFilesQueries.insertRemoved(id, clock.nowEpochUs()) }
                removed += missing
            }
            queries.insertStamp(REMOVED_CHECK_STAMP, clock.nowEpochUs())
        }
        Outcome.Success(removed)
    }

    private fun savedWaveform(soundId: String, path: String): Waveform? {
        val saved = queries.selectWaveform(soundId).executeAsOneOrNull() ?: return null
        return if (saved.waveform_path == path) decodeWaveform(saved.waveform_json) else null
    }

    private suspend fun fetchWaveform(soundId: String, path: String): Waveform? {
        val url = mediaUrl(appConfig.mediaBaseUrl, path) ?: return null
        val text = remote.fetchText(url) ?: return null
        val waveform = decodeWaveform(text)
        if (waveform == null) {
            logger.debug(TAG, "Waveform of $soundId is not readable; the rail is shown")
            return null
        }
        queries.insertWaveform(soundId, path, text)
        return waveform
    }

    private suspend fun fetchFeedPage(feed: SoundFeed, pageIndex: Int): List<RemoteSound> = when (feed) {
        is SoundFeed.Trending -> fetchTrending(feed)
        is SoundFeed.Local -> remote.sounds(feed.regionCode.uppercase(), feed.categoryId, pageIndex * SOUND_PAGE_SIZE, SOUND_PAGE_SIZE)
        is SoundFeed.Global -> remote.sounds(GLOBAL_REGION_TAG, feed.categoryId, pageIndex * SOUND_PAGE_SIZE, SOUND_PAGE_SIZE)
    }

    // The region's ranks first, then the global ones, each by rank, without repeats (Sounds spec, Tab contents).
    private suspend fun fetchTrending(feed: SoundFeed.Trending): List<RemoteSound> {
        val regionTags = listOfNotNull(feed.regionCode?.uppercase(), GLOBAL_REGION_TAG).distinct()
        val ranks = remote.trendingRanks(clock.now().trendingMonth(), regionTags)
        val orderedIds = ranks
            .sortedWith(compareBy({ regionTags.indexOf(it.regionTag) }, { it.rank }))
            .map { it.soundId }
            .distinct()
            .take(MAX_TRENDING_SOUNDS)
        val byId = remote.soundsByIds(orderedIds).associateBy { it.sound.id }
        return orderedIds.mapNotNull { byId[it] }
    }

    // Loading page N replaces everything from N on, in one transaction, so a reader never sees half a refresh.
    private fun saveFeedPage(key: String, pageIndex: Int, sounds: List<RemoteSound>, isLast: Boolean) {
        val firstPosition = pageIndex * SOUND_PAGE_SIZE
        db.transaction {
            sounds.forEach(::insertSound)
            queries.deleteFeedEntriesFrom(key, firstPosition.toLong())
            queries.deleteFeedPagesFrom(key, pageIndex.toLong())
            sounds.forEachIndexed { index, sound -> queries.insertFeedEntry(key, (firstPosition + index).toLong(), sound.sound.id) }
            queries.insertFeedPage(key, pageIndex.toLong(), clock.nowEpochUs(), if (isLast) 1L else 0L)
        }
    }

    private fun insertSound(remoteSound: RemoteSound) {
        val sound = remoteSound.sound
        queries.insertSound(
            id = sound.id,
            titleJson = sound.title.toJson(),
            categoryId = sound.categoryId,
            filePath = sound.filePath,
            durationMs = sound.durationUs / MICROS_PER_MILLI,
            waveformPath = sound.waveformPath,
            licenseType = sound.licenseType,
            sourceUrl = sound.sourceUrl,
            credit = sound.credit,
            searchText = searchTextOf(remoteSound),
        )
    }

    private fun contentOf(feed: SoundFeed, sounds: List<LibrarySound>, pages: List<SelectFeedPages>): SoundFeedContent {
        // Trending is filtered here, from its one saved list, so the chips work offline and need no request.
        val shown = if (feed is SoundFeed.Trending && feed.categoryId != null) sounds.filter { it.categoryId == feed.categoryId } else sounds
        return SoundFeedContent(sounds = shown, loadedPages = pages.size, endReached = pages.lastOrNull()?.is_last == 1L)
    }

    private fun SoundFeed.refreshTime(): Duration = if (this is SoundFeed.Trending) CatalogRefreshRules.trending else CatalogRefreshRules.soundPage

    private fun isFresh(fetchedAtEpochUs: Long?, maxAge: Duration): Boolean {
        if (fetchedAtEpochUs == null) return false
        val ageUs = clock.nowEpochUs() - fetchedAtEpochUs
        // A negative age means the clock moved back; refresh rather than trust a copy of unknown age.
        return ageUs in 0 until maxAge.inWholeMicroseconds
    }

    private fun titleMatchPosition(sound: LibrarySound, needle: String): Int =
        sound.title.byLanguage.values.map { it.lowercase().indexOf(needle) }.filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE

    private suspend fun <T> runOnIo(what: String, block: suspend () -> Outcome<T>): Outcome<T> = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CatalogUnreachableException) {
            logger.debug(TAG, "$what: catalog unreachable (${e.message})")
            Outcome.Failure(AppError.Offline)
        } catch (e: Exception) {
            logger.error(TAG, "$what failed", e)
            Outcome.Failure(AppError.Unexpected(e))
        }
    }
}
