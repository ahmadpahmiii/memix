package app.memix.core.data.catalog

import kotlin.time.Instant

private const val SECONDS_PER_DAY = 86_400L
private const val DAYS_FROM_0000_TO_1970 = 719_468L
private const val DAYS_PER_ERA = 146_097L

/**
 * The first day of the month of [this] in UTC, as the catalog's `trending.month` column holds it (`2026-11-01`).
 * Plain calendar arithmetic (days since 1970 to a civil date) so the data layer needs no date library for one value.
 */
internal fun Instant.trendingMonth(): String {
    val daysSinceEpoch = epochSeconds.floorDiv(SECONDS_PER_DAY)
    val shifted = daysSinceEpoch + DAYS_FROM_0000_TO_1970
    val era = shifted.floorDiv(DAYS_PER_ERA)
    val dayOfEra = shifted - era * DAYS_PER_ERA
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthIndex = (5 * dayOfYear + 2) / 153 // 0 is March
    val month = if (monthIndex < 10) monthIndex + 3 else monthIndex - 9
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-01"
}
