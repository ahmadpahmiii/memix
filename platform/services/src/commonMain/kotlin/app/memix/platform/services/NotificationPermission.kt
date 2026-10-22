package app.memix.platform.services

import androidx.compose.runtime.Composable

/**
 * Asks for permission to show notifications with the phone's own dialog (Android 13+; nothing on older Android or on
 * iOS until P7). The export asks once, at the first Export video, and never waits for the answer (spec P1-12 →
 * Notification permission). Call [request] from the screen; [onClosed] runs when the dialog is gone, or at once when
 * there's nothing to ask.
 */
fun interface NotificationPermissionRequest {
    fun request(onClosed: () -> Unit)
}

@Composable
expect fun rememberNotificationPermissionRequest(): NotificationPermissionRequest
