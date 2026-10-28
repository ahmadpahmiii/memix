package app.memix.core.domain.media

import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.EffectItem
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.StickerItem
import app.memix.core.model.project.StickerLayer
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TextLayer

/**
 * Every media copy the project uses, as relative paths: its clips', sounds' and stickers' on the video timeline, its
 * layers' in a photo scene and the canvas's background picture (P2-12). Split and duplicated clips share one copy, so it's
 * listed once.
 */
internal fun Project.mediaCopyPaths(): Set<String> {
    val paths = HashSet<String>()
    video?.tracks?.forEach { track ->
        track.items.forEach { item ->
            val source = when (item) {
                is MediaClip -> item.source
                is AudioClip -> item.source
                is StickerItem -> item.source
                is TextItem, is EffectItem -> null
            }
            source?.cachedCopyPath?.let(paths::add)
        }
    }
    photo?.layers?.forEach { layer ->
        val source = when (layer) {
            is ImageLayer -> layer.source
            is StickerLayer -> layer.source
            is TextLayer -> null
        }
        source?.cachedCopyPath?.let(paths::add)
    }
    (canvas.background as? CanvasBackground.Image)?.source?.cachedCopyPath?.let(paths::add)
    return paths
}

/**
 * The same project with every media copy path passed through [rewrite]: clips, sounds and stickers on the video
 * timeline, image and sticker layers in a photo scene and the canvas's background picture. Media without a copy is left
 * as it is.
 */
internal fun Project.withMediaCopyPaths(rewrite: (String) -> String): Project {
    fun MediaRef.rewritten(): MediaRef = cachedCopyPath?.let { copy(cachedCopyPath = rewrite(it)) } ?: this
    val rewrittenVideo = video?.let { timeline ->
        timeline.copy(
            tracks = timeline.tracks.map { track ->
                track.copy(
                    items = track.items.map { item ->
                        when (item) {
                            is MediaClip -> item.copy(source = item.source.rewritten())
                            is AudioClip -> item.copy(source = item.source.rewritten())
                            is StickerItem -> item.copy(source = item.source.rewritten())
                            is TextItem, is EffectItem -> item
                        }
                    },
                )
            },
        )
    }
    val rewrittenPhoto = photo?.let { scene ->
        scene.copy(
            layers = scene.layers.map { layer ->
                when (layer) {
                    is ImageLayer -> layer.copy(source = layer.source.rewritten())
                    is StickerLayer -> layer.copy(source = layer.source.rewritten())
                    is TextLayer -> layer
                }
            },
        )
    }
    val rewrittenCanvas = (canvas.background as? CanvasBackground.Image)
        ?.let { canvas.copy(background = it.copy(source = it.source.rewritten())) } ?: canvas
    return copy(video = rewrittenVideo, photo = rewrittenPhoto, canvas = rewrittenCanvas)
}
