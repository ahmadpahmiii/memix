package app.memix.core.data

import kotlin.time.Clock

private const val MICROS_PER_SECOND = 1_000_000L
private const val NANOS_PER_MICRO = 1_000

/** The clock's time as microseconds since 1970, the unit of every stored timestamp. */
internal fun Clock.nowEpochUs(): Long {
    val now = now()
    return now.epochSeconds * MICROS_PER_SECOND + now.nanosecondsOfSecond / NANOS_PER_MICRO
}
