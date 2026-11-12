package app.memix.core.data.report

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.memix.core.data.db.MemixDatabase
import app.memix.core.data.nowEpochUs
import app.memix.core.domain.AppError
import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.sound.SoundReport
import app.memix.core.domain.sound.SoundReports
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val TAG = "SoundReports"

/** Sends reports through [remote] and remembers each reported sound in `sound_reported` (SoundReports.sq). */
internal class SqlDelightSoundReports(
    database: Lazy<MemixDatabase>,
    private val remote: SoundReportRemote,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : SoundReports {
    // Opening the database file is disk I/O, so it waits for the first call, which runs on ioDispatcher.
    private val queries by lazy { database.value.soundReportsQueries }

    override fun observeReportedIds(): Flow<Set<String>> =
        flow { emitAll(queries.selectReportedIds().asFlow().mapToList(EmptyCoroutineContext)) }
            .map { it.toSet() }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)

    override suspend fun submit(report: SoundReport): Outcome<Unit> {
        try {
            // On the IO dispatcher: the first call builds the Supabase client (reads the settings, creates an HTTP client).
            withContext(ioDispatcher) { remote.file(report.soundId, report.storedReason) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReportUnreachableException) {
            return Outcome.Failure(AppError.Offline)
        } catch (e: Exception) {
            // The class and status only: an error from the server or the serializer can quote the row, which holds the note.
            logger.error(TAG, "A report was not sent: ${e::class.simpleName}${(e as? ReportRefusedException)?.let { " ${it.statusCode}" }.orEmpty()}")
            return Outcome.Failure(AppError.Unexpected(ReportNotSentException()))
        }
        rememberReported(report.soundId)
        return Outcome.Success(Unit)
    }

    // The server has the report, so the note is written even if the screen leaves meanwhile. If the phone can't note it
    // (disk full), the sound just shows its Report row again.
    private suspend fun rememberReported(soundId: String) {
        try {
            withContext(ioDispatcher + NonCancellable) { queries.insertReported(soundId, clock.nowEpochUs()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "A sent report could not be noted on the phone: ${e::class.simpleName}")
        }
    }
}

/** What a failed send reports upward: no message, no cause, so nothing the person wrote can travel with it. */
internal class ReportNotSentException : Exception("The report was not sent")
