package app.memix.core.domain.sound

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.Outcome
import kotlinx.coroutines.flow.Flow

/** The sounds this phone has reported, so a sound shows "Reported. Thank you." in place of the Report row. */
class ObserveReportedSoundIdsUseCase(private val reports: SoundReports) {
    operator fun invoke(): Flow<Set<String>> = reports.observeReportedIds()
}

/**
 * Sends a report of a library sound (spec P3-10) and counts it as `sound_report` once the server has taken it, with the
 * reason's code and never the note. The note is limited here whatever the caller passed.
 */
class ReportSoundUseCase(private val reports: SoundReports, private val analytics: Analytics) {
    suspend operator fun invoke(soundId: String, reason: SoundReportReason, note: String): Outcome<Unit> {
        val outcome = reports.submit(SoundReport(soundId, reason, limitReportNote(note)))
        if (outcome is Outcome.Success) analytics.log(AnalyticsEvent.SoundReported(soundId, reason))
        return outcome
    }
}
