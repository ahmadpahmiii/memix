package app.memix.engine.video

import android.net.Uri
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import java.io.File

/** What [MediaSourceResolver.resolve] found. */
internal data class ResolvedSources(
    /** A playable URI for each media file of the plan that is on the phone. */
    val uriByMedia: Map<MediaRef, Uri>,
    /** The items whose media file isn't on the phone, in plan order. */
    val missingItemIds: List<String>,
    /** The plan's background picture, when it has one and it is on the phone; null otherwise (the bars draw black). */
    val backgroundFile: File?,
)

/** Finds the file each clip of a [CompositionPlan] plays from. Reads the disk, so call it off the main thread. */
internal class MediaSourceResolver(private val filesDir: File) {

    fun resolve(plan: CompositionPlan): ResolvedSources {
        val uriByMedia = mutableMapOf<MediaRef, Uri>()
        val missingItemIds = mutableListOf<String>()
        for ((itemId, media) in plan.mediaItems) {
            if (media in uriByMedia) continue
            val uri = uriFor(media)
            if (uri != null) uriByMedia[media] = uri else missingItemIds += itemId
        }
        return ResolvedSources(uriByMedia, missingItemIds, backgroundFileOf(plan))
    }

    /** The background picture's file when the plan has one and it is on the phone; null otherwise (the bars draw black). */
    private fun backgroundFileOf(plan: CompositionPlan): File? {
        val picture = (plan.background as? PlanBackground.Image)?.source?.cachedCopyPath ?: return null
        return File(filesDir, picture).takeIf { it.isFile }
    }

    // The app's own copy wins: it survives the original being moved or deleted, and a photo picker
    // grant doesn't outlive the process. Without a copy, only a gallery URI can still be read here.
    private fun uriFor(media: MediaRef): Uri? {
        val copyPath = media.cachedCopyPath
        if (copyPath != null) return File(filesDir, copyPath).takeIf { it.isFile }?.let(Uri::fromFile)
        return when (val origin = media.origin) {
            is MediaOrigin.GalleryUri -> Uri.parse(origin.uri)
            // An iOS photo library id, or a catalog item that hasn't been downloaded yet.
            is MediaOrigin.PhotoAsset, is MediaOrigin.CatalogItem -> null
        }
    }
}
