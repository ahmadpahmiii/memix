package app.memix.core.domain.sound

import app.memix.core.domain.Outcome
import app.memix.core.model.text.graphemeCount
import app.memix.core.model.text.takeGraphemes
import kotlinx.coroutines.flow.Flow

/** The most characters the report's note may have (spec P3-10 → Layout 5). */
const val MAX_REPORT_NOTE_CHARACTERS = 480

/** What the reporter says is wrong; [code] is what the report stores and what `sound_report.reason` carries. */
enum class SoundReportReason(val code: String) {
    RIGHTS("rights"),
    OFFENSIVE("offensive"),
    BROKEN("broken"),
    OTHER("other"),
}

/**
 * One report of a library sound, ready to send. [note] is already limited by [limitReportNote]. It holds nothing about
 * the person or the phone: the table has no column for it.
 */
class SoundReport(val soundId: String, val reason: SoundReportReason, val note: String) {
    /** The `reports.reason` text: the code alone, or "code: note". At most 9 + 2 + 480 characters, inside the table's 500. */
    val storedReason: String get() = if (note.isEmpty()) reason.code else "${reason.code}: $note"
}

/**
 * Reports of library sounds. Implemented in :core:data (Supabase for the report, SQLDelight for the phone's memory of it).
 *
 * Nothing is queued: a report that can't be sent is returned as a failure and the screen offers to send it again.
 */
interface SoundReports {
    /** The ids of every sound this phone has reported successfully. Answers from the phone at once; safe on the main thread. */
    fun observeReportedIds(): Flow<Set<String>>

    /**
     * Sends [report] and, once the server has taken it, remembers the sound as reported. [AppError.Offline] when the
     * server couldn't be reached (no network, a timeout); [AppError.Unexpected] for anything else, including a
     * refusal. Never logs or returns the note in an error.
     */
    suspend fun submit(report: SoundReport): Outcome<Unit>
}

/**
 * The note as it is sent: trimmed, without NUL (the database refuses it), and at most [MAX_REPORT_NOTE_CHARACTERS]
 * characters in both senses, so the stored "code: note" always fits the table's 500.
 */
fun limitReportNote(text: String): String = limitReportNoteLength(text.trim().filter { it != NUL }).trimEnd()

/**
 * [text] cut to [MAX_REPORT_NOTE_CHARACTERS] user-perceived characters (spec) and also to that many Unicode code points,
 * never inside a cluster. The table's check counts code points, and one cluster (a family emoji) can be seven or more, so
 * the cluster limit alone could produce a reason the table refuses. The field uses this while typing, so what the person
 * sees is what is sent.
 */
fun limitReportNoteLength(text: String): String {
    var limited = text.takeGraphemes(MAX_REPORT_NOTE_CHARACTERS)
    while (limited.codePointTotal() > MAX_REPORT_NOTE_CHARACTERS) limited = limited.takeGraphemes(limited.graphemeCount() - 1)
    return limited
}

private fun String.codePointTotal(): Int {
    var total = 0
    var index = 0
    while (index < length) {
        index += if (this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) 2 else 1
        total++
    }
    return total
}

private const val NUL = '\u0000'
