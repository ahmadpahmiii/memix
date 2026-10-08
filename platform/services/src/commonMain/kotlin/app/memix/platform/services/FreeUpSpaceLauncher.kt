package app.memix.platform.services

import androidx.compose.runtime.Composable

/** Opens the phone's own screen for freeing storage space. */
interface FreeUpSpaceLauncher {
    /**
     * Opens the storage manager, asking it to help the user reach [requestedBytes] of space for the app; the
     * internal storage settings if that screen doesn't exist. Returns false when neither opens.
     */
    fun launch(requestedBytes: Long): Boolean
}

/** Registers the storage screen. The app checks the space again by itself when it comes back to the foreground. */
@Composable
expect fun rememberFreeUpSpaceLauncher(): FreeUpSpaceLauncher
