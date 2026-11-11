package app.memix.core.ui

/** "0:02": minutes and seconds, rounded up to whole seconds, at least 0:01. The length in a sound's meta line (spec P1-06, P3-02). */
fun soundLengthText(durationUs: Long): String =
    minutesAndSeconds(((durationUs + MICROS_PER_SECOND - 1) / MICROS_PER_SECOND).coerceAtLeast(1))

/** "0:01": how far a sound has played, rounded down to whole seconds so it never shows a second not yet reached (spec P3-03). */
fun soundPositionText(positionUs: Long): String = minutesAndSeconds(positionUs.coerceAtLeast(0) / MICROS_PER_SECOND)

private fun minutesAndSeconds(seconds: Long): String =
    "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"

private const val MICROS_PER_SECOND = 1_000_000L
private const val SECONDS_PER_MINUTE = 60L
