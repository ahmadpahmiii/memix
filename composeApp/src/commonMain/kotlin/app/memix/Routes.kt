package app.memix

import app.memix.core.domain.sound.StartingSound
import kotlinx.serialization.Serializable

/** The tabs, the bottom nav and the sheets over them; the app's start destination. */
@Serializable data object MainRoute

// Tabs, inside MainRoute's own navigation.
@Serializable data object HomeRoute
@Serializable data object TemplatesRoute
@Serializable data object SoundsRoute
@Serializable data object DraftsRoute
// Full screens over MainRoute.
/**
 * A saved video draft; P1-02 opens it right after its first import. [madeFromPhoto]: the draft was just made by Make it a video
 * meme (P2-10), so the Meme sounds sheet opens once the editor has arrived. [startingSound]: the draft was just made by the Sounds
 * tab's Use (P3-15); once the editor has arrived, the sound bonks (or a toast says it did not come along).
 */
@Serializable data class VideoEditorRoute(val projectId: String, val madeFromPhoto: Boolean = false, val startingSound: StartingSound = StartingSound.NONE)
/**
 * A photo draft (P2-01). [blank] opens a blank canvas that only exists in memory: it is saved at its first committed step
 * that leaves a layer, and [projectId] is the id it is saved under.
 */
@Serializable data class PhotoEditorRoute(val projectId: String, val blank: Boolean = false)
@Serializable data object CatalogRoute
/** The Exporting screen and, when the file is saved, the Exported screen (P1-12); pushed over the video editor. */
@Serializable data object ExportRoute
/** The Exported screen after a photo is saved to the gallery (P2-08); pushed over the photo editor. */
@Serializable data object PhotoExportedRoute
/**
 * The WhatsApp sticker packs flow (P2-09): the packs list, the builder and the result, pushed over the photo editor. [hasImage]: the
 * canvas has a photo, which decides the action of the "you need 3 stickers" state.
 */
@Serializable data class StickerPacksRoute(val hasImage: Boolean)
