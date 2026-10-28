package app.memix.platform.services

import androidx.compose.runtime.Composable
import app.memix.core.model.project.MediaOrigin

/** What the system photo picker offers. */
enum class PickerMedia {
    /** Videos and photos (the video editor's pickers). */
    IMAGES_AND_VIDEOS,

    /** Photos only (the photo meme start, spec P2-01 → Starting a photo meme); the file chooser fallback filters the same way. */
    IMAGES_ONLY,
}

/** Opens the system photo picker for videos and photos, or photos only. */
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
 * allows fewer), in the order the user taps them where the picker supports it; with [maxItems] 1, the picker for one
 * item (the editor's Replace, a photo meme start). It needs no permission. Pass the same [maxItems] and [media] on every composition.
 *
 * Call it once, unconditionally, at the root of the UI. If Android stops the app while the picker is open, the
 * result still arrives in [onPicked] after the restart, but only when this is composed at the same place again.
 * [onPicked] gets the items in the order the picker returned them; an empty list when nothing was picked. The
 * phone's file chooser stands in on phones without the picker, and it ignores [maxItems], so the list can be longer.
 */
@Composable
expect fun rememberMediaPickerLauncher(maxItems: Int, media: PickerMedia, onPicked: (List<MediaOrigin>) -> Unit): MediaPickerLauncher
