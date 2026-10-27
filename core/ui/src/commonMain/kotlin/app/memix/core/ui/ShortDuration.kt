package app.memix.core.ui

import androidx.compose.runtime.Composable
import memix.core.ui.generated.resources.Res
import memix.core.ui.generated.resources.duration_minutes
import memix.core.ui.generated.resources.duration_seconds
import org.jetbrains.compose.resources.stringResource

/**
 * A rough duration for "about 20 s" and "About 12 s left" (spec P1-12 → Estimate): the nearest 5 s under a minute
 * (never under 5 s), then whole minutes. Unit symbols are string resources.
 */
@Composable
fun shortDurationText(seconds: Long): String =
    if (seconds < SECONDS_PER_MINUTE - FIVE_SECONDS / 2) {
        stringResource(Res.string.duration_seconds, roundedToFive(seconds))
    } else {
        stringResource(Res.string.duration_minutes, ((seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE).coerceAtLeast(1).toInt())
    }

private fun roundedToFive(seconds: Long): Int = maxOf(FIVE_SECONDS, ((seconds + FIVE_SECONDS / 2) / FIVE_SECONDS * FIVE_SECONDS).toInt())

private const val SECONDS_PER_MINUTE = 60L
private const val FIVE_SECONDS = 5
