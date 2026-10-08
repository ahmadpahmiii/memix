package app.memix.debug

import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.video.ExportSettings
import app.memix.core.domain.video.ExportedVideo
import app.memix.core.domain.video.VideoEngine
import kotlin.time.Duration
import kotlin.time.TimeSource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Debug-only hand check for P1-03, "a sample project with every track type exports correctly".
 * Exports [ExportCheckProject] and logs the output path, length, size and track count under [TAG],
 * so the file can be pulled and checked with ffprobe. Android starts it from MainActivity's
 * `memix.exportCheck` extra in debug builds only; the steps are in docs/TECHNICAL_DESIGN.md.
 */
class ExportCheck : KoinComponent {
    private val videoEngine: VideoEngine by inject()
    private val logger: Logger by inject()

    suspend fun run() {
        logger.debug(TAG, "export: starting ${ExportCheckProject.ID}, expected length ${seconds(ExportCheckProject.LENGTH_US)}")
        val started = TimeSource.Monotonic.markNow()
        var loggedQuarters = 0
        val outcome = videoEngine.export(ExportCheckProject.build(), ExportSettings()) { progress ->
            val quarters = (progress * QUARTERS).toInt()
            if (quarters > loggedQuarters) {
                loggedQuarters = quarters
                logger.debug(TAG, "export: ${quarters * PERCENT_PER_QUARTER}%")
            }
        }
        when (outcome) {
            is Outcome.Success -> logResult(outcome.value, started.elapsedNow())
            is Outcome.Failure -> logger.error(
                TAG,
                "export: failed with ${outcome.error}. NotFound means a test file is missing from files/${ExportCheckProject.MEDIA_DIR}.",
            )
        }
    }

    private fun logResult(video: ExportedVideo, elapsed: Duration) {
        val tracks = if (video.hasAudio) "2 (video, audio)" else "1 (video only)"
        logger.debug(
            TAG,
            "export: done in ${elapsed.inWholeMilliseconds} ms. path=${video.path} duration=${seconds(video.durationUs)} " +
                "size=${video.sizeBytes} bytes tracks=$tracks",
        )
    }

    // "8.000 s"; common code has no String.format.
    private fun seconds(us: Long): String {
        val millis = us / MICROS_PER_MILLI
        return "${millis / MILLIS_PER_SECOND}.${(millis % MILLIS_PER_SECOND).toString().padStart(3, '0')} s"
    }

    companion object {
        const val TAG = "MemixExportCheck"
        private const val QUARTERS = 4
        private const val PERCENT_PER_QUARTER = 25
        private const val MICROS_PER_MILLI = 1_000L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
