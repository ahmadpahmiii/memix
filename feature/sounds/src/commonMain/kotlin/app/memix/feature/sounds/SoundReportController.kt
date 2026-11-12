package app.memix.feature.sounds

import androidx.compose.runtime.Immutable
import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.sound.ObserveReportedSoundIdsUseCase
import app.memix.core.domain.sound.ReportSoundUseCase
import app.memix.core.domain.sound.SoundReportReason
import app.memix.core.domain.sound.limitReportNoteLength
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the report form needs from the app, handed over together (a Koin factory) so ViewModel constructors stay readable. */
class SoundReportTools(
    val observeReported: ObserveReportedSoundIdsUseCase,
    val report: ReportSoundUseCase,
)

/** The report form (spec P3-10): kept while the person goes back to the details and returns, cleared when the sheet closes. */
@Immutable
data class SoundReportUi(
    /** None is chosen at first on purpose: a preselected reason would produce junk reports. */
    val reason: SoundReportReason? = null,
    /** At most [app.memix.core.domain.sound.MAX_REPORT_NOTE_CHARACTERS] characters, as typed (not trimmed). */
    val note: String = "",
    /** The report is on its way: Send looks busy and ignores taps, the form looks disabled. */
    val sending: Boolean = false,
    val message: SoundReportMessage? = null,
    /** Counts every message shown, so the same message twice in a row still moves focus and is announced again. */
    val messageCount: Int = 0,
)

/** The one line above Send, when there is something to say. */
enum class SoundReportMessage { PICK_REASON, OFFLINE, FAILED }

/**
 * Owns the report form of a [SoundsViewModel]: the choice, the note, the send and its outcome. Main thread only.
 * [onChanged] gets the form after every change; [onSent] says the server took the report.
 * The note is never logged or kept beyond the form; nothing here knows more about the person than the form holds.
 */
internal class SoundReportController(
    private val scope: CoroutineScope,
    private val tools: SoundReportTools,
    private val onChanged: (SoundReportUi) -> Unit,
    private val onSent: () -> Unit,
) {
    private var form = SoundReportUi()
    private var sendJob: Job? = null

    /** The form as it is now, including the note, which is not published on every keystroke (see [changeNote]). */
    val current: SoundReportUi get() = form

    fun chooseReason(reason: SoundReportReason) {
        if (form.sending) return
        change(form.copy(reason = reason, message = null))
    }

    /**
     * Keeps the note without publishing it: a keystroke must not rebuild the whole Sounds state. The pane seeds its field from
     * [current] when it opens, and every other change (a reason, Send, a failure) publishes the latest note with it.
     */
    fun changeNote(text: String) {
        if (form.sending) return
        form = form.copy(note = limitReportNoteLength(text))
    }

    /** Sends the form for [soundId]. Without a reason it only says so; while one send runs, another tap does nothing. */
    fun send(soundId: String) {
        if (form.sending) return
        val reason = form.reason
        if (reason == null) {
            change(form.withMessage(SoundReportMessage.PICK_REASON))
            return
        }
        val note = form.note
        change(form.copy(sending = true, message = null))
        sendJob = scope.launch {
            when (val outcome = tools.report(soundId, reason, note)) {
                is Outcome.Success -> {
                    sendJob = null
                    onSent()
                }
                is Outcome.Failure -> change(form.copy(sending = false).withMessage(outcome.error.toMessage()))
            }
        }
    }

    /** The sheet closed, or the report went out: the form empties, and a send still running is given up. */
    /** Publishes [current]: the pane is opening and needs the latest note. */
    fun publish() = onChanged(form)

    fun reset() {
        sendJob?.cancel()
        sendJob = null
        change(SoundReportUi())
    }

    private fun SoundReportUi.withMessage(next: SoundReportMessage) = copy(message = next, messageCount = messageCount + 1)

    private fun AppError.toMessage() = if (this == AppError.Offline) SoundReportMessage.OFFLINE else SoundReportMessage.FAILED

    private fun change(next: SoundReportUi) {
        if (next == form) return
        form = next
        onChanged(next)
    }
}
