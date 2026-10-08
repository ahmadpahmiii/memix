package app.memix.debug

import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.GetProjectUseCase
import app.memix.core.domain.project.ObserveProjectSummariesUseCase
import app.memix.core.domain.project.ProjectEditSession
import app.memix.core.domain.project.SaveProjectUseCase
import app.memix.core.domain.project.StartEditSessionUseCase
import app.memix.core.model.project.Project
import app.memix.core.model.project.StickerItem
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TimelineItem
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Debug-only hand check for P1-07, "killing the app mid-edit restores the draft". Android starts it from
 * MainActivity's `memix.projectCheck` extra in debug builds only; it logs under [ProjectRoundTripCheck.TAG].
 *
 * Step [RUN] saves a baseline, then drives a real [ProjectEditSession] through a fixed script: 60 edits
 * 20 ms apart, a pause, 50 more edits (past the 100 undo steps), undo until nothing is left, 3 redos and
 * one 60-frame drag. Every save is logged as the drafts list sees it. Then, if asked, the process ends
 * itself with SIGKILL a chosen time after the last change ([killProcess]), or goes to the background and
 * ends itself right after the save that going to the background starts.
 * Step [VERIFY], in a new process, logs which scripted version reopened and whether it is identical.
 */
class AutosaveCheck(
    private val killProcess: () -> Unit,
    private val moveToBackground: () -> Unit,
) : KoinComponent {
    private val startEditSession: StartEditSessionUseCase by inject()
    private val saveProject: SaveProjectUseCase by inject()
    private val getProject: GetProjectUseCase by inject()
    private val observeSummaries: ObserveProjectSummariesUseCase by inject()
    private val logger: Logger by inject()

    private var activeSession: ProjectEditSession? = null
    private var lastChange: TimeMark? = null
    private var killAfterBackgroundSave = false

    /** Runs the script. With [killAfter], ends the process that long after the last change. */
    suspend fun run(killAfter: Duration?, background: Boolean) = coroutineScope {
        if (saveProject(scriptedVersion(0)) is Outcome.Failure) {
            log("$RUN: baseline save failed")
            return@coroutineScope
        }
        val saveLog = launch { logSaves() }
        val session = startEditSession(scriptedVersion(0)).also { activeSession = it }
        runScript(session)
        log("$RUN: last change is '${labelOf(session.state.value.project)}'")
        when {
            background -> {
                killAfterBackgroundSave = true
                log("$RUN: moving to the background")
                moveToBackground()
            }
            killAfter != null -> {
                delay(killAfter - (lastChange?.elapsedNow() ?: Duration.ZERO))
                log("$RUN: kill -9 now, ${sinceLastChange()} after the last change")
                killProcess()
            }
            else -> {
                session.close()
                delay(SAVE_LOG_WAIT)
                saveLog.cancel()
                log("$RUN: done. Kill the app any time, then run $VERIFY.")
            }
        }
    }

    /** What the editor screen does on ON_STOP: save now. In background mode the process then ends. */
    suspend fun onAppStopped() {
        val session = activeSession ?: return
        val stoppedAfter = sinceLastChange()
        session.saveNow().join()
        log("$RUN: app stopped $stoppedAfter after the last change; saved at once, ${sinceLastChange()} after it")
        if (!killAfterBackgroundSave) return
        log("$RUN: kill -9 now")
        killProcess()
    }

    suspend fun verify() {
        when (val outcome = getProject(PROJECT_ID)) {
            is Outcome.Failure -> log("$VERIFY: load failed with ${outcome.error}")
            is Outcome.Success -> logReopened(outcome.value)
        }
    }

    private suspend fun runScript(session: ProjectEditSession) {
        for (step in 1..FIRST_EDITS) change { session.commit { it.edited(step) } }
        log("$RUN: $FIRST_EDITS edits ${STEP_INTERVAL.inWholeMilliseconds} ms apart, then a pause")
        delay(PAUSE)
        for (step in FIRST_EDITS + 1..ALL_EDITS) change { session.commit { it.edited(step) } }
        log("$RUN: $ALL_EDITS edits in all")
        var undos = 0
        while (session.state.value.canUndo) change { session.undo().also { undos++ } }
        val oldestKept = labelOf(session.state.value.project)
        change { session.undo() }
        val afterExtraUndo = labelOf(session.state.value.project)
        log("$RUN: undo x$undos reached '$oldestKept'; one more undo stayed at '$afterExtraUndo'")
        repeat(REDOS) { change { session.redo() } }
        log("$RUN: redo x$REDOS reached '${labelOf(session.state.value.project)}', canRedo=${session.state.value.canRedo}")
        drag(session)
    }

    // The finger's in-between positions live in UI state; only the release becomes a project change.
    private suspend fun drag(session: ProjectEditSession) {
        var stickerCenterX = 0f
        repeat(DRAG_FRAMES) { frame ->
            stickerCenterX = (frame + 1).toFloat() / DRAG_FRAMES
            delay(FRAME_INTERVAL)
        }
        val beforeDrag = session.state.value.project
        change { session.commit { it.dragged(stickerCenterX) } }
        change { session.undo() }
        val undoneToBefore = session.state.value.project == beforeDrag
        change { session.redo() }
        log("$RUN: drag of $DRAG_FRAMES frames is one step (one undo goes back to before it: $undoneToBefore), canRedo=${session.state.value.canRedo}")
    }

    // Waits first, so the last change of the script is also the moment the kill delay counts from.
    private suspend fun change(apply: () -> Unit) {
        delay(STEP_INTERVAL)
        apply()
        lastChange = TimeSource.Monotonic.markNow()
    }

    // Each save rewrites the row's last-edited time, so a new time in the drafts list is a finished save.
    // The first row the list shows is the baseline.
    private suspend fun logSaves() {
        observeSummaries()
            .mapNotNull { summaries -> summaries.firstOrNull { it.id == PROJECT_ID } }
            .distinctUntilChanged { old, new -> old.updatedAtEpochUs == new.updatedAtEpochUs }
            .collectIndexed { index, saved ->
                val label = saved.name.removePrefix(NAME_PREFIX)
                if (index == 0) log("baseline '$label' is in the drafts list") else log("saved '$label', ${sinceLastChange()} after the last change")
            }
    }

    // Saving stamps the time of the save, so that one field is left out of the comparison.
    private fun logReopened(reopened: Project) {
        val label = labelOf(reopened)
        val expected = scriptedVersions()[label]
        when {
            expected == null -> log("$VERIFY: DAMAGED. '$label' isn't a version the script makes")
            reopened.copy(updatedAtEpochUs = expected.updatedAtEpochUs) != expected ->
                log("$VERIFY: DAMAGED. '$label' differs from the scripted version")
            else -> log("$VERIFY: reopened '$label', identical to the scripted version")
        }
    }

    private fun sinceLastChange(): String = lastChange?.elapsedNow()?.inWholeMilliseconds?.let { "$it ms" } ?: "no change yet"

    private fun log(message: String) = logger.debug(ProjectRoundTripCheck.TAG, message)

    companion object {
        const val RUN = "autosave"
        const val VERIFY = "autosave-verify"
        private const val PROJECT_ID = "autosave-check"
        private const val NAME_PREFIX = "Autosave check: "
        private const val FIRST_EDITS = 60
        private const val ALL_EDITS = 110
        private const val REDOS = 3
        private const val DRAG_FRAMES = 60
        private const val CAPTION_START_US = 500_000L
        private const val CAPTION_STEP_US = 10_000L
        private val STEP_INTERVAL = 20.milliseconds
        private val FRAME_INTERVAL = 16.milliseconds
        private val PAUSE = 800.milliseconds
        private val SAVE_LOG_WAIT = 1_000.milliseconds

        private fun labelOf(project: Project) = project.name.removePrefix(NAME_PREFIX)

        private fun scriptedVersion(step: Int): Project = SampleProject.everyTrackType().copy(id = PROJECT_ID).edited(step)

        // Edit n renames the draft and moves the caption to a place of its own, so every version is unique
        // and easy to name, whichever version it starts from.
        private fun Project.edited(step: Int): Project = copy(name = "${NAME_PREFIX}edit $step")
            .withItems { item -> if (item is TextItem) item.copy(startUs = CAPTION_START_US + step * CAPTION_STEP_US) else item }

        private fun Project.dragged(stickerCenterX: Float): Project = copy(name = "$name, dragged")
            .withItems { item -> if (item is StickerItem) item.copy(transform = item.transform.copy(centerX = stickerCenterX)) else item }

        // Every version the script can leave behind: each edit, and each edit with the drag on top.
        private fun scriptedVersions(): Map<String, Project> =
            (0..ALL_EDITS).map(::scriptedVersion).flatMap { listOf(it, it.dragged(stickerCenterX = 1f)) }.associateBy(::labelOf)

        // Rebuilds only the tracks whose items change, so versions share the rest, as real edits do.
        private fun Project.withItems(change: (TimelineItem) -> TimelineItem): Project = copy(
            video = video?.let { timeline ->
                timeline.copy(
                    tracks = timeline.tracks.map { track ->
                        val items = track.items.map(change)
                        if (items == track.items) track else track.copy(items = items)
                    },
                )
            },
        )
    }
}
