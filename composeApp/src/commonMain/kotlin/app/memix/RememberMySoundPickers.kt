package app.memix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.memix.core.domain.mysound.MySoundSource
import app.memix.core.model.project.MediaOrigin
import app.memix.feature.sounds.MySoundPickers
import app.memix.platform.services.PickerMedia
import app.memix.platform.services.rememberAudioFilePickerLauncher
import app.memix.platform.services.rememberMediaPickerLauncher

/**
 * My sounds' two system pickers (P3-07) as the Sounds feature uses them: the document picker for one audio file and the photo
 * picker for one video, neither needing a permission. [onPicked] gets the source and the pick, or null when nothing was picked.
 * Call it once, unconditionally, at the same place in the UI every time: a result after Android restarted the app behind the
 * picker reaches only a picker registered there.
 */
@Composable
internal fun rememberMySoundPickers(onPicked: (MySoundSource, MediaOrigin?) -> Unit): MySoundPickers {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val filePicker = rememberAudioFilePickerLauncher { origin -> currentOnPicked(MySoundSource.FILE, origin) }
    val videoPicker = rememberMediaPickerLauncher(maxItems = 1, PickerMedia.VIDEOS_ONLY) { picked -> currentOnPicked(MySoundSource.VIDEO, picked.firstOrNull()) }
    return remember(filePicker, videoPicker) { MySoundPickers(openFile = filePicker::launch, openVideo = videoPicker::launch) }
}
