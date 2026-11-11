package app.memix.feature.sounds

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Tells the Sounds screen that the user moved to another bottom-nav item or opened Create (spec P3-02 → Entry and exit:
 * the search text clears and a playing preview stops). The app root sends it from its tab switch. It is not sent from
 * the screen leaving composition, because a rotation does that too and must keep both.
 */
class SoundsTabExit {
    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val left: SharedFlow<Unit> = events

    fun notifyLeft() {
        events.tryEmit(Unit)
    }
}
