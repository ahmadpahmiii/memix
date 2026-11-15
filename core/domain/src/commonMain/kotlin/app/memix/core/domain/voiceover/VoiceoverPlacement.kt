package app.memix.core.domain.voiceover

import kotlin.math.max

/*
 * Where a take lands on the timeline so that what was said at picture time T sits at T (spec P3-08 → Sync). The take's
 * first sample was captured at a moment of the monotonic clock; the preview's position at that moment says where on the
 * timeline that sample belongs. Everything here is pure arithmetic on µs and nanoseconds.
 */

private const val NANOS_PER_MICRO = 1_000L

/**
 * Where the take goes: [startUs] on the timeline, with [trimInUs] of its first samples cut off. [captureUs] is where its
 * first sample belongs, which can be before the playhead. [spreadUs] is how far apart the clock samples put it (0 with one
 * sample), a sign of an unsteady player clock.
 */
data class TakePlacement(val startUs: Long, val trimInUs: Long, val captureUs: Long, val spreadUs: Long = 0)

/**
 * The timeline time of the take's first sample from one preview [clock] sample: the sample moved by the nanoseconds between
 * it and the first sample, plus [extraOffsetUs], a hand-check correction (positive moves the take later; 0 in release), minus
 * [primingUs], the encoder's start-up delay that the file carries as leading silence (the real sample sits that much later in
 * the decoded file, so the clip starts that much earlier; 0 when the file's edit list removes it). The sample is taken while
 * the video plays steadily, so it can be extrapolated back to before playback started.
 *
 * The samples captured before the playhead (the microphone opens a moment before the video starts moving) have no
 * picture to match, so they are trimmed: the clip starts at the playhead with [TakePlacement.trimInUs] cut off, or at
 * [TakePlacement.captureUs] when the microphone came later than the video.
 */
fun placeTake(timing: TakeTiming, clock: ClockSample, playheadUs: Long, extraOffsetUs: Long = 0, primingUs: Long = 0): TakePlacement =
    placeTake(timing, listOf(clock), playheadUs, extraOffsetUs, primingUs)

/**
 * [placeTake] from several clock samples taken at different moments of the take: the median capture time is used, so one
 * jittery reading of the player's clock does not move the take, and [TakePlacement.spreadUs] reports the largest difference.
 */
fun placeTake(timing: TakeTiming, clocks: List<ClockSample>, playheadUs: Long, extraOffsetUs: Long = 0, primingUs: Long = 0): TakePlacement {
    require(clocks.isNotEmpty()) { "A placement needs a clock sample" }
    val captures = clocks.map { it.positionUs + (timing.firstFrameNanos - it.atNanos) / NANOS_PER_MICRO + extraOffsetUs - primingUs }.sorted()
    val captureUs = captures[captures.size / 2]
    val startUs = max(captureUs, playheadUs)
    return TakePlacement(startUs, trimInUs = startUs - captureUs, captureUs = captureUs, spreadUs = captures.last() - captures.first())
}

/**
 * The line a hand-check build logs for each take (tag `MemixVoiceover`), the numbers the clap test reads (docs/qa/phase-3/
 * engineer-hand-checks.md → P3-08). Holds no personal data.
 */
fun syncReportLine(timing: TakeTiming, clocks: List<ClockSample>, playheadUs: Long, extraOffsetUs: Long, primingUs: Long, placement: TakePlacement): String =
    "take timing=${timing.source} startDelay=${timing.startDelayUs}us inputLatency=${timing.inputLatencyUs}us " +
        "samples=${clocks.size} firstSample=${clocks.firstOrNull()?.positionUs}us firstFrameBeforeSample=${clocks.firstOrNull()?.let { (it.atNanos - timing.firstFrameNanos) / NANOS_PER_MICRO }}us " +
        "playhead=${playheadUs}us extraOffset=${extraOffsetUs}us priming=${primingUs}us capture=${placement.captureUs}us spread=${placement.spreadUs}us " +
        "start=${placement.startUs}us trimIn=${placement.trimInUs}us"
