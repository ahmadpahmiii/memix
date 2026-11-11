package app.memix.feature.sounds

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Two small things the list does with the user's own scrolling (spec P3-02 → Interactions): dragging down past the top
 * by [refreshDistancePx] asks for a refresh (the spec draws no indicator: cached rows stay put and update in place),
 * and any drag tells [onUserScroll], which hides the keyboard. Consumes nothing.
 */
internal class PullToRefresh(
    private val refreshDistancePx: Float,
    private val onRefresh: () -> Unit,
    private val onUserScroll: () -> Unit,
) : NestedScrollConnection {
    private var pulledPx = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) onUserScroll()
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        // What the list could not scroll any further up is the pull past its top.
        pulledPx = if (available.y > 0f) pulledPx + available.y else 0f
        if (pulledPx >= refreshDistancePx) {
            pulledPx = 0f
            onRefresh()
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        pulledPx = 0f
        return Velocity.Zero
    }
}
