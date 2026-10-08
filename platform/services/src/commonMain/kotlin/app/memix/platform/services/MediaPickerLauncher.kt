package app.memix.platform.services

import androidx.compose.runtime.Composable
import app.memix.core.model.project.MediaOrigin

/** Opens the system photo picker for videos and photos. */
interface MediaPickerLauncher {
    /**
     * Opens the picker; its result arrives in the `onPicked` given to [rememberMediaPickerLauncher]. A second call
     * while the picker is still opening does nothing, so a double tap can't open two. Returns false when the phone
     * has no app that can pick media.
     */
    fun launch(): Boolean
}

/**
 * Registers the system photo picker: videos and photos, several at once, up to [maxItems] (fewer if the system
 * allows fewer), in the order the user taps them where the picker supports it. It needs no permission.
 *
 * Call it once, unconditionally, at the root of the UI. If Android stops the app while the picker is open, the
 * result still arrives in [onPicked] after the restart, but only when this is composed at the same place again.
 * [onPicked] gets the items in the order the picker returned them; an empty list when nothing was picked. The
 * phone's file chooser stands in on phones without the picker, and it ignores [maxItems], so the list can be longer.
 */
@Composable
expect fun rememberMediaPickerLauncher(maxItems: Int, onPicked: (List<MediaOrigin>) -> Unit): MediaPickerLauncher
