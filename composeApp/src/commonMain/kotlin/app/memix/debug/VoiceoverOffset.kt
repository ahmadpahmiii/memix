package app.memix.debug

import app.memix.core.domain.voiceover.VoiceoverTuning
import org.koin.mp.KoinPlatform

/**
 * The voiceover clap test's correction (spec docs/ux/specs/P3-08-voiceover.md → Sync; builds with hand checks):
 * `--el memix.voiceoverOffsetUs <µs>` moves every take recorded in this run later on the timeline (negative: earlier), on top
 * of the measured timing, so the phone's own microphone delay can be dialed in without a new build. Release builds never call it.
 */
object VoiceoverOffset {
    fun arm(offsetUs: Long) {
        KoinPlatform.getKoin().get<VoiceoverTuning>().extraOffsetUs = offsetUs
    }
}
