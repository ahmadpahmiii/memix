package app.memix.platform.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// TODO(P7-10): UNUserNotificationCenter, once iOS exports exist (P7-05).
@Composable
actual fun rememberNotificationPermissionRequest(): NotificationPermissionRequest = remember { NotificationPermissionRequest { it() } }
