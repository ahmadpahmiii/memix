package app.memix.feature.videoeditor

import app.memix.core.domain.project.videoLengthUs
import app.memix.core.domain.voiceover.ClockSample
import app.memix.core.domain.voiceover.MIN_TAKE_US
import app.memix.core.domain.voiceover.RecordedTake
import app.memix.core.domain.voiceover.StartTakeResult
import app.memix.core.domain.voiceover.TakeInterruption
import app.memix.core.domain.voiceover.VoiceRoute
import app.memix.core.domain.voiceover.VoiceTake
import app.memix.core.domain.voiceover.addVoiceover
import app.memix.core.domain.voiceover.canRecordAt
import app.memix.core.domain.voiceover.toMediaRef
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The Voiceover panel (spec docs/ux/specs/P3-08-voiceover.md): Record counts 3, 2, 1, opens the microphone, plays the video
 * from the playhead and lands the take as an Audio clip placed by the clock (`placeTake`). Everything runs on the main thread;
 * the recorder's own threads are behind [EditorVoiceover.startTake].
 *
 * A take ends one way: Stop (or Play/Pause, Done, back, close), the video's end, or an interruption (the preview paused by a
 * call or an unplugged headset, the system silencing the microphone, a full phone, the app leaving the screen). Each keeps
 * what was recorded when it is long enough and has sound, as one "Voiceover" step.
 */
internal class VoiceoverControl(
    private val host: EditorHost,
    private val projectId: String,
    private val voiceover: EditorVoiceover,
) {
    private var microphoneGranted = false

    // The phone's dialog was answered No once; from then on Settings is the only way (spec → Permission).
    private var microphoneAnswered = false
    private var countdownJob: Job? = null
    private var opening = false
    private var cancelWhenOpened = false
    private var recording: Recording? = null
    private var lastTakeId: String? = null
    private var interruptedWhileAway = false

    // Cancel does nothing for the first moments of the countdown: a double tap on Record would land on it (spec review, P3-08).
    private var cancelArmed = false

    /** A take is being recorded: the controls that would change the project wait for Stop. */
    val isRecording: Boolean get() = recording != null

    /** The countdown or a take is running. */
    val isBusy: Boolean get() = host.uiState.voiceover?.phase.let { it != null && it != VoicePhase.READY }

    fun open() {
        if (host.preview == null) return
        host.preview?.pause()
        lastTakeId = null
        host.updateUiState { it.copy(voiceover = VoiceoverPanelUi(VoicePhase.READY, voiceover.route(), VoiceoverLive())) }
        host.scope.launch {
            microphoneAnswered = voiceover.microphoneAsked.wasDenied()
            showMicrophoneDenied()
        }
    }

    /** Done, back or Escape: stops what runs; with nothing running, closes and selects the newest take. */
    fun close() {
        when {
            recording != null -> end(StopCause.USER)
            isBusy -> cancelCountdown()
            else -> closePanel()
        }
    }

    /** Stop, or Cancel during the countdown. */
    fun stop() {
        if (recording != null) end(StopCause.USER) else if (cancelArmed) cancelCountdown()
    }

    fun record(granted: Boolean) {
        val panel = host.uiState.voiceover ?: return
        // Tapping twice quickly is one tap: the button is Cancel or Stop from the first one.
        if (panel.phase != VoicePhase.READY || panel.askMicrophone || opening) return
        val project = host.currentProject() ?: return
        if (!canRecordAt(host.currentPositionUs(), project.videoLengthUs())) return host.showToast(ToastMessage.Explain(Explanation.VOICEOVER_AT_END))
        microphoneGranted = granted
        when {
            granted -> startCountdown()
            microphoneAnswered -> showMicrophoneDenied()
            else -> update { it.copy(askMicrophone = true, route = voiceover.route()) }
        }
    }

    fun microphoneAnswered(granted: Boolean) {
        if (host.uiState.voiceover == null) return
        microphoneGranted = granted
        update { it.copy(askMicrophone = false) }
        if (granted) return startCountdown()
        microphoneAnswered = true
        host.scope.launch { voiceover.microphoneAsked.markDenied() }
        showMicrophoneDenied()
    }

    /** Back on screen, or the panel opened: Settings may have changed the permission. Never starts a take by itself. */
    fun microphoneChecked(granted: Boolean) {
        microphoneGranted = granted
        if (host.uiState.voiceover == null) return
        // Coming back from Settings: a dialog that was waiting for an answer is gone.
        update { it.copy(askMicrophone = false) }
        showMicrophoneDenied()
    }

    /** The app left the screen: a take stops there and is kept; its message shows when the user is back. */
    fun appStopped(afterLanding: () -> Unit) {
        if (recording != null) {
            interruptedWhileAway = true
            // The take lands after the editor's own save at ON_STOP ran, so the draft is saved again once it has.
            end(StopCause.INTERRUPTED, afterLanding)
        } else {
            cancelCountdown()
        }
    }

    fun appStarted() {
        if (!interruptedWhileAway) return
        interruptedWhileAway = false
        host.showToast(ToastMessage.Explain(Explanation.VOICEOVER_INTERRUPTED))
    }

    /** The editor is closing: stops a take, keeps it, then runs [afterwards]. Returns false when nothing was running. */
    fun stopForClose(afterwards: () -> Unit): Boolean {
        if (recording == null) {
            cancelCountdown()
            return false
        }
        end(StopCause.USER, afterwards)
        return true
    }

    /** The editor's ViewModel is gone: the microphone and the file are let go without landing anything. */
    fun release() {
        countdownJob?.cancel()
        recording?.let {
            it.watchers.cancel()
            it.take.discard()
        }
        recording = null
    }

    // ---- Countdown ---------------------------------------------------------------------------------------------

    private fun startCountdown() {
        update { it.copy(phase = VoicePhase.COUNTING_DOWN, countdown = COUNTDOWN_SECONDS, savedAtUs = null, quiet = false) }
        cancelWhenOpened = false
        cancelArmed = false
        countdownJob = host.scope.launch {
            launch {
                delay(CANCEL_GRACE)
                cancelArmed = true
            }
            for (second in COUNTDOWN_SECONDS downTo 1) {
                update { it.copy(countdown = second) }
                delay(1.seconds)
            }
            openTake()
        }
    }

    private fun cancelCountdown() {
        if (host.uiState.voiceover?.phase != VoicePhase.COUNTING_DOWN) return
        // Past the last tick the microphone is being opened by a call that can't be taken back; the result is dropped when it arrives.
        if (opening) cancelWhenOpened = true else countdownJob?.cancel()
        update { it.copy(phase = VoicePhase.READY, countdown = 0) }
    }

    private suspend fun openTake() {
        opening = true
        val result = voiceover.startTake(projectId)
        opening = false
        if (result is StartTakeResult.Started && cancelWhenOpened) return result.take.discard()
        if (cancelWhenOpened) return
        when (result) {
            is StartTakeResult.Started -> beginRecording(result.take)
            StartTakeResult.NoSpace -> failedToStart(Explanation.VOICEOVER_NO_SPACE)
            StartTakeResult.Busy -> failedToStart(Explanation.VOICEOVER_BUSY)
            StartTakeResult.Failed -> failedToStart(Explanation.VOICEOVER_SILENT)
            StartTakeResult.PermissionMissing -> {
                microphoneGranted = false
                failedToStart(null)
                showMicrophoneDenied()
            }
        }
    }

    private fun failedToStart(explanation: Explanation?) {
        update { it.copy(phase = VoicePhase.READY, countdown = 0) }
        explanation?.let { host.showToast(ToastMessage.Explain(it)) }
    }

    // ---- Recording ---------------------------------------------------------------------------------------------

    private fun beginRecording(take: VoiceTake) {
        val preview = host.preview
        val project = host.currentProject()
        if (preview == null || project == null) return take.discard()
        val route = voiceover.route()
        val startPlayheadUs = host.currentPositionUs()
        val live = host.uiState.voiceover?.live ?: VoiceoverLive()
        // Without headphones the speaker's sound would go into the microphone: the picture plays, the project's sound doesn't.
        preview.setSilent(route == VoiceRoute.PHONE_SPEAKER)
        preview.play()
        val current = Recording(take, startPlayheadUs, project.videoLengthUs(), live)
        recording = current
        update { it.copy(phase = VoicePhase.RECORDING, countdown = 0, route = route, quiet = false) }
        // Lazy, so the job is on the take before a watcher can end it.
        current.watchers = host.scope.launch(start = CoroutineStart.LAZY) {
            launch { watchPosition(current) }
            launch { watchPlayback(current) }
            launch { watchLevel(current) }
            launch { watchInterruption(current) }
        }
        current.watchers.start()
    }

    // The take's length follows the picture; the clock sample for placing it is taken once the video moves steadily.
    private suspend fun watchPosition(current: Recording) {
        val preview = host.preview ?: return
        preview.positionUs.collect { positionUs ->
            current.live.elapsedUs = (positionUs - current.startPlayheadUs).coerceAtLeast(0)
            val moved = positionUs - current.startPlayheadUs
            // Three readings of the player's clock at 250, 500 and 750 ms of movement; the median places the take.
            while (current.samples.size < SAMPLE_AFTER_US.size && moved >= SAMPLE_AFTER_US[current.samples.size]) {
                current.samples += preview.sampleClock() ?: break
            }
            if (positionUs >= current.videoLengthUs - END_TOLERANCE_US) end(StopCause.REACHED_END)
        }
    }

    // Anything that pauses the preview ends the take: our own Stop first, otherwise a call, the end, or an unplugged headset.
    private suspend fun watchPlayback(current: Recording) {
        val preview = host.preview ?: return
        preview.playback.first { !it.isPlaying }
        end(StopCause.INTERRUPTED)
    }

    private suspend fun watchLevel(current: Recording): Unit = coroutineScope {
        var quietTimer: Job? = null
        current.take.level.collect { level ->
            current.live.level = level
            if (level >= QUIET_LEVEL) {
                quietTimer?.cancel()
                quietTimer = null
                if (host.uiState.voiceover?.quiet == true) update { it.copy(quiet = false) }
            } else if (quietTimer == null) {
                // A child of this watcher, so it ends with the take.
                quietTimer = launch {
                    delay(QUIET_AFTER)
                    update { it.copy(quiet = true) }
                }
            }
        }
    }

    private suspend fun watchInterruption(current: Recording) {
        val reason = current.take.interruption.first { it != null }
        end(if (reason == TakeInterruption.NO_SPACE) StopCause.NO_SPACE else StopCause.INTERRUPTED)
    }

    /** Stops the picture and the microphone together and lands the take, once, however many things ask. */
    private fun end(cause: StopCause, afterwards: (() -> Unit)? = null) {
        val current = recording ?: return
        if (current.ending) {
            afterwards?.let { current.afterwards += it }
            return
        }
        current.ending = true
        afterwards?.let { current.afterwards += it }
        val preview = host.preview
        preview?.pause()
        preview?.setSilent(false)
        // Read after the pause: it is where the picture stopped, which is where the clip ends.
        val stoppedAtUs = preview?.sampleClock()?.positionUs ?: host.currentPositionUs()
        val resolved = resolveCause(cause, stoppedAtUs, current)
        // The player can stop a frame short of the end; a take that reached the end ends exactly there (spec P3-08 → Stops on its own).
        val endUs = if (resolved == StopCause.REACHED_END) current.videoLengthUs else stoppedAtUs
        current.watchers.cancel()
        host.scope.launch {
            val recorded = withTimeoutOrNull(FINISH_TIMEOUT) { current.take.finish() }
            if (recorded == null) {
                // The recorder never answered: let it go and say nothing was recorded.
                current.take.discard()
                host.showToast(ToastMessage.Explain(Explanation.VOICEOVER_SILENT))
            } else {
                land(current, recorded, endUs, resolved)
            }
            recording = null
            update { it.copy(phase = VoicePhase.READY, countdown = 0, quiet = false) }
            current.afterwards.forEach { it() }
        }
    }

    // A pause nobody asked for at the video's end is the end, not an interruption.
    private fun resolveCause(cause: StopCause, stoppedAtUs: Long, current: Recording): StopCause =
        if (cause == StopCause.INTERRUPTED && stoppedAtUs >= current.videoLengthUs - END_TOLERANCE_US) StopCause.REACHED_END else cause

    // ---- Landing -----------------------------------------------------------------------------------------------

    private suspend fun land(current: Recording, recorded: RecordedTake, endUs: Long, cause: StopCause) {
        val takeId = host.newId()
        val media = recorded.toMediaRef() ?: return discard(recorded, Explanation.VOICEOVER_TOO_SHORT)
        if (recorded.durationUs < MIN_TAKE_US) return discard(recorded, Explanation.VOICEOVER_TOO_SHORT)
        if (recorded.peak < SILENCE_PEAK) return discard(recorded, Explanation.VOICEOVER_SILENT)
        // A take the player never moved steadily through (it stalled) is placed as if it began with the picture.
        val clocks = current.samples.toList().ifEmpty { listOf(ClockSample(current.startPlayheadUs, recorded.timing.firstFrameNanos)) }
        val placement = voiceover.placeTake(recorded, clocks, current.startPlayheadUs)
        // The edit changes nothing only when what is left of the take, trimmed to the picture and the video's end, is under 0.5 s.
        val landed = host.commit(VideoEdit.VOICEOVER, pausesPlayback = false) { it.addVoiceover(media, takeId, placement, endUs, host.newId()) }
        if (!landed) return discard(recorded, Explanation.VOICEOVER_TOO_SHORT)
        lastTakeId = takeId
        val lengthUs = (minOf(endUs, current.videoLengthUs) - placement.startUs).coerceAtMost(recorded.durationUs - placement.trimInUs)
        // The Confirm haptic is the "it landed" signal; the panel covers the clip, so no bonk is seen.
        host.updateUiState { it.copy(landed = host.newLanding(persistentSetOf(takeId), isSound = true)) }
        update { it.copy(savedAtUs = placement.startUs) }
        // One message slot: what stopped the take says it was saved; a Stop the user chose is only spoken.
        val explanation = explanationFor(cause)
        if (explanation != null) host.showToast(ToastMessage.Explain(explanation))
        else if (cause == StopCause.USER) host.showToast(ToastMessage.VoiceoverAdded(placement.startUs, lengthUs), spokenOnly = true)
    }

    // Away from the screen the interrupted message waits for the user's return ([appStarted]).
    private fun explanationFor(cause: StopCause): Explanation? = when (cause) {
        StopCause.USER -> null
        StopCause.REACHED_END -> Explanation.VOICEOVER_END
        StopCause.NO_SPACE -> Explanation.VOICEOVER_NO_SPACE
        StopCause.INTERRUPTED -> if (interruptedWhileAway) null else Explanation.VOICEOVER_INTERRUPTED
    }

    private suspend fun discard(recorded: RecordedTake, explanation: Explanation) {
        voiceover.discardTake(recorded)
        host.showToast(ToastMessage.Explain(explanation))
    }

    // ---- Panel ---------------------------------------------------------------------------------------------------

    private fun showMicrophoneDenied() {
        val denied = microphoneAnswered && !microphoneGranted
        if (host.uiState.voiceover?.microphoneDenied != denied) update { it.copy(microphoneDenied = denied) }
    }

    private fun closePanel() {
        host.updateUiState { it.copy(voiceover = null) }
        lastTakeId?.let(host::select)
        host.returnFocusTo(FocusTarget.VOICEOVER_TOOL)
    }

    private fun update(change: (VoiceoverPanelUi) -> VoiceoverPanelUi) {
        host.updateUiState { state -> state.voiceover?.let { state.copy(voiceover = change(it)) } ?: state }
    }

    /** Why a take ended. */
    private enum class StopCause { USER, REACHED_END, INTERRUPTED, NO_SPACE }

    /** The take that runs: its recorder, where the playhead was when the picture started, and its watchers. */
    private class Recording(val take: VoiceTake, val startPlayheadUs: Long, val videoLengthUs: Long, val live: VoiceoverLive) {
        val samples = mutableListOf<ClockSample>()
        var ending = false
        val afterwards = mutableListOf<() -> Unit>()
        var watchers: Job = Job()
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 3

        // The preview's clock is read once the picture has moved this far (and again twice): playback has settled and extrapolating back is exact.
        val SAMPLE_AFTER_US = listOf(250_000L, 500_000L, 750_000L)

        // Cancel is ignored this long after Record, and a recorder that doesn't finish in this time is given up on.
        val CANCEL_GRACE = 0.3.seconds
        val FINISH_TIMEOUT = 5.seconds

        // The end of the video within one frame (30 fps), as the transport row counts it.
        const val END_TOLERANCE_US = 33_333L

        // Under 1% of full scale (-40 dBFS) is the room, not a voice; a whole take that quiet is "nothing recorded".
        const val QUIET_LEVEL = 0.01f
        const val SILENCE_PEAK = 0.01f
        val QUIET_AFTER = 3.seconds
    }
}
