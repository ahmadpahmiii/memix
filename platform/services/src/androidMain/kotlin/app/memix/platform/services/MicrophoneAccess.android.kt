package app.memix.platform.services

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberMicrophoneAccess(): MicrophoneAccess {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending[0]?.invoke(granted)
        pending[0] = null
    }
    return remember(context, launcher) {
        object : MicrophoneAccess {
            override fun isGranted() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

            override fun request(onAnswer: (Boolean) -> Unit) {
                if (isGranted()) return onAnswer(true)
                pending[0] = onAnswer
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }

            override fun openSettings() = openAppSettings(context)
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Every phone has a Settings app; if one doesn't, the explanation stays and nothing happens.
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        return
    }
}

@Composable
actual fun KeepScreenOn(keepOn: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, keepOn) {
        if (keepOn) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
