package app.memix.core.domain.project

import kotlin.time.Clock

private const val MICROS_PER_SECOND = 1_000_000L
private const val NANOS_PER_MICRO = 1_000

/** The project model keeps every time in microseconds, wall-clock timestamps included. */
internal fun Clock.nowEpochUs(): Long {
    val now = now()
    return now.epochSeconds * MICROS_PER_SECOND + now.nanosecondsOfSecond / NANOS_PER_MICRO
}
