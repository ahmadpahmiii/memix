package app.memix.feature.photoeditor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import app.memix.core.model.project.FilterRef
import app.memix.core.model.project.ImageAdjust
import app.memix.core.model.project.ImageEdit
import app.memix.core.model.project.ImageToolCatalog
import app.memix.core.model.project.PixelSize
import app.memix.core.ui.scene.ImageToolRenderer
import app.memix.core.ui.scene.SceneImageSource
import app.memix.core.ui.scene.SceneImageUse
import app.memix.core.ui.scene.renderEditedPicture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The Filters tab's 21 tiles (spec docs/ux/specs/P2-04-image-tools.md → Filters tab): the layer's picture, its geometry edits included
 * and its other color edits not, with each filter on at 100%. Made on demand from a small decode of the picture (never the original at
 * full size), one tile at a time in strip order so the first visible ones come first, and kept for the panel's visit.
 * A tile not made yet is absent: the strip shows a `surface-raised` square.
 */
@Stable
internal class FilterThumbnails(private val source: SceneImageSource, private val tools: ImageToolRenderer, private val scope: CoroutineScope) {
    private val made = mutableStateMapOf<String, ImageBitmap>()
    private var job: Job? = null
    private var shownFor: Pair<String, ImageEdit>? = null

    /** The tile of filter [id], or of None for null. */
    fun tileOf(id: String?): ImageBitmap? = made[id ?: NONE_KEY]

    /** Starts making the tiles for [path] of [sourceSize] shaped by [geometry]; a call for the same picture and shape changes nothing. */
    fun show(path: String, sourceSize: PixelSize, geometry: ImageEdit?) {
        val shape = (geometry ?: ImageEdit.NONE).copy(adjust = ImageAdjust(), filter = null, deepFry = 0f)
        if (shownFor == path to shape) return
        shownFor = path to shape
        job?.cancel()
        made.clear()
        job = scope.launch {
            val raw = source.decode(path, RAW_LONG_SIDE_PX, SceneImageUse.OFFSCREEN) ?: return@launch
            val picture = raw.copy(sourceSize)
            for (id in listOf<String?>(null) + ImageToolCatalog.filters.map { it.id }) {
                val tile = renderEditedPicture(picture, shape.copy(filter = id?.let { FilterRef(it) }), TILE_LONG_SIDE_PX, tools) ?: continue
                made[id ?: NONE_KEY] = tile
            }
        }
    }

    /** Lets go of every tile (the panel closed). */
    fun release() {
        job?.cancel()
        job = null
        shownFor = null
        made.clear()
    }

    private fun app.memix.core.ui.scene.SceneBitmap.copy(size: PixelSize) = app.memix.core.ui.scene.SceneBitmap(image, size)

    private companion object {
        const val NONE_KEY = "none"
        const val RAW_LONG_SIDE_PX = 512
        const val TILE_LONG_SIDE_PX = 320
    }
}
