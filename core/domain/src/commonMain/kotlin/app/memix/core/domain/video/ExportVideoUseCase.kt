package app.memix.core.domain.video

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.project.videoLengthUs
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one export that runs at a time (spec docs/ux/specs/P1-12-export.md): starts it from Export video, keeps it
 * alive while the user leaves the app ([ExportForeground]), reports its progress, and ends it as Done, Failed or
 * Cancelled, with the analytics events. It outlives every screen: the Exporting screen, the editor and the
 * notification all read [state]. One instance per app.
 *
 * Main thread only for every call; the export itself runs on the engine's own thread. The draft is a copy taken at
 * the tap, so the file always matches what the user saw.
 */
class ExportVideoUseCase(
    private val engine: VideoEngine,
    private val foreground: ExportForeground,
    private val preferences: ExportPreferences,
    private val analytics: Analytics,
    private val appScope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
) {
    private val mutableState = MutableStateFlow<ExportJobState>(ExportJobState.Idle)

    /** The export now, or how the last one ended until [dismiss]. */
    val state: StateFlow<ExportJobState> = mutableState.asStateFlow()

    // Written by the engine's export thread, read once every frame-ish by the main thread.
    private val engineProgress = MutableStateFlow(0f)
    private var running: Job? = null

    /**
     * Starts exporting [project] at [choice] into the gallery, from the user's tap. False when an export is already
     * running, or Android refused to keep it alive (then [state] says so).
     */
    fun start(project: Project, choice: ExportChoice): Boolean {
        if (state.value is ExportJobState.Running) return false
        val job = ExportJob(project, choice)
        if (!foreground.start(controls)) {
            fail(job, ExportError.SYSTEM_STOPPED)
            return false
        }
        val lengthSeconds = (project.videoLengthUs() / MICROS_PER_SECOND.toDouble()).roundToLong()
        analytics.log(AnalyticsEvent.ExportStart(project.type, choice.resolution.label, choice.frameRate, lengthSeconds))
        engineProgress.value = 0f
        mutableState.value = ExportJobState.Running(job, percent = 0, secondsLeft = null)
        // Remembered app-wide for the next export (spec → Kept).
        appScope.launch(mainDispatcher) { preferences.saveLastChoice(choice) }
        running = appScope.launch(mainDispatcher) { run(job) }
        return true
    }

    /** Cancel, Stop in "Stop exporting?", or the notification's Cancel: stops at once and deletes the partial file. */
    fun cancel() {
        val now = state.value as? ExportJobState.Running ?: return
        endRunning()
        analytics.log(AnalyticsEvent.ExportCancel(now.job.project.type, now.percent))
        foreground.finish(notice = null)
        mutableState.value = ExportJobState.Cancelled(now.job)
    }

    /** The screen showed how the export ended: back to idle. */
    fun dismiss() {
        if (state.value !is ExportJobState.Running) mutableState.value = ExportJobState.Idle
    }

    private val controls = object : ExportControls {
        override fun cancel() = this@ExportVideoUseCase.cancel()

        override fun stoppedBySystem() {
            val now = state.value as? ExportJobState.Running ?: return
            endRunning()
            fail(now.job, ExportError.SYSTEM_STOPPED, ExportNotice.StoppedBySystem)
        }
    }

    private suspend fun run(job: ExportJob) {
        val started = TimeSource.Monotonic.markNow()
        preferences.saveUnfinishedExport(UnfinishedExport(job.project.id, percent = 0))
        val canvas = PixelSize(job.project.canvas.widthPx, job.project.canvas.heightPx)
        // Waits here, at 0%, if the sheet's capability check hadn't finished (spec → Not ready yet).
        val option = engine.exportCapabilities(canvas).optionFor(job.choice)
        if (option == null) return fail(job, ExportError.ENCODER)
        val reporter = appScope.launch(mainDispatcher) { reportProgress(job, started) }
        val outcome = try {
            engine.export(job.project, settingsOf(job, option)) { engineProgress.value = it }
        } catch (e: CancellationException) {
            // cancel() or Android's stop ended it and already said how.
            throw e
        } finally {
            reporter.cancel()
        }
        running = null
        when (outcome) {
            is ExportOutcome.Done -> finishDone(job, option, outcome.video, started)
            is ExportOutcome.Failed -> fail(job, outcome.error)
        }
    }

    private suspend fun finishDone(job: ExportJob, option: ExportOption, video: ExportedVideo, started: TimeMark) {
        val tookMs = started.elapsedNow().inWholeMilliseconds
        analytics.log(AnalyticsEvent.ExportComplete(job.project.type, tookMs, video.sizeBytes / BYTES_PER_MB))
        measuredSecondsPerMegapixelSecond(tookMs, option, video.durationUs)?.let {
            preferences.saveSecondsPerMegapixelSecond(speedGroupOf(job.choice.resolution), it)
        }
        preferences.saveUnfinishedExport(null)
        foreground.finish(ExportNotice.Saved(lengthLabelOf(video.durationUs), job.choice.resolution.label))
        mutableState.value = ExportJobState.Done(job, video, option)
    }

    private fun fail(job: ExportJob, error: ExportError, notice: ExportNotice = ExportNotice.Failed) {
        analytics.log(AnalyticsEvent.ExportFail(job.project.type, error.eventValue))
        appScope.launch(mainDispatcher) { preferences.saveUnfinishedExport(null) }
        foreground.finish(notice)
        mutableState.value = ExportJobState.Failed(job, error)
    }

    private fun endRunning() {
        running?.cancel()
        running = null
        appScope.launch(mainDispatcher) { preferences.saveUnfinishedExport(null) }
    }

    // The screen's progress about ten times a second; the notification and the saved percent once a second.
    private suspend fun reportProgress(job: ExportJob, started: TimeMark) {
        var lastSecond = -1L
        while (true) {
            delay(PROGRESS_EVERY_MS)
            val now = state.value as? ExportJobState.Running ?: return
            val progress = engineProgress.value
            val percent = (progress * PERCENT).roundToInt().coerceIn(0, PERCENT)
            val secondsLeft = secondsLeftOf(progress, started)
            if (percent != now.percent || secondsLeft != now.secondsLeft) {
                mutableState.value = now.copy(percent = percent, secondsLeft = secondsLeft)
            }
            val second = started.elapsedNow().inWholeSeconds
            if (second != lastSecond) {
                lastSecond = second
                foreground.showProgress(percent, secondsLeft)
                preferences.saveUnfinishedExport(UnfinishedExport(job.project.id, percent))
            }
        }
    }

    private fun settingsOf(job: ExportJob, option: ExportOption) = ExportSettings(
        frameRate = job.choice.frameRate,
        frameSize = option.frameSize,
        codec = option.codec,
        videoBitrateBps = option.videoBitrateBps,
        destination = ExportDestination.GALLERY,
    )

    private companion object {
        const val PROGRESS_EVERY_MS = 100L
        const val PERCENT = 100
        const val MICROS_PER_SECOND = 1_000_000L
        const val BYTES_PER_MB = 1_000_000.0
    }
}

/** What one export was asked to make: the draft as it was at the tap, at a resolution and frame rate. */
data class ExportJob(val project: Project, val choice: ExportChoice)

/** Where the export stands (spec P1-12 → States). */
sealed interface ExportJobState {
    data object Idle : ExportJobState

    /** [percent] 0–100; [secondsLeft] once it can be told (after 2 s), else null. */
    data class Running(val job: ExportJob, val percent: Int, val secondsLeft: Long?) : ExportJobState

    data class Done(val job: ExportJob, val video: ExportedVideo, val option: ExportOption) : ExportJobState

    data class Failed(val job: ExportJob, val error: ExportError) : ExportJobState

    data class Cancelled(val job: ExportJob) : ExportJobState
}

/**
 * The time left at [progress] (0–1) of an export that started at [started], from its pace so far; null in the first
 * two seconds or before any progress, when a guess would jump around.
 */
internal fun secondsLeftOf(progress: Float, started: TimeMark): Long? {
    val elapsed = started.elapsedNow().inWholeMilliseconds
    if (elapsed < TIME_LEFT_AFTER_MS || progress < MIN_PROGRESS_FOR_TIME) return null
    return ((elapsed * (1 - progress) / progress) / MILLIS_PER_SECOND).roundToLong()
}

/** A video length as "0:08" or "12:34": minutes and seconds, digits and a colon in every language. */
fun lengthLabelOf(durationUs: Long): String {
    val totalSeconds = (durationUs + HALF_SECOND_US) / MICROS_PER_SECOND_LONG
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "${totalSeconds / SECONDS_PER_MINUTE}:${seconds.toString().padStart(2, '0')}"
}

private const val TIME_LEFT_AFTER_MS = 2_000L
private const val MIN_PROGRESS_FOR_TIME = 0.01f
private const val MILLIS_PER_SECOND = 1_000.0
private const val MICROS_PER_SECOND_LONG = 1_000_000L
private const val HALF_SECOND_US = 500_000L
private const val SECONDS_PER_MINUTE = 60
