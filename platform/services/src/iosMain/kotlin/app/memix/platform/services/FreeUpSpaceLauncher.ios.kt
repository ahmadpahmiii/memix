package app.memix.platform.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// iOS has no screen an app can open to free storage, so the "Free up space" button goes away (spec P1-02).
@Composable
actual fun rememberFreeUpSpaceLauncher(): FreeUpSpaceLauncher =
    remember {
        object : FreeUpSpaceLauncher {
            override fun launch(requestedBytes: Long): Boolean = false
        }
    }
