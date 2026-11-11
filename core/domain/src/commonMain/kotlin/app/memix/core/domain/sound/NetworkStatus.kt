package app.memix.core.domain.sound

import kotlinx.coroutines.flow.Flow

/** Whether the phone has a connection. Implemented in :platform:services (Android `ConnectivityManager`). */
interface NetworkStatus {
    /**
     * True while the phone has a network that can reach the internet. Emits the current value first, then each change,
     * without repeats. It says what the phone reports, not that a server answers.
     */
    fun observeOnline(): Flow<Boolean>
}
