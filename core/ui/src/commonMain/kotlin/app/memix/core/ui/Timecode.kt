package app.memix.core.ui

import androidx.compose.runtime.Composable
import memix.core.ui.generated.resources.Res
import memix.core.ui.generated.resources.a11y_minutes
import memix.core.ui.generated.resources.a11y_time_minutes_seconds
import memix.core.ui.generated.resources.a11y_time_of
import memix.core.ui.generated.resources.a11y_time_seconds
import memix.core.ui.generated.resources.decimal_separator
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val MICROS_PER_HUNDREDTH = 10_000L
private const val MICROS_PER_TENTH = 100_000L
private const val TENTHS_PER_SECOND = 10L
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

/**
 * A project time as a screen reader says it, in the user's language and number format: "3.2 seconds", and from one
 * minute up "1 minute 3.2 seconds" (spec P1-04 → Copy). Tenths, rounded down like [formatTimecode].
 */
@Composable
fun spokenTime(timeUs: Long): String {
    val totalTenths = timeUs.coerceAtLeast(0) / MICROS_PER_TENTH
    val minutes = (totalTenths / (TENTHS_PER_SECOND * SECONDS_PER_MINUTE)).toInt()
    val secondsInTenths = totalTenths % (TENTHS_PER_SECOND * SECONDS_PER_MINUTE)
    val decimalMark = stringResource(Res.string.decimal_separator)
    val secondsNumber = "${secondsInTenths / TENTHS_PER_SECOND}$decimalMark${secondsInTenths % TENTHS_PER_SECOND}"
    val seconds = stringResource(Res.string.a11y_time_seconds, secondsNumber)
    if (minutes == 0) return seconds
    val minutesText = pluralStringResource(Res.plurals.a11y_minutes, minutes, minutes)
    return stringResource(Res.string.a11y_time_minutes_seconds, minutesText, seconds)
}

/** "3.2 seconds of 8 seconds": where the playhead is, out of the whole project. */
@Composable
fun spokenTimeOf(timeUs: Long, totalUs: Long): String = stringResource(Res.string.a11y_time_of, spokenTime(timeUs), spokenTime(totalUs))
