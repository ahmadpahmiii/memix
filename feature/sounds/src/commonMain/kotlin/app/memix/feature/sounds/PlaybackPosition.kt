package app.memix.feature.sounds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import app.memix.core.designsystem.MemixTheme
import kotlinx.coroutines.delay

/**
 * Where the active sound is, in microseconds, refreshed 30 times a second while it plays and 4 times a second with
 * reduced motion (spec P3-03 → Progress). Read it only in draw code or a derived value, so a tick redraws the waveform
 * and recomposes nothing. It holds the position while paused and reads 0 when nothing is active; a new [activeId]
 * starts from the player's own 0.
 */
@Composable
internal fun rememberPlaybackPosition(clock: PlaybackClock, activeId: String?, isPlaying: Boolean): State<Long> {
    val position = remember { mutableLongStateOf(0L) }
    val tickMillis = if (MemixTheme.reduceMotion) REDUCED_TICK_MILLIS else TICK_MILLIS
    LaunchedEffect(clock, activeId, isPlaying, tickMillis) {
        position.longValue = clock.positionUs()
        while (isPlaying) {
            delay(tickMillis)
            position.longValue = clock.positionUs()
        }
    }
    return position
}

private const val TICK_MILLIS = 33L
private const val REDUCED_TICK_MILLIS = 250L
