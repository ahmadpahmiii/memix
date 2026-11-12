package app.memix.platform.services

import androidx.compose.runtime.Composable
import app.memix.core.model.project.MediaOrigin

/** Opens the system document picker for one audio file (My sounds' "Import audio", P3-07). */
interface AudioFilePickerLauncher {
    /**
     * Opens the picker; its result arrives in the `onPicked` given to [rememberAudioFilePickerLauncher]. A second call while
     * the picker is still opening does nothing, so a double tap can't open two. Returns false when the phone has no app that
     * can pick files.
     */
    fun launch(): Boolean
}

/**
 * Registers the system document picker: audio files only, one file, no permission (Android's `ACTION_OPEN_DOCUMENT`).
 * [onPicked] gets the file, or null when nothing was picked. Call it once, unconditionally, at the same place in the UI every
 * time: if Android stops the app while the picker is open, the result arrives after the restart only there.
 */
@Composable
expect fun rememberAudioFilePickerLauncher(onPicked: (MediaOrigin?) -> Unit): AudioFilePickerLauncher
