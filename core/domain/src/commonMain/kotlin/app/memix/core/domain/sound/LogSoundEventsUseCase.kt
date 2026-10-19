package app.memix.core.domain.sound

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.SoundTab
import app.memix.core.model.project.ProjectType

/**
 * The meme sound events of PRD → Metrics and analytics. Adding a sound counts here, as `sound_add`, not as a
 * `tool_use` step (spec P1-06 → Analytics; a duplicate is `tool_use` `duplicate` instead).
 */
class LogSoundEventsUseCase(private val analytics: Analytics) {
    fun added(soundId: String, editor: ProjectType) {
        analytics.log(AnalyticsEvent.SoundAdd(soundId, SoundTab.STARTER, editor))
    }

    fun previewed(soundId: String) {
        analytics.log(AnalyticsEvent.SoundPreview(soundId, SoundTab.STARTER))
    }
}
