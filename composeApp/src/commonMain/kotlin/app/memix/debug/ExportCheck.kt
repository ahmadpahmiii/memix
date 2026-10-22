package app.memix.debug

import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.ProjectRepository
import app.memix.core.domain.project.videoLengthUs
import app.memix.core.domain.video.ExportOutcome
import app.memix.core.domain.video.ExportSettings
import app.memix.core.domain.video.ExportedVideo
import app.memix.core.domain.video.VideoEngine
import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Debug-only export hand checks, logged under [TAG] so the file can be pulled and checked with ffprobe:
 * - [run] (P1-03, "a sample project with every track type exports correctly") exports [ExportCheckProject]; MainActivity's
 *   `memix.exportCheck` extra starts it.
 * - [runLatestDraft] (P1-08 export sync) exports the draft edited last, after logging where each of its sounds starts;
 *   the `memix.exportLatestDraft` extra starts it, and `memix.exportShortSide` (P1-10: 720 for 720p) lowers its
 *   resolution.
 * Both take `memix.exportWatermarkFree` (P1-13) to leave the "Memix" mark out, which every export carries otherwise.
 * The file goes to `cache/exports/` (never the gallery), where the owner steps pull it. A test file that isn't pushed
 * exports black, logged by the engine (MemixVideoEngine: "Exporting … missing clip(s) as black"; test files go in
 * `files/debug-media/`). Debug builds only; the steps are in docs/qa/phase-1/engineer-hand-checks.md.
 */
class ExportCheck : KoinComponent {
    private val videoEngine: VideoEngine by inject()
    private val repository: ProjectRepository by inject()
    private val logger: Logger by inject()

    suspend fun run(watermark: Boolean = true) {
        logger.debug(TAG, "export: starting ${ExportCheckProject.ID}, expected length ${seconds(ExportCheckProject.LENGTH_US)}")
        export(ExportCheckProject.build(), ExportSettings(watermark = watermark))
    }

    /**
     * Exports the draft edited last, with one line per meme sound and audio clip: where it starts and what of it plays.
     * [shortSidePx] exports at a lower resolution (720 for 720p); null at the canvas size. [watermark] false marks the
     * export watermark-free, so a frame can be compared with the preview (`memix.exportWatermarkFree`, P1-13).
     */
    suspend fun runLatestDraft(shortSidePx: Int? = null, watermark: Boolean = true) {
        val latest = repository.observeSummaries().first().firstOrNull() ?: return logger.error(TAG, "exportLatestDraft: there are no drafts")
        val project = when (val loaded = repository.get(latest.id)) {
            is Outcome.Success -> loaded.value
            is Outcome.Failure -> return logger.error(TAG, "exportLatestDraft: loading ${latest.id} failed with ${loaded.error}")
        }
        val lengthUs = project.videoLengthUs()
        logger.debug(TAG, "exportLatestDraft: ${project.id}, length ${seconds(lengthUs)}")
        project.video?.tracks.orEmpty()
            .filter { it.kind == TrackKind.MEME_SOUND || it.kind == TrackKind.AUDIO }
            .flatMap { track -> track.items.filterIsInstance<AudioClip>().map { track.kind to it } }
            .sortedBy { (_, clip) -> clip.startUs }
            .forEach { (kind, clip) ->
                val pastEnd = if (clip.startUs + clip.durationUs > lengthUs) ", runs past the end (silent there)" else ""
                logger.debug(
                    TAG,
                    "sound: ${kind.name.lowercase()} ${clip.id} starts at ${seconds(clip.startUs)}, from ${seconds(clip.trimInUs)} of its file, " +
                        "${seconds(clip.durationUs)} long, volume ${(clip.volume * PERCENT).roundToInt()}%$pastEnd",
                )
            }
        export(project, ExportSettings(shortSidePx = shortSidePx, watermark = watermark))
    }

    private suspend fun export(project: Project, settings: ExportSettings = ExportSettings()) {
        val started = TimeSource.Monotonic.markNow()
        var loggedQuarters = 0
        logger.debug(TAG, "export: ${settings.shortSidePx?.let { "short side $it px" } ?: "canvas size"}, watermark ${if (settings.watermark) "on" else "off"}")
        val outcome = videoEngine.export(project, settings) { progress ->
            val quarters = (progress * QUARTERS).toInt()
            if (quarters > loggedQuarters) {
                loggedQuarters = quarters
                logger.debug(TAG, "export: ${quarters * PERCENT_PER_QUARTER}%")
            }
        }
        when (outcome) {
            is ExportOutcome.Done -> logResult(outcome.video, started.elapsedNow())
            is ExportOutcome.Failed -> logger.error(TAG, "export: failed with ${outcome.error}")
        }
    }

    private fun logResult(video: ExportedVideo, elapsed: Duration) {
        val tracks = if (video.hasAudio) "2 (video, audio)" else "1 (video only)"
        logger.debug(
            TAG,
            "export: done in ${elapsed.inWholeMilliseconds} ms. path=${video.location} duration=${seconds(video.durationUs)} " +
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
        private const val PERCENT = 100
        private const val PERCENT_PER_QUARTER = 25
        private const val MICROS_PER_MILLI = 1_000L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
