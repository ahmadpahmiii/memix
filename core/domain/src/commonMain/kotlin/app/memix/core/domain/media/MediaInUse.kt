package app.memix.core.domain.media

import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.EffectItem
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.Project
import app.memix.core.model.project.StickerItem
import app.memix.core.model.project.StickerLayer
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TextLayer

/**
 * Every media copy the project uses, as relative paths: its clips', sounds' and stickers' on the video timeline, and
 * its layers' in a photo scene. Split and duplicated clips share one copy, so it's listed once.
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
    return paths
}
