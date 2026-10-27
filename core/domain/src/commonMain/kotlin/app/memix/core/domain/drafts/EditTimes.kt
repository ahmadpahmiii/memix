package app.memix.core.domain.drafts

import app.memix.core.domain.project.nowEpochUs
import kotlin.time.Clock

/**
 * The phone's calendar and date format, for the drafts list's edit times. Implemented in :platform:services. Fast
 * enough for the main thread (no I/O).
 */
interface LocalCalendar {
    /** Calendar days from [fromEpochUs] to [toEpochUs] in the phone's time zone: 0 on the same day, 1 for yesterday. */
    fun daysBetween(fromEpochUs: Long, toEpochUs: Long): Int

    /** A short date in the user's language and the phone's time zone ("12 Oct"), with the year when [includeYear]. */
    fun shortDate(epochUs: Long, includeYear: Boolean): String

    /** Whether both times fall in the same calendar year. */
    fun isSameYear(epochUs: Long, otherEpochUs: Long): Boolean
}

/** How long ago a draft was edited, in the drafts list's words (spec P1-14 → Copy). */
sealed interface EditedTime {
    /** Under a minute ago (or a clock that went back). */
    data object JustNow : EditedTime
    data class MinutesAgo(val minutes: Int) : EditedTime
    /** Under 24 hours ago. */
    data class HoursAgo(val hours: Int) : EditedTime
    /** The previous calendar day, 24 hours or more ago. */
    data object Yesterday : EditedTime
    /** 2 to 6 calendar days ago. */
    data class DaysAgo(val days: Int) : EditedTime
    /** Longer ago: a short date in the user's language, with the year if it isn't this year. */
    data class OnDate(val date: String) : EditedTime
}

/** Turns edit times into [EditedTime]s against one "now", so every row of one list update agrees. */
class DescribeEditTimeUseCase(private val calendar: LocalCalendar, private val clock: Clock) {
    fun nowEpochUs(): Long = clock.nowEpochUs()

    operator fun invoke(editedAtEpochUs: Long, nowEpochUs: Long): EditedTime {
        val agoUs = nowEpochUs - editedAtEpochUs
        if (agoUs < MICROS_PER_HOUR * HOURS_PER_DAY) return withinADay(agoUs)
        return when (val days = calendar.daysBetween(editedAtEpochUs, nowEpochUs)) {
            in 0..1 -> EditedTime.Yesterday
            in 2..LAST_DAYS_AGO -> EditedTime.DaysAgo(days)
            else -> EditedTime.OnDate(calendar.shortDate(editedAtEpochUs, includeYear = !calendar.isSameYear(editedAtEpochUs, nowEpochUs)))
        }
    }

    private fun withinADay(agoUs: Long): EditedTime = when {
        agoUs < MICROS_PER_MINUTE -> EditedTime.JustNow
        agoUs < MICROS_PER_HOUR -> EditedTime.MinutesAgo((agoUs / MICROS_PER_MINUTE).toInt())
        else -> EditedTime.HoursAgo((agoUs / MICROS_PER_HOUR).toInt())
    }

    private companion object {
        const val MICROS_PER_MINUTE = 60_000_000L
        const val MICROS_PER_HOUR = 60 * MICROS_PER_MINUTE
        const val HOURS_PER_DAY = 24
        const val LAST_DAYS_AGO = 6
    }
}
