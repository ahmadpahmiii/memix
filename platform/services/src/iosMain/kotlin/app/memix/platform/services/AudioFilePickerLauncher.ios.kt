package app.memix.platform.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.memix.core.model.project.MediaOrigin

// TODO(P7-08): UIDocumentPickerViewController for audio. Until then the app shows "Can't open your files" on iOS.
@Composable
actual fun rememberAudioFilePickerLauncher(onPicked: (MediaOrigin?) -> Unit): AudioFilePickerLauncher =
    remember {
        object : AudioFilePickerLauncher {
            override fun launch(): Boolean = false
        }
    }
