package app.memix.feature.photoeditor

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import app.memix.core.designsystem.MemixMotion
import app.memix.core.model.project.PhotoCatalog
import app.memix.core.ui.scene.SceneBitmap
import app.memix.core.ui.scene.SceneImageSource
import app.memix.core.ui.scene.SceneImageUse
import app.memix.core.ui.scene.SceneImages
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The pictures of the open scene, decoded once per file at the size they are drawn at (spec P2-01 → Performance): one
 * bitmap per file whatever the number of layers using it, never above the source's own size or
 * [PhotoCatalog.MAX_DECODE_PX]. [ensure] runs when the committed scene changes (a gesture's release, an undo), never
 * during a drag, and decodes again only when the size needed moved by 25% or more. A panel opening scales the bitmaps
 * that are there; it never decodes.
 *
 * What the scene draws reads [imageOf] and [appearanceOf] in the draw phase, so a picture arriving redraws only the
 * scene. Lives with the editor screen: dropping it lets go of every bitmap.
 */
@Stable
internal class SceneBitmaps(private val source: SceneImageSource, private val scope: CoroutineScope) : SceneImages {
    private val decoded = mutableStateMapOf<String, SceneBitmap>()
    private val appearance = mutableStateMapOf<String, Float>()
    private val decodedLongSide = HashMap<String, Int>()
    private val jobs = HashMap<String, Job>()
    private val failed = HashSet<String>()

    override fun imageOf(path: String): SceneBitmap? = decoded[path]

    // A picture that hasn't been through ensure (or is faded in already) counts as fully there.
    override fun appearanceOf(path: String): Float = appearance[path] ?: 1f

    /**
     * Makes sure every file in [neededLongSides] (path to the long side in pixels its biggest layer is drawn at) has a
     * picture of about that size. [onFailed] gets the files that couldn't be read, once each.
     */
    fun ensure(neededLongSides: Map<String, Int>, onFailed: (Set<String>) -> Unit) {
        for ((path, needed) in neededLongSides) {
            if (path in failed) continue
            val have = decodedLongSide[path]
            val wanted = needed.coerceIn(MIN_DECODE_PX, PhotoCatalog.MAX_DECODE_PX)
            if (have != null && wanted <= have * GROW_LIMIT && wanted >= have * SHRINK_LIMIT) continue
            jobs.remove(path)?.cancel()
            jobs[path] = scope.launch { decode(path, wanted, onFailed) }
        }
    }

    /** Lets go of every bitmap; the editor is closing. */
    fun release() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        decoded.clear()
        appearance.clear()
        decodedLongSide.clear()
    }

    private suspend fun decode(path: String, longSide: Int, onFailed: (Set<String>) -> Unit) {
        val bitmap = source.decode(path, longSide, SceneImageUse.SCREEN)
        if (bitmap == null) {
            failed += path
            decoded.remove(path)
            onFailed(setOf(path))
            return
        }
        val firstTime = path !in decoded
        decodedLongSide[path] = min(longSide, max(bitmap.image.width, bitmap.image.height))
        decoded[path] = bitmap
        if (firstTime) fadeIn(path)
    }

    // Each picture fades in over 120 ms when it is decoded (spec → Before the first draw): no spinner, nothing loops.
    private suspend fun fadeIn(path: String) {
        appearance[path] = 0f
        animate(0f, 1f, animationSpec = tween(MemixMotion.durationPress)) { value, _ -> appearance[path] = value }
        appearance.remove(path)
    }

    private companion object {
        const val MIN_DECODE_PX = 64
        const val GROW_LIMIT = 1.25f
        const val SHRINK_LIMIT = 0.8f
    }
}

/**
 * The long side in pixels each layer's file is drawn at on a frame [frameWidthPx] wide, the biggest per file: the picture
 * fitted on the canvas, times the layer's scale, times how much the frame shrinks the canvas, never above the source's own
 * size. Layers whose box isn't known (no measured picture) are left out.
 */
internal fun neededLongSides(layers: List<SceneLayerUi>, canvasWidthPx: Int, frameWidthPx: Float): Map<String, Int> {
    if (canvasWidthPx <= 0 || frameWidthPx <= 0f) return emptyMap()
    val pixelScale = frameWidthPx / canvasWidthPx
    val needed = HashMap<String, Int>()
    for (layer in layers) {
        val path = layer.path ?: continue
        val drawn = max(layer.boxWidthPx, layer.boxHeightPx) * layer.transform.scale * pixelScale
        val capped = min(drawn.roundToInt(), layer.pictureLongSidePx.takeIf { it > 0 } ?: Int.MAX_VALUE)
        needed[path] = max(needed[path] ?: 0, capped)
    }
    return needed
}
