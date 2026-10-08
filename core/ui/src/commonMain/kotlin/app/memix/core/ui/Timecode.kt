package app.memix.core.ui

private const val MICROS_PER_HUNDREDTH = 10_000L
private const val HUNDREDTHS_PER_SECOND = 100L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L

/**
 * A project time as Memix shows it everywhere (P1-04 spec → Timecode format): `MM:SS.cc` ("00:03.20"), and from one
 * hour up `H:MM:SS.cc` ("1:02:07.45"). Rounded down to the hundredth, so it never shows a time not yet reached.
 * ASCII digits in every language: it's a timecode, not a number in a sentence.
 */
fun formatTimecode(timeUs: Long): String {
    val totalHundredths = timeUs.coerceAtLeast(0) / MICROS_PER_HUNDREDTH
    val hundredths = totalHundredths % HUNDREDTHS_PER_SECOND
    val totalSeconds = totalHundredths / HUNDREDTHS_PER_SECOND
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    val totalMinutes = totalSeconds / SECONDS_PER_MINUTE
    val minutes = totalMinutes % MINUTES_PER_HOUR
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutesSecondsHundredths = "${twoDigits(minutes)}:${twoDigits(seconds)}.${twoDigits(hundredths)}"
    return if (hours > 0) "$hours:$minutesSecondsHundredths" else minutesSecondsHundredths
}

private fun twoDigits(value: Long): String = value.toString().padStart(2, '0')
