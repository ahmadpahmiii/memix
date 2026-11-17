package app.memix.feature.videoeditor.transitions

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import app.memix.core.domain.image.TransitionMath
import app.memix.core.model.project.TransitionCatalog
import app.memix.core.ui.scene.ImageToolRenderer
import app.memix.core.ui.scene.SceneBitmap
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Transition strip's tile pictures (spec docs/ux/specs/P4-08-transitions.md → The Transition panel): each transition at 50% progress between the
 * cut's two real frames, drawn by the CPU twin `TransitionMath` that the reference renders use. Made on demand from small copies of the frames,
 * one at a time off the main thread, and kept for the panel's visit. A tile not made yet is absent: the strip shows a plain `surface-raised`
 * square, and a tap works all the same. [outgoingTile] is the first frame alone, the None tile's picture.
 */
@Stable
class TransitionTiles(
    private val tools: ImageToolRenderer,
    private val scope: CoroutineScope,
    /** Where the pictures are drawn (CPU work, off the main thread). */
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val made = mutableStateMapOf<String, ImageBitmap>()
    private var job: Job? = null
    private var shownKey: Any? = null

    /** The tile of transition [id], or null while it isn't made. */
    fun tileOf(id: String): ImageBitmap? = made[id]

    /** The first frame alone (a transition at progress 0), for the None tile. */
    fun noneTile(): ImageBitmap? = made[NONE_KEY]

    /** Starts making the tiles from the two frames (kept under [key]); a call for the same pictures changes nothing. */
    fun show(key: Any, outgoing: SceneBitmap, incoming: SceneBitmap) {
        if (shownKey == key && job?.isActive == true) return
        if (shownKey != key) made.clear()
        shownKey = key
        job?.cancel()
        job = scope.launch {
            if (!tools.isSupported) return@launch
            val (width, height) = tileSize(outgoing)
            val first = withContext(workDispatcher) { pixelsOf(outgoing, width, height) }
            val second = withContext(workDispatcher) { pixelsOf(incoming, width, height) }
            tools.bitmapOf(dimmed(first), width, height)?.let { made[NONE_KEY] = it }
            for (def in TransitionCatalog.all) {
                val pixels = withContext(workDispatcher) { TransitionMath.render(def.id, first, second, width, height, progressOf(def.id), def.defaultDurationUs) }
                tools.bitmapOf(pixels, width, height)?.let { made[def.id] = it }
            }
        }
    }

    /** Lets go of every tile (the panel closed). */
    fun release() {
        job?.cancel()
        job = null
        shownKey = null
        made.clear()
    }

    // 50% for every tile but the two dips, which are fully black or white there and would show no picture: they are drawn at 30%.
    private fun progressOf(id: String): Float = if (id == "dip_black" || id == "dip_white") DIP_TILE_PROGRESS else TILE_PROGRESS

    // The tiles are 72 dp squares cropped from this picture, made at about 3x that on its short side.
    private fun tileSize(picture: SceneBitmap): Pair<Int, Int> {
        val scale = TILE_SHORT_SIDE_PX.toFloat() / minOf(picture.image.width, picture.image.height)
        return max(1, (picture.image.width * scale).roundToInt()) to max(1, (picture.image.height * scale).roundToInt())
    }

    // The picture scaled to cover [width] x [height], the overflow cropped (the clip's own Fill; a Fit clip's bars are not drawn in a tile).
    private fun pixelsOf(picture: SceneBitmap, width: Int, height: Int): IntArray {
        val small = ImageBitmap(width, height)
        val cover = max(width.toFloat() / picture.image.width, height.toFloat() / picture.image.height)
        val drawnWidth = (picture.image.width * cover).roundToInt()
        val drawnHeight = (picture.image.height * cover).roundToInt()
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(small), Size(width.toFloat(), height.toFloat())) {
            drawImage(picture.image, dstOffset = androidx.compose.ui.unit.IntOffset((width - drawnWidth) / 2, (height - drawnHeight) / 2), dstSize = IntSize(drawnWidth, drawnHeight))
        }
        return IntArray(width * height).also { small.readPixels(it) }
    }

    // The None tile: the first frame at 40% (spec: "the clip's first frame at 40%").
    private fun dimmed(pixels: IntArray): IntArray = IntArray(pixels.size) { i ->
        val p = pixels[i]
        val r = (((p shr 16) and 0xFF) * NONE_LEVEL).roundToInt()
        val g = (((p shr 8) and 0xFF) * NONE_LEVEL).roundToInt()
        val b = ((p and 0xFF) * NONE_LEVEL).roundToInt()
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private companion object {
        const val TILE_PROGRESS = 0.5f
        const val DIP_TILE_PROGRESS = 0.3f
        const val TILE_SHORT_SIDE_PX = 216
        const val NONE_LEVEL = 0.4f
        const val NONE_KEY = "none"
    }
}
