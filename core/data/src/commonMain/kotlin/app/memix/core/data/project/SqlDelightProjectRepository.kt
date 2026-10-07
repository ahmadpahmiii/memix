package app.memix.core.data.project

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.memix.core.data.db.MemixDatabase
import app.memix.core.domain.AppError
import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.ProjectRepository
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectSummary
import app.memix.core.model.project.ProjectType
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Drafts in the SQLDelight `project` table, one JSON row per project (see Project.sq). */
internal class SqlDelightProjectRepository(
    database: Lazy<MemixDatabase>,
    private val codec: ProjectJsonCodec,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : ProjectRepository {
    // Opening the database file is disk I/O, so it waits for the first call, which runs on ioDispatcher.
    private val queries by lazy { database.value.projectQueries }

    override suspend fun save(project: Project): Outcome<Unit> = runOnIo("save") {
        queries.insertOrReplace(
            id = project.id,
            name = project.name,
            type = project.type.columnValue,
            schemaVersion = project.schemaVersion.toLong(),
            createdAtEpochUs = project.createdAtEpochUs,
            updatedAtEpochUs = project.updatedAtEpochUs,
            projectJson = codec.encode(project),
        )
        Outcome.Success(Unit)
    }

    override suspend fun get(id: String): Outcome<Project> = runOnIo("get") {
        val savedJson = queries.selectJsonById(id).executeAsOneOrNull()
        if (savedJson == null) Outcome.Failure(AppError.NotFound) else decode(id, savedJson)
    }

    override suspend fun delete(id: String): Outcome<Unit> = runOnIo("delete") {
        queries.deleteById(id)
        Outcome.Success(Unit)
    }

    override fun observeSummaries(): Flow<List<ProjectSummary>> =
        flow { emitAll(queries.selectSummariesByLastEdited(::toSummary).asFlow().mapToList(ioDispatcher)) }
            .flowOn(ioDispatcher)

    // Catches every exception: damaged JSON, a newer app's JSON or a faulty migration step all mean the
    // same to the user. Nothing in here suspends, so there is no cancellation to let through.
    private fun decode(id: String, savedJson: String): Outcome<Project> = try {
        Outcome.Success(codec.decode(savedJson))
    } catch (e: Exception) {
        // The message can quote the JSON, which holds gallery URIs, so only the exception type is logged.
        logger.error(TAG, "Project $id is unreadable: ${e::class.simpleName}")
        Outcome.Failure(AppError.ProjectUnreadable)
    }

    private suspend fun <T> runOnIo(operation: String, block: () -> Outcome<T>): Outcome<T> = try {
        withContext(ioDispatcher) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.error(TAG, "Project $operation failed", e)
        Outcome.Failure(if (e.isDiskFull()) AppError.StorageFull else AppError.Unexpected(e))
    }

    private fun toSummary(id: String, name: String, type: String, updatedAtEpochUs: Long, thumbnailPath: String?) =
        ProjectSummary(id, name, projectTypeOf(type), updatedAtEpochUs, thumbnailPath)

    private companion object {
        const val TAG = "ProjectRepository"
    }
}

// The type column uses the same names as the JSON, so renaming the Kotlin enum never breaks saved rows.
private val ProjectType.columnValue: String
    get() = when (this) {
        ProjectType.VIDEO -> "video"
        ProjectType.PHOTO -> "photo"
    }

private fun projectTypeOf(columnValue: String): ProjectType = ProjectType.entries.first { it.columnValue == columnValue }

// SQLite's own text for SQLITE_FULL, which both Android's SQLiteFullException and iOS's SQLiter messages carry.
private fun Exception.isDiskFull(): Boolean = message?.contains("database or disk is full") == true
