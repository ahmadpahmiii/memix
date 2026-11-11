package app.memix.feature.sounds

import androidx.compose.runtime.Stable

/** Where the playing sound is, in microseconds. Read every frame in the draw step, never in the state. */
@Stable
fun interface PlaybackClock {
    fun positionUs(): Long
}

/** How much of a sound of [durationUs] has played, 0 to 1. */
internal fun playedFraction(positionUs: Long, durationUs: Long): Float =
    if (durationUs <= 0L) 0f else (positionUs.toFloat() / durationUs).coerceIn(0f, 1f)
