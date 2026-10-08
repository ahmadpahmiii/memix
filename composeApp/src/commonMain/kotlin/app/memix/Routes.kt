package app.memix

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object TemplatesRoute
@Serializable data object SoundsRoute
@Serializable data object DraftsRoute
/** A saved video draft; P1-02 opens it right after its first import. */
@Serializable data class VideoEditorRoute(val projectId: String)
@Serializable data object PhotoEditorRoute
@Serializable data object CatalogRoute
