package app.memix

import kotlinx.serialization.Serializable

/** The tabs, the bottom nav and the sheets over them; the app's start destination. */
@Serializable data object MainRoute

// Tabs, inside MainRoute's own navigation.
@Serializable data object HomeRoute
@Serializable data object TemplatesRoute
@Serializable data object SoundsRoute
@Serializable data object DraftsRoute
// Full screens over MainRoute.
/** A saved video draft; P1-02 opens it right after its first import. */
@Serializable data class VideoEditorRoute(val projectId: String)
/**
 * A photo draft (P2-01). [blank] opens a blank canvas that only exists in memory: it is saved at its first committed step
 * that leaves a layer, and [projectId] is the id it is saved under.
 */
@Serializable data class PhotoEditorRoute(val projectId: String, val blank: Boolean = false)
@Serializable data object CatalogRoute
/** The Exporting screen and, when the file is saved, the Exported screen (P1-12); pushed over the video editor. */
@Serializable data object ExportRoute
