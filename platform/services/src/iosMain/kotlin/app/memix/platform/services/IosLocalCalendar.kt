package app.memix.platform.services

import app.memix.core.domain.drafts.LocalCalendar

// TODO(P7-08): NSCalendar (startOfDayForDate, components) and NSDateFormatter.setLocalizedDateFormatFromTemplate("dMMM").
// Until then iOS counts days in UTC and writes dates as year-month-day; nothing on iOS shows drafts before P7.
internal class IosLocalCalendar : LocalCalendar {
    override fun daysBetween(fromEpochUs: Long, toEpochUs: Long): Int = (dayOf(toEpochUs) - dayOf(fromEpochUs)).toInt()

    override fun shortDate(epochUs: Long, includeYear: Boolean): String {
        val (year, month, day) = civilDateOf(dayOf(epochUs))
        val monthDay = "${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        return if (includeYear) "$year-$monthDay" else monthDay
    }

    override fun isSameYear(epochUs: Long, otherEpochUs: Long): Boolean =
        civilDateOf(dayOf(epochUs)).first == civilDateOf(dayOf(otherEpochUs)).first

    private fun dayOf(epochUs: Long): Long = epochUs.floorDiv(MICROS_PER_DAY)

    private companion object {
        const val MICROS_PER_DAY = 86_400_000_000L
    }
}

// Days since 1970-01-01 to (year, month, day) in the proleptic Gregorian calendar (H. Hinnant's civil_from_days).
private fun civilDateOf(daysSinceEpoch: Long): Triple<Long, Int, Int> {
    val shifted = daysSinceEpoch + 719_468
    val era = (if (shifted >= 0) shifted else shifted - 146_096) / 146_097
    val dayOfEra = shifted - era * 146_097
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthIndex = (5 * dayOfYear + 2) / 153
    val day = (dayOfYear - (153 * monthIndex + 2) / 5 + 1).toInt()
    val month = (if (monthIndex < 10) monthIndex + 3 else monthIndex - 9).toInt()
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
    return Triple(year, month, day)
}
