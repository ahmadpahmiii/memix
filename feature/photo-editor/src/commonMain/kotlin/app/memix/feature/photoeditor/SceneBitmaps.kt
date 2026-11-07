package app.memix.feature.photoeditor

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import app.memix.core.designsystem.MemixMotion
import app.memix.core.model.project.BoxSize
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.PanelsFormat
import app.memix.core.model.project.PhotoCatalog
import app.memix.core.model.project.PhotoFormat
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.geometry
import app.memix.core.model.project.pictureRect
import app.memix.core.model.project.coveredImageSize
import app.memix.core.model.project.size
import app.memix.core.ui.scene.SceneBitmap
import app.memix.core.domain.segmentation.SegmentationLimits
import app.memix.core.model.project.CutOut
import app.memix.core.model.project.CutOutCatalog
import app.memix.core.ui.scene.StickerPng
import app.memix.core.ui.scene.stickerPng
import app.memix.core.model.project.ImageEdit
import androidx.compose.ui.graphics.ImageBitmap
import app.memix.core.ui.scene.PreparedCutOut
import app.memix.core.ui.scene.cutOutPicture
import app.memix.core.model.project.pictureZoom
import app.memix.core.model.project.shownSize
import app.memix.core.ui.scene.editedLongSidePx
import app.memix.core.ui.scene.ImageToolRenderer
import app.memix.core.ui.scene.SceneImageSource
import app.memix.core.ui.scene.renderEditedPicture
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
internal class SceneBitmaps(private val source: SceneImageSource, private val tools: ImageToolRenderer, private val scope: CoroutineScope) : SceneImages {
    private val decoded = mutableStateMapOf<String, SceneBitmap>()
    private val appearance = mutableStateMapOf<String, Float>()
    private val decodedLongSide = HashMap<String, Int>()
    private val jobs = HashMap<String, Job>()
    private val failed = HashSet<String>()

    override fun imageOf(path: String): SceneBitmap? = decoded[path]

    // Edited layers' pictures (P2-04): geometry and color tools applied, one per layer, replaced as the edit or the size changes.
    private val processed = mutableStateMapOf<String, SceneBitmap>()
    private val processedFor = HashMap<String, ProcessedKey>()
    private var rawPicture: Pair<String, SceneBitmap>? = null

    override fun processedOf(layerId: String): SceneBitmap? = processed[layerId]

    private data class ProcessedKey(val edit: ImageEdit, val longSidePx: Int, val cutOut: CutOut?)

    // The decoded mask pictures of the cut-outs, one per file.
    private val masks = HashMap<String, SceneBitmap>()

    /**
     * Makes the processed picture of every layer in [requests] (a layer with a color edit, as the slider has it now) and lets go of
     * the rest. Runs in the caller's coroutine, which the caller cancels when a newer edit arrives; a picture is decoded again only
     * when it must be bigger than the one held. The pictures are software copies, since pixels are read back.
     */
    suspend fun process(requests: List<ProcessRequest>, onFailed: (Set<String>) -> Unit) {
        val wanted = requests.map { it.layerId }.toSet()
        (processed.keys - wanted).forEach { id ->
            processed.remove(id)
            processedFor.remove(id)
        }
        if (requests.isEmpty()) rawPicture = null
        for (request in requests) {
            val key = ProcessedKey(request.edit, request.longSidePx, request.cutOut)
            if (processedFor[request.layerId] == key) continue
            val raw = rawFor(request) ?: return onFailed(setOf(request.path))
            val prepared = request.cutOut?.let { cutOut -> preparedOf(cutOut) ?: continue }
            val image = renderEditedPicture(raw, request.edit, request.longSidePx, tools, prepared) ?: continue
            processed[request.layerId] = SceneBitmap(image, raw.sourceSize)
            processedFor[request.layerId] = key
        }
    }

    /** The cut-out screen's picture: the upright picture of [path] as a software bitmap of about [longSidePx], or null when it can't be read. */
    suspend fun cutOutSource(path: String, longSidePx: Int): SceneBitmap? = source.decode(path, longSidePx, SceneImageUse.OFFSCREEN)

    /** [cutOut] applied to [picture] (from [cutOutSource]) for the cut-out screen, the same pixels the scene makes; null when it can't be done. */
    suspend fun cutOutPreview(picture: SceneBitmap, cutOut: CutOut): ImageBitmap? {
        val prepared = preparedOf(cutOut) ?: return null
        return cutOutPicture(picture, prepared, tools)?.image
    }

    /** [cut] (from [cutOutPreview]) as a trimmed sticker PNG, at most [CutOutCatalog.STICKER_LONG_SIDE_PX] on its long side. */
    suspend fun stickerPng(cut: ImageBitmap): StickerPng = stickerPng(cut, CutOutCatalog.STICKER_LONG_SIDE_PX, tools)

    // The mask decoded, or the all-kept stand-in when there is none; null when the file can't be read.
    private suspend fun preparedOf(cutOut: CutOut): PreparedCutOut? {
        val path = cutOut.maskPath ?: return PreparedCutOut(cutOut, null)
        return maskFor(path)?.let { PreparedCutOut(cutOut, it.image) }
    }

    private suspend fun maskFor(path: String): SceneBitmap? =
        masks[path] ?: source.decode(path, SegmentationLimits.INPUT_LONG_SIDE_PX, SceneImageUse.OFFSCREEN)?.also { masks[path] = it }

    // The whole picture at the size the crop needs (the crop shows a part of it), decoded again only when the held one is too small.
    private suspend fun rawFor(request: ProcessRequest): SceneBitmap? {
        val needed = (request.longSidePx * request.edit.pictureZoom(request.sourceSize)).roundToInt().coerceIn(MIN_DECODE_PX, PhotoCatalog.MAX_DECODE_PX)
        rawPicture?.takeIf { (path, held) -> path == request.path && max(held.image.width, held.image.height) >= min(needed, max(request.sourceSize.widthPx, request.sourceSize.heightPx)) }
            ?.let { return it.second }
        val decoded = source.decode(request.path, needed, SceneImageUse.OFFSCREEN) ?: return null
        rawPicture = request.path to decoded
        return decoded
    }

    // A picture that hasn't been through ensure (or is faded in already) counts as fully there.
    override fun appearanceOf(path: String): Float = appearance[path] ?: 1f

    /**
     * Makes sure every file in [neededLongSides] (path to the long side in pixels its biggest layer is drawn at) has a
     * picture of about that size. [onFailed] gets the files that couldn't be read, once each.
     */
    fun ensure(neededLongSides: Map<String, Int>, onFailed: (Set<String>) -> Unit) {
        letGoOfFilesLeftOut(neededLongSides.keys)
        for ((path, needed) in neededLongSides) {
            if (path in failed) continue
            val have = decodedLongSide[path]
            val wanted = needed.coerceIn(MIN_DECODE_PX, PhotoCatalog.MAX_DECODE_PX)
            if (have != null && wanted <= have * GROW_LIMIT && wanted >= have * SHRINK_LIMIT) continue
            jobs.remove(path)?.cancel()
            jobs[path] = scope.launch { decode(path, wanted, onFailed) }
        }
    }

    // A file that left the scene (a replaced background picture, a deleted layer) lets go of its bitmap, so Replace
    // five times doesn't keep five cover-sized pictures; Undo decodes it again.
    private fun letGoOfFilesLeftOut(inScene: Set<String>) {
        for (path in (decoded.keys + jobs.keys).filter { it !in inScene }) {
            jobs.remove(path)?.cancel()
            decoded.remove(path)
            appearance.remove(path)
            decodedLongSide.remove(path)
        }
    }

    /** Lets go of every bitmap; the editor is closing. */
    fun release() {
        processed.clear()
        processedFor.clear()
        masks.clear()
        rawPicture = null
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
 * size. Layers whose box isn't known (no measured picture) are left out. A background picture counts at the size that
 * covers the canvas (P2-12).
 */
internal fun neededLongSides(layers: List<SceneLayerUi>, canvas: Canvas, frameWidthPx: Float, format: PhotoFormat? = null): Map<String, Int> {
    if (canvas.widthPx <= 0 || frameWidthPx <= 0f) return emptyMap()
    val pixelScale = frameWidthPx / canvas.widthPx
    val needed = HashMap<String, Int>()
    (canvas.background as? CanvasBackground.Image)?.let { background ->
        val path = background.source.cachedCopyPath ?: return@let
        val picture = background.source.pixelSize
        val covered = picture?.let { coveredImageSize(it, canvas.size()) } ?: BoxSize(canvas.widthPx.toFloat(), canvas.heightPx.toFloat())
        val drawn = max(covered.width, covered.height) * pixelScale
        needed[path] = min(drawn.roundToInt(), picture?.let { max(it.widthPx, it.heightPx) } ?: Int.MAX_VALUE)
    }
    // A Panels cell's photo is drawn to cover its cell times its zoom (spec P2-02): decoded at that size, never above the picture's own.
    (format as? PanelsFormat)?.let { panels ->
        val geometry = panels.geometry(PixelSize(canvas.widthPx, canvas.heightPx))
        panels.cells.forEachIndexed { index, cell ->
            val path = cell.source?.cachedCopyPath ?: return@forEachIndexed
            val rect = geometry?.cells?.getOrNull(index) ?: return@forEachIndexed
            val picture = cell.source?.pixelSize
            val drawn = if (picture != null) cell.pictureRect(rect, picture).let { max(it.width, it.height) } else max(rect.width, rect.height) * cell.zoom
            val capped = min((drawn * pixelScale).roundToInt(), picture?.let { max(it.widthPx, it.heightPx) } ?: Int.MAX_VALUE)
            needed[path] = max(needed[path] ?: 0, capped)
        }
    }
    for (layer in layers) {
        val path = layer.path ?: continue
        val drawn = max(layer.boxWidthPx, layer.boxHeightPx) * layer.pictureZoom * layer.transform.scale * pixelScale
        val capped = min(drawn.roundToInt(), layer.pictureLongSidePx.takeIf { it > 0 } ?: Int.MAX_VALUE)
        needed[path] = max(needed[path] ?: 0, capped)
    }
    return needed
}

/** One edited layer to process: [edit] as it stands now (a slider's live value included) at [longSidePx], from its file [path] of [sourceSize] as shown. */
internal class ProcessRequest(val layerId: String, val path: String, val sourceSize: PixelSize, val edit: ImageEdit, val longSidePx: Int, val cutOut: CutOut? = null)

/**
 * The requests for [layers] with a color edit on a frame [frameWidthPx] wide: [override] is the edit a slider shows now for one layer
 * (null when none). While [live] the size is capped so a drag recomputes a small picture; the release processes at the drawn size.
 */
internal fun processRequests(layers: List<SceneLayerUi>, canvas: Canvas, frameWidthPx: Float, override: Pair<String, ImageEdit>?, live: Boolean): List<ProcessRequest> =
    layers.mapNotNull { layer ->
        val edit = (if (override?.first == layer.id) override.second else layer.edit) ?: ImageEdit.NONE
        val path = layer.path ?: return@mapNotNull null
        val size = layer.pictureSize ?: return@mapNotNull null
        if (!edit.hasColorEdits && layer.cutOut == null) return@mapNotNull null
        val shown = edit.shownSize(size)
        val drawn = editedLongSidePx(max(layer.boxWidthPx, layer.boxHeightPx), layer.transform.scale, canvas.widthPx, frameWidthPx.roundToInt(), shown)
        ProcessRequest(layer.id, path, size, edit, if (live) min(drawn, LIVE_LONG_SIDE_PX) else drawn, layer.cutOut)
    }

private const val LIVE_LONG_SIDE_PX = 640
