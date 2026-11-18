package app.memix.feature.videoeditor

import app.memix.core.domain.project.ReverseRefusal
import app.memix.core.domain.project.hasReversedClips
import app.memix.core.domain.project.reversibleClip
import app.memix.core.domain.project.reverseRefusal
import app.memix.core.domain.project.setClipReversed
import app.memix.core.domain.video.ReverseFailure
import app.memix.core.model.project.FreezeReverseCatalog
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Reverse tool (spec docs/ux/specs/P4-10-reverse-and-freeze.md): a switch on a video clip of the main track or an overlay layer. A tap is one undo step and never
 * waits: the engine prepares the backward picture in the background, and while it does the clip holds its first frame, silent. This class shows that wait
 * ("Reversing 40%" once it has taken a second, spoken every quarter), says when the copy is ready, and switches Reverse off again, with one toast, for a clip whose
 * copy could not be made.
 */
internal class ReverseControl(private val host: EditorHost) {
    private var statusTimer: Job? = null
    private var preparing = false
    private var statusShown = false
    private var lastSpokenQuarter = 0
    private var latestPercent = 0
    private val failuresHandled = mutableSetOf<String>()

    /** The Reverse tool of the selected clip: on becomes off and the other way round; a clip over 60 s explains and changes nothing. */
    fun toggle() {
        val itemId = host.uiState.selectedItemId ?: return
        val project = host.currentProject() ?: return
        val clip = project.reversibleClip(itemId) ?: return
        if (project.reverseRefusal(itemId) == ReverseRefusal.TOO_LONG) return host.showToast(ToastMessage.Explain(Explanation.REVERSE_LONG))
        val on = !clip.reversed
        // Playback goes on: the switch doesn't pause (spec P4-10 → Interactions).
        host.commit(VideoEdit.CLIP_REVERSE, pausesPlayback = false, value = if (on) VALUE_ON else VALUE_OFF) { it.setClipReversed(itemId, on) }
    }

    /** What the preview says about reversed copies: how far the slowest is (null when none is being made) and which clips failed. */
    fun onPlayback(percent: Int?, failures: List<ReverseFailure>) {
        showProgress(percent)
        switchOffFailed(failures)
    }

    // The status appears only when the copy isn't ready after STATUS_AFTER_MS and goes with a fade when it is.
    private fun showProgress(percent: Int?) {
        if (percent == null) return finishProgress()
        if (!preparing) {
            preparing = true
            lastSpokenQuarter = 0
            statusTimer = host.scope.launch {
                delay(FreezeReverseCatalog.STATUS_AFTER_MS.milliseconds)
                statusShown = true
                host.updateUiState { it.copy(reversePercent = latestPercent) }
            }
        }
        latestPercent = percent
        if (!statusShown) return
        host.updateUiState { it.copy(reversePercent = percent) }
        speakEveryQuarter(percent)
    }

    // A screen reader hears the progress at most every 25% (spec P4-10 → Accessibility), never on each percent.
    private fun speakEveryQuarter(percent: Int) {
        val quarter = percent / QUARTER
        if (quarter <= lastSpokenQuarter) return
        lastSpokenQuarter = quarter
        host.showToast(ToastMessage.ReverseProgress(quarter * QUARTER), spokenOnly = true)
    }

    private fun finishProgress() {
        if (!preparing) return
        preparing = false
        statusTimer?.cancel()
        statusTimer = null
        if (!statusShown) return
        statusShown = false
        host.updateUiState { it.copy(reversePercent = null) }
        // Turning Reverse off while it was being made also ends the wait, and nothing is ready then.
        if (host.currentProject()?.hasReversedClips() == true) host.showToast(ToastMessage.ReverseReady, spokenOnly = true)
    }

    // A clip the engine could not reverse goes back to forward: one toast, and the edit is undone as one step that logs nothing (spec P4-10 → States).
    private fun switchOffFailed(failures: List<ReverseFailure>) {
        failuresHandled.retainAll(failures.map { it.itemId }.toSet())
        for (failure in failures) {
            if (!failuresHandled.add(failure.itemId)) continue
            val itemId = failure.itemId
            val off = host.commit(VideoEdit.CLIP_REVERSE, pausesPlayback = false, loggedAs = emptyList()) { it.setClipReversed(itemId, false) }
            if (off) host.showToast(ToastMessage.Explain(if (failure.tooLong) Explanation.REVERSE_LONG else if (failure.noSpace) Explanation.REVERSE_NO_SPACE else Explanation.REVERSE_FAILED))
        }
    }

    private companion object {
        const val VALUE_ON = "on"
        const val VALUE_OFF = "off"
        const val QUARTER = 25
    }
}
