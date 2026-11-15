package app.memix.core.domain.voiceover

import kotlin.concurrent.Volatile

/**
 * The voiceover sync's hand-check settings (spec P3-08 → Sync; docs/qa/phase-3/engineer-hand-checks.md → P3-08). In a
 * release build [logOffsets] is false and [extraOffsetUs] stays 0. Hand-check builds log each take's offsets, and the
 * clap test can try a correction with `--el memix.voiceoverOffsetUs <µs>`: positive moves a take later on the timeline.
 */
class VoiceoverTuning(val logOffsets: Boolean) {
    @Volatile
    var extraOffsetUs: Long = 0
}
