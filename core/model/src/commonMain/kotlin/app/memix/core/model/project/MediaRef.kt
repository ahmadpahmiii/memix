package app.memix.core.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A photo, video or audio file used in a project: where it came from, plus the app's own copy of it,
 * so a draft still opens after the original is moved or deleted from the gallery.
 */
@Serializable
data class MediaRef(
    val origin: MediaOrigin,
    val kind: MediaKind,
    /**
     * The app's copy, relative to the app's files directory. Relative because iOS moves the app's
     * container on every app update, which breaks absolute paths. Null until the copy exists.
     */
    val cachedCopyPath: String? = null,
)

@Serializable
enum class MediaKind {
    @SerialName("video") VIDEO,
    @SerialName("image") IMAGE,
    @SerialName("audio") AUDIO,
}

/** Where a [MediaRef] came from. */
@Serializable
sealed interface MediaOrigin {
    /** A content URI from the Android photo picker or MediaStore. */
    @Serializable
    @SerialName("gallery_uri")
    data class GalleryUri(val uri: String) : MediaOrigin

    /** A `PHAsset.localIdentifier` from the iOS photo library. */
    @Serializable
    @SerialName("photo_asset")
    data class PhotoAsset(val localIdentifier: String) : MediaOrigin

    /** A meme sound or sticker from the catalog, by its catalog id. Bundled starter-pack sounds use the same ids. */
    @Serializable
    @SerialName("catalog_item")
    data class CatalogItem(val itemId: String) : MediaOrigin
}
