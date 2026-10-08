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
@Serializable data object PhotoEditorRoute
@Serializable data object CatalogRoute
