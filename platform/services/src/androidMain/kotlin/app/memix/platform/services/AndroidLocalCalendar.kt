package app.memix.platform.services

import android.text.format.DateFormat
import app.memix.core.domain.drafts.LocalCalendar
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/**
 * The phone's time zone and the user's language (the app's own language when one is set: Android makes it the default
 * locale), read on every call, so a change of either shows at the next list update.
 */
internal class AndroidLocalCalendar : LocalCalendar {
    override fun daysBetween(fromEpochUs: Long, toEpochUs: Long): Int =
        ChronoUnit.DAYS.between(localDateOf(fromEpochUs), localDateOf(toEpochUs)).toInt()

    override fun shortDate(epochUs: Long, includeYear: Boolean): String {
        val locale = Locale.getDefault()
        // The locale's own order and month form for day + short month ("12 Oct", "Oct 12", "12 अक्टू॰").
        val pattern = DateFormat.getBestDateTimePattern(locale, if (includeYear) SKELETON_WITH_YEAR else SKELETON)
        // SimpleDateFormat reads the ICU pattern letters getBestDateTimePattern returns; it uses the phone's time zone.
        return SimpleDateFormat(pattern, locale).format(Date(epochUs.floorDiv(MICROS_PER_MILLI)))
    }

    override fun isSameYear(epochUs: Long, otherEpochUs: Long): Boolean = localDateOf(epochUs).year == localDateOf(otherEpochUs).year

    private fun localDateOf(epochUs: Long): LocalDate =
        Instant.ofEpochSecond(epochUs.floorDiv(MICROS_PER_SECOND)).atZone(ZoneId.systemDefault()).toLocalDate()

    private companion object {
        const val SKELETON = "dMMM"
        const val SKELETON_WITH_YEAR = "dMMMy"
        const val MICROS_PER_SECOND = 1_000_000L
        const val MICROS_PER_MILLI = 1_000L
    }
}
