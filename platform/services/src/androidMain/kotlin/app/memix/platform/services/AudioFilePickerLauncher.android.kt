package app.memix.platform.services

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import app.memix.core.model.project.MediaOrigin

/*
 * ACTION_OPEN_DOCUMENT needs no permission: the user's choice is the grant, and it lasts while the app runs, which is as long as
 * the import needs it (the file is converted into the app's own folder at once). Nothing is asked to be kept.
 */
@Composable
actual fun rememberAudioFilePickerLauncher(onPicked: (MediaOrigin?) -> Unit): AudioFilePickerLauncher {
    // Saved with the activity, so a tap while the picker is still opening is ignored even across a rotation.
    val awaitingResult = rememberSaveable { mutableStateOf(false) }
    // The launcher re-registers whenever the contract instance changes, so it must stay the same object.
    val contract = remember { OpenDocument() }
    val activityLauncher = rememberLauncherForActivityResult(contract) { uri ->
        awaitingResult.value = false
        onPicked(uri?.let { MediaOrigin.GalleryUri(it.toString()) })
    }
    return remember(activityLauncher) {
        object : AudioFilePickerLauncher {
            override fun launch(): Boolean {
                if (awaitingResult.value) return true
                return try {
                    activityLauncher.launch(AUDIO_TYPES)
                    awaitingResult.value = true
                    true
                } catch (e: ActivityNotFoundException) {
                    false
                }
            }
        }
    }
}

private val AUDIO_TYPES = arrayOf("audio/*")
