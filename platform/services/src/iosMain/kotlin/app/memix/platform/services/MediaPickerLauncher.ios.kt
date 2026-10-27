package app.memix.platform.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.memix.core.model.project.MediaOrigin

// TODO(P7-08): PHPickerViewController. Until then the app shows "Can't open your gallery" on iOS.
@Composable
actual fun rememberMediaPickerLauncher(maxItems: Int, onPicked: (List<MediaOrigin>) -> Unit): MediaPickerLauncher =
    remember {
        object : MediaPickerLauncher {
            override fun launch(): Boolean = false
        }
    }
