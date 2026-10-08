package app.memix.engine.video

import android.net.Uri
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import java.io.File

/** What [MediaSourceResolver.resolve] returns. */
internal sealed interface ResolvedSources {
    /** A playable URI for every media file the plan uses. */
    data class Found(val uriByMedia: Map<MediaRef, Uri>) : ResolvedSources

    /** The first clip whose media isn't on the phone. */
    data class Missing(val itemId: String) : ResolvedSources
}

/** Finds the file each clip of a [CompositionPlan] plays from. Reads the disk, so call it off the main thread. */
internal class MediaSourceResolver(private val filesDir: File) {

    fun resolve(plan: CompositionPlan): ResolvedSources {
        val uriByMedia = mutableMapOf<MediaRef, Uri>()
        for ((itemId, media) in mediaUsedBy(plan)) {
            if (media in uriByMedia) continue
            uriByMedia[media] = uriFor(media) ?: return ResolvedSources.Missing(itemId)
        }
        return ResolvedSources.Found(uriByMedia)
    }

    private fun mediaUsedBy(plan: CompositionPlan): List<Pair<String, MediaRef>> {
        val mainMedia = plan.mainVideo.filterIsInstance<VisualClip>().map { it.itemId to it.source }
        val laneMedia = plan.audioLanes.flatMap { lane -> lane.segments.filterIsInstance<SoundClip>().map { it.itemId to it.source } }
        return mainMedia + laneMedia
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
