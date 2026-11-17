package app.memix.feature.videoeditor.effects

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import app.memix.core.domain.image.EffectMath
import app.memix.core.model.project.EffectCatalog
import app.memix.core.model.project.EffectDef
import app.memix.core.model.project.EffectTab
import app.memix.core.ui.scene.ImageToolRenderer
import app.memix.core.ui.scene.SceneBitmap
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Effects sheet's tile pictures (spec docs/ux/specs/P4-06-video-effects.md → The grid): the user's frame with each effect at its peak (100%
 * intensity, at the effect's reference time), drawn by the CPU twin `EffectMath` that the reference renders use. Made on demand from a small
 * copy of the frame, the open tab's effects first, one at a time off the main thread, and kept for the sheet's visit. A tile not made yet is
 * absent: the grid shows a plain `surface-raised` square, and a tap works all the same.
 */
@Stable
class EffectTiles(private val tools: ImageToolRenderer, private val scope: CoroutineScope) {
    private val made = mutableStateMapOf<String, ImageBitmap>()
    private var job: Job? = null
    private var shownKey: Any? = null

    /** The tile of effect [id], or null while it isn't made. */
    fun tileOf(id: String): ImageBitmap? = made[id]

    /** Starts making the tiles from [picture] (kept under [key]), [firstTab]'s first; a call for the same picture and tab changes nothing but the order. */
    fun show(key: Any, picture: SceneBitmap, firstTab: EffectTab) {
        if (shownKey == key && job?.isActive == true) return
        if (shownKey != key) made.clear()
        shownKey = key
        job?.cancel()
        job = scope.launch {
            val ordered = EffectCatalog.all.filter { it.isBuilt }.sortedBy { if (it.tab == firstTab) 0 else 1 }
            for (def in ordered) {
                if (made.containsKey(def.id)) continue
                val tile = withContext(Dispatchers.Default) { tileOf(picture, def) } ?: continue
                made[def.id] = tile
            }
        }
    }

    /** Lets go of every tile (the sheet closed). */
    fun release() {
        job?.cancel()
        job = null
        shownKey = null
        made.clear()
    }

    private suspend fun tileOf(picture: SceneBitmap, def: EffectDef): ImageBitmap? {
        if (!tools.isSupported) return null
        val scale = TILE_LONG_SIDE_PX.toFloat() / max(picture.image.width, picture.image.height)
        val width = max(1, (picture.image.width * scale).roundToInt())
        val height = max(1, (picture.image.height * scale).roundToInt())
        val small = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(small), Size(width.toFloat(), height.toFloat())) {
            drawImage(picture.image, dstSize = IntSize(width, height))
        }
        val pixels = IntArray(width * height)
        small.readPixels(pixels)
        EffectMath.apply(def.id, 1f, def.refTimeUs, pixels, width, height)
        return tools.bitmapOf(pixels, width, height)
    }

    private companion object {
        /** The tiles are 104 dp wide; this is sharp on the densest phones and cheap to make. */
        const val TILE_LONG_SIDE_PX = 288
    }
}
