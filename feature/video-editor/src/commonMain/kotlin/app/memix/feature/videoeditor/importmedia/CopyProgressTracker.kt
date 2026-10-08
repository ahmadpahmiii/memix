package app.memix.feature.videoeditor.importmedia

import app.memix.core.domain.media.ImportProgress
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Turns a copy's byte-by-byte progress into what the import sheet shows: at most one update per
 * [PUBLISH_INTERVAL] (spec: 10 a second), new items included, so a pick of many small photos can't flood the
 * sheet or the screen reader; the count catches up within one interval. Also estimates the time left from the
 * average speed over the last [SPEED_WINDOW].
 *
 * One tracker per copy run; the copying thread calls it, one call at a time.
 */
internal class CopyProgressTracker(timeSource: TimeSource = TimeSource.Monotonic) {
    private val startedAt = timeSource.markNow()
    private var lastPublishedAt: Duration? = null

    // (time since start, completed bytes) at each published update; the first one is the base for the speed.
    private val samples = ArrayDeque<Pair<Duration, Long>>()

    /** Returns the new sheet numbers, or null when it's too soon after the last update to show any. */
    fun publish(progress: ImportProgress): CopyProgressUi? {
        val now = startedAt.elapsedNow()
        val previous = lastPublishedAt
        if (previous != null && now - previous < PUBLISH_INTERVAL) return null
        lastPublishedAt = now
        recordSample(now, progress.completedBytes)
        return CopyProgressUi(
            itemNumber = progress.itemNumber.coerceAtMost(progress.itemCount),
            itemCount = progress.itemCount,
            fraction = progress.fraction,
            percent = floor(progress.fraction * PERCENT).toInt().coerceIn(0, MAX_PERCENT_WHILE_COPYING),
            timeLeft = timeLeft(now, progress),
            currentFileCopiedBytes = if (progress.currentItemSizeKnown) null else progress.currentItemCopiedBytes,
        )
    }

    private fun recordSample(now: Duration, completedBytes: Long) {
        samples.addLast(now to completedBytes)
        // Keep the newest sample that is at least SPEED_WINDOW old as the base, and everything after it.
        while (samples.size > 1 && samples[1].first <= now - SPEED_WINDOW) samples.removeFirst()
    }

    private fun timeLeft(now: Duration, progress: ImportProgress): TimeLeft? {
        val totalBytes = progress.totalBytes ?: return null
        if (now < SPEED_WINDOW) return null
        val (baseTime, baseBytes) = samples.first()
        val elapsedSeconds = (now - baseTime).toDouble(DurationUnit.SECONDS)
        if (elapsedSeconds <= 0.0) return null
        val bytesPerSecond = (progress.completedBytes - baseBytes) / elapsedSeconds
        if (bytesPerSecond <= 0.0) return null
        val secondsLeft = (totalBytes - progress.completedBytes).coerceAtLeast(0) / bytesPerSecond
        return if (secondsLeft > SHOW_TIME_LEFT_ABOVE_SECONDS) roundTimeLeft(secondsLeft) else null
    }

    companion object {
        val PUBLISH_INTERVAL = 100.milliseconds
        val SPEED_WINDOW = 2.seconds
        private const val SHOW_TIME_LEFT_ABOVE_SECONDS = 10.0
        private const val PERCENT = 100f
        private const val MAX_PERCENT_WHILE_COPYING = 99
        private const val SECONDS_STEP = 5
        private const val SECONDS_PER_MINUTE = 60

        /** Up to the next 5 s step under a minute, up to the next whole minute from there. */
        internal fun roundTimeLeft(seconds: Double): TimeLeft {
            val wholeSeconds = ceil(seconds).toInt()
            val inSteps = ceil(wholeSeconds.toDouble() / SECONDS_STEP).toInt() * SECONDS_STEP
            return if (inSteps < SECONDS_PER_MINUTE) {
                TimeLeft.Seconds(inSteps)
            } else {
                TimeLeft.Minutes(ceil(wholeSeconds.toDouble() / SECONDS_PER_MINUTE).toInt())
            }
        }
    }
}
