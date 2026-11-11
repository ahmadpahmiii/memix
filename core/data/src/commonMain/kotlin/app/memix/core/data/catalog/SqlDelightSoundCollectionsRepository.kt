package app.memix.core.data.catalog

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.memix.core.data.db.MemixDatabase
import app.memix.core.data.nowEpochUs
import app.memix.core.domain.sound.RECENT_SOUNDS_STORED
import app.memix.core.domain.sound.SoundCollectionsRepository
import app.memix.core.model.catalog.LibrarySound
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Favorites and recents in the tables of SoundCollections.sq; the sound details are joined from `sound`. */
internal class SqlDelightSoundCollectionsRepository(
    database: Lazy<MemixDatabase>,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
) : SoundCollectionsRepository {
    // Opening the database file is disk I/O, so it waits for the first call, which runs on ioDispatcher.
    private val db by lazy { database.value }
    private val queries by lazy { db.soundCollectionsQueries }

    // The flows run on ioDispatcher through flowOn, so mapToList needs no dispatcher of its own.
    override fun observeFavoriteIds(): Flow<Set<String>> =
        flow { emitAll(queries.selectFavoriteIds().asFlow().mapToList(EmptyCoroutineContext)) }
            .map { it.toSet() }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)

    override fun observeFavorites(): Flow<List<LibrarySound>> =
        flow { emitAll(queries.selectFavoriteSounds(::toLibrarySound).asFlow().mapToList(EmptyCoroutineContext)) }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)

    override fun observeRecents(limit: Int): Flow<List<LibrarySound>> =
        flow { emitAll(queries.selectRecentSounds(limit.toLong(), ::toLibrarySound).asFlow().mapToList(EmptyCoroutineContext)) }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)

    override suspend fun toggleFavorite(soundId: String): Boolean = withContext(ioDispatcher) {
        // One transaction, so two quick taps can't both read "not a favorite" and leave the heart out of step.
        queries.transactionWithResult {
            val wasFavorite = queries.isFavorite(soundId).executeAsOne() > 0
            if (wasFavorite) queries.deleteFavorite(soundId) else queries.insertFavorite(soundId, clock.nowEpochUs())
            !wasFavorite
        }
    }

    override suspend fun recordRecent(soundId: String) = withContext(ioDispatcher) {
        // Ordered by the wall clock: a clock set back puts a new use below older ones until time catches up. Accepted.
        queries.transaction {
            queries.insertRecent(soundId, clock.nowEpochUs())
            queries.deleteRecentsBeyond(RECENT_SOUNDS_STORED.toLong())
        }
    }
}
