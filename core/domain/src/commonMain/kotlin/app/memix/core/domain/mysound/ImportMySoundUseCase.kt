package app.memix.core.domain.mysound

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.Logger
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaInspector
import app.memix.core.domain.project.nowEpochUs
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Brings one picked audio file, or one picked video's sound, into My sounds (spec P3-07): looks at the pick, refuses what
 * can't work (not sound, no sound track, over [MAX_MY_SOUND_DURATION_MINUTES] minutes) before any work, checks there is
 * room, converts to AAC `.m4a` in the app's own folder, checks the result plays and adds the row. Nothing is read by any
 * network code and no file name, path or length reaches [Analytics] or the logs.
 *
 * Logs `sound_import` once per outcome that ends the attempt: added, refused, or not enough space (a retry after freeing
 * space logs its own). A cancelled import logs nothing and leaves no file. Safe to call again with the same pick.
 */
class ImportMySoundUseCase(
    private val importer: SoundImporter,
    private val mediaFiles: MediaFiles,
    private val inspector: MediaInspector,
    private val repository: MySoundsRepository,
    private val leftoverCleaner: LeftoverMySoundsCleaner,
    private val analytics: Analytics,
    private val logger: Logger,
    private val clock: Clock,
) {
    suspend operator fun invoke(origin: MediaOrigin, source: MySoundSource, onProgress: (Float) -> Unit): MySoundImport {
        val probe = importer.probe(origin, source)
        val shownName = probe.displayName
        val refusal = probe.failure ?: lengthRefusal(probe)
        if (refusal != null) return refused(source, refusal, shownName)
        val space = spaceCheck(probe)
        if (space != null) {
            analytics.log(AnalyticsEvent.SoundImport(source, success = false, reason = MySoundFailure.STORAGE))
            return space
        }
        // The launch cleanup deletes files no row uses, which this import's is until it lands.
        leftoverCleaner.awaitDone()
        return convertAndAdd(origin, source, probe, onProgress)
    }

    private suspend fun convertAndAdd(origin: MediaOrigin, source: MySoundSource, probe: SoundProbe, onProgress: (Float) -> Unit): MySoundImport {
        val id = Uuid.random().toString()
        val path = MySoundPaths.fileOf(id)
        try {
            val converted = importer.convert(origin, path, onProgress)
            if (converted is SoundConversion.Failed) return refusedAfterWork(source, converted.failure, probe.displayName, path)
            val facts = inspector.inspect(path, MediaKind.AUDIO)
            val durationUs = facts?.durationUs
            if (durationUs == null || !facts.hasAudio) return refusedAfterWork(source, MySoundFailure.UNSUPPORTED, probe.displayName, path)
            // The metadata can understate a variable-bitrate file, so the length that counts is the converted one.
            if (durationUs > MAX_MY_SOUND_DURATION_US) return refusedAfterWork(source, MySoundFailure.TOO_LONG, probe.displayName, path)
            val sound = MySound(id, mySoundNameFromFile(probe.displayName), durationUs, source, path, clock.nowEpochUs())
            // A cancel that lands now must not leave a row without its file, or a file without its row.
            withContext(NonCancellable) {
                repository.add(sound)
                analytics.log(AnalyticsEvent.SoundImport(source, success = true, reason = null))
            }
            return MySoundImport.Added(sound)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { mediaFiles.delete(listOf(path)) }
            throw e
        } catch (e: Exception) {
            // The row couldn't be written. Class name only: a message can quote a path.
            logger.error(TAG, "Adding a My sound failed: ${e::class.simpleName}")
            return refusedAfterWork(source, MySoundFailure.UNREADABLE, probe.displayName, path)
        }
    }

    private fun lengthRefusal(probe: SoundProbe): MySoundFailure? {
        // A source that states no length is converted and measured on the result (see convertAndAdd).
        val durationUs = probe.durationUs ?: return null
        return when {
            durationUs <= 0 -> MySoundFailure.UNSUPPORTED
            durationUs > MAX_MY_SOUND_DURATION_US -> MySoundFailure.TOO_LONG
            else -> null
        }
    }

    // The converted file is about 16 KB per second of sound (128 kbps); a passed-through AAC can be larger, which the headroom covers.
    private suspend fun spaceCheck(probe: SoundProbe): MySoundImport.NotEnoughSpace? {
        val neededBytes = (probe.durationUs ?: 0L) / MICROS_PER_SECOND * BYTES_PER_SECOND + HEADROOM_BYTES
        if (mediaFiles.allocatableBytes() >= neededBytes && mediaFiles.reserveBytes(neededBytes)) return null
        return MySoundImport.NotEnoughSpace(neededBytes, mediaFiles.allocatableBytes())
    }

    private fun refused(source: MySoundSource, failure: MySoundFailure, displayName: String?): MySoundImport.Failed {
        analytics.log(AnalyticsEvent.SoundImport(source, success = false, reason = failure))
        return MySoundImport.Failed(failure, displayName)
    }

    private suspend fun refusedAfterWork(source: MySoundSource, failure: MySoundFailure, displayName: String?, path: String): MySoundImport.Failed {
        mediaFiles.delete(listOf(path))
        return refused(source, failure, displayName)
    }

    companion object {
        /** Kept free on top of the converted file. Smaller than a video's 100 MB: a sound is at most about 10 MB (spec P3-07 → Requests). */
        const val HEADROOM_BYTES = 25_000_000L
        private const val BYTES_PER_SECOND = 16_000L
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val TAG = "MySounds"
    }
}

/** How an import ended. */
sealed interface MySoundImport {
    data class Added(val sound: MySound) : MySoundImport

    /** [displayName] is the picked item's name for the result sheet; null when the provider gave none. */
    data class Failed(val failure: MySoundFailure, val displayName: String?) : MySoundImport

    /** Sizes include the headroom. The caller waits for the user to free space, then imports again. */
    data class NotEnoughSpace(val neededBytes: Long, val freeBytes: Long) : MySoundImport
}
