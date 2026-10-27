package app.memix.platform.services

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
actual fun LockPortraitOnPhones() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        if (activity.resources.configuration.smallestScreenWidthDp < LARGE_SCREEN_MIN_WIDTH_DP) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            // A rotation recreates the activity and runs this too; the lock must survive that. Leaving the screen
            // gives the orientation back to the system (the rest of the app doesn't lock it).
            if (!activity.isChangingConfigurations) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

// Android's own line between phones and large screens (smallest width), the one its orientation rules use.
private const val LARGE_SCREEN_MIN_WIDTH_DP = 600
