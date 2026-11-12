package app.memix.core.data.mysound

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.memix.core.data.db.MemixDatabase
import app.memix.core.domain.mysound.MySound
import app.memix.core.domain.mysound.MySoundSource
import app.memix.core.domain.mysound.MySoundsRepository
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** My sounds in the table of MySounds.sq (P3-07). The audio files are the callers' business. */
internal class SqlDelightMySounds(
    database: Lazy<MemixDatabase>,
    private val ioDispatcher: CoroutineDispatcher,
) : MySoundsRepository {
    // Opening the database file is disk I/O, so it waits for the first call, which runs on ioDispatcher.
    private val queries by lazy { database.value.mySoundsQueries }

    // The flow runs on ioDispatcher through flowOn, so mapToList needs no dispatcher of its own.
    override fun observeAll(): Flow<List<MySound>> =
        flow { emitAll(queries.selectAll(::toMySound).asFlow().mapToList(EmptyCoroutineContext)) }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)

    override suspend fun get(id: String): MySound? = withContext(ioDispatcher) {
        queries.selectById(id, ::toMySound).executeAsOneOrNull()
    }

    override suspend fun add(sound: MySound) {
        withContext(ioDispatcher) {
            queries.insert(sound.id, sound.name, sound.durationUs, sound.source.code, sound.path, sound.createdAtEpochUs)
        }
    }

    override suspend fun rename(id: String, name: String): Boolean = withContext(ioDispatcher) {
        queries.transactionWithResult {
            val exists = queries.selectById(id).executeAsOneOrNull() != null
            if (exists) queries.rename(name, id)
            exists
        }
    }

    override suspend fun remove(id: String): MySound? = withContext(ioDispatcher) {
        queries.transactionWithResult {
            val sound = queries.selectById(id, ::toMySound).executeAsOneOrNull()
            if (sound != null) queries.delete(id)
            sound
        }
    }

    override suspend fun putBack(sound: MySound) = add(sound)

    override suspend fun removeByPaths(paths: Set<String>) {
        withContext(ioDispatcher) { queries.transaction { paths.forEach { queries.deleteByPath(it) } } }
    }

    override suspend fun allPaths(): Set<String> = withContext(ioDispatcher) { queries.selectPaths().executeAsList().toSet() }

    private fun toMySound(id: String, name: String, durationUs: Long, source: String, path: String, createdAtEpochUs: Long) =
        MySound(id, name, durationUs, MySoundSource.ofCode(source), path, createdAtEpochUs)
}
