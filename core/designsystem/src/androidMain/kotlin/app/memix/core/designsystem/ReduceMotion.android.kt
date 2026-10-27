package app.memix.core.designsystem

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// "Remove animations" in Android's accessibility settings sets the animator scale to 0.
@Composable
internal actual fun isReduceMotionEnabled(): Boolean =
    Settings.Global.getFloat(LocalContext.current.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
