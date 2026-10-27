package app.memix.feature.videoeditor.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixTheme
import app.memix.core.model.project.TrackKind
import app.memix.feature.videoeditor.ReorderUi
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reorder mode on the timeline (spec P1-06 → Reorder): the main clips collapse into square tiles, `space-1` apart, in
 * the editor's order, and expand back to time when it ends. The moving tile starts under the finger (a long-press
 * drag) or under the playhead (the Reorder tool); a neighbor slides over when the moving tile passes it.
 *
 * Positions change at frame rate (drag, slides, edge scroll), so only draw, placement and gesture code read them.
 * The order itself belongs to the editor; [showOrder] follows it.
 */
@Stable
internal class ReorderStrip(
    order: List<String>,
    movingId: String,
    /** Where the moving tile's left edge starts on screen. */
    anchorLeftPx: Float,
    val tileSizePx: Float,
    /** A tile plus the `space-1` gap. */
    val pitchPx: Float,
    private val scope: CoroutineScope,
) {
    var order by mutableStateOf(order)
        private set
    var movingId by mutableStateOf(movingId)

    /** The left edge of the first tile's place; edge auto-scroll moves it. */
    var originPx by mutableFloatStateOf(anchorLeftPx - order.indexOf(movingId) * pitchPx)

    /** The moving tile's left edge while a finger drags it; NaN otherwise. */
    var dragLeftPx by mutableFloatStateOf(Float.NaN)

    /** How far the finger landed from the moving tile's left edge. */
    var grabOffsetPx = 0f

    /** 0: clips laid out in time; 1: tiles. Runs both ways over 200 ms. */
    val collapse = Animatable(0f)

    /** True once the editor left reorder mode: the tiles expand, then the strip goes. */
    var isEnding = false

    /** The main clips by id, from the newest timeline (the expansion runs against the reordered one). */
    var clips: Map<String, ItemUi> = emptyMap()

    // Moves originPx with a slide that would take the moving tile out of view.
    private var originScroll: Job? = null

    // Each tile's place, in tiles from the first, sliding when the order changes.
    private val places = HashMap<String, Animatable<Float, AnimationVector1D>>().apply {
        order.forEachIndexed { index, id -> put(id, Animatable(index.toFloat())) }
    }

    val isDragging: Boolean get() = !dragLeftPx.isNaN()

    /** Where tile [id] is on screen now. */
    fun tileLeftPx(id: String): Float =
        if (id == movingId && isDragging) dragLeftPx else originPx + (places[id]?.value ?: order.indexOf(id).toFloat()) * pitchPx

    /** The tile under [x], or null. */
    fun tileAt(x: Float): String? = order.firstOrNull { id -> x >= tileLeftPx(id) && x <= tileLeftPx(id) + tileSizePx }

    /** The place the dragged tile's center is nearest to. */
    fun placeUnderDraggedTile(): Int = ((dragLeftPx - originPx) / pitchPx).roundToInt().coerceIn(0, order.lastIndex)

    /**
     * Follows the editor's [newOrder]: tiles whose place changed slide there in 200 ms, or jump with reduce motion.
     * Without a finger on it, the moving tile also stays in view: when its new place is closer than [edgeZonePx] to
     * either side of a timeline [widthPx] wide, the strip scrolls with the slide to keep it that far in (spec P1-06 →
     * Reorder: the moving tile always stays in view).
     */
    fun showOrder(newOrder: List<String>, reduceMotion: Boolean, widthPx: Float, edgeZonePx: Float) {
        order = newOrder
        newOrder.forEachIndexed { index, id ->
            val place = places.getOrPut(id) { Animatable(index.toFloat()) }
            val target = index.toFloat()
            if (place.targetValue == target) return@forEachIndexed
            scope.launch { if (reduceMotion) place.snapTo(target) else place.animateTo(target, tween(MemixMotion.durationSheet)) }
        }
        if (!isDragging) keepMovingTileInView(widthPx, edgeZonePx, reduceMotion)
    }

    /** Scrolls the strip by [deltaPx], keeping at least one tile's width of it on a screen [widthPx] wide. */
    fun scrollBy(deltaPx: Float, widthPx: Float) {
        originScroll?.cancel()
        originPx = clampedOrigin(originPx + deltaPx, widthPx)
    }

    private fun keepMovingTileInView(widthPx: Float, edgeZonePx: Float, reduceMotion: Boolean) {
        val newLeftPx = originPx + order.indexOf(movingId) * pitchPx
        val shiftPx = when {
            newLeftPx < edgeZonePx -> edgeZonePx - newLeftPx
            newLeftPx + tileSizePx > widthPx - edgeZonePx -> widthPx - edgeZonePx - (newLeftPx + tileSizePx)
            else -> return
        }
        val targetPx = clampedOrigin(originPx + shiftPx, widthPx)
        originScroll?.cancel()
        if (reduceMotion) {
            originPx = targetPx
            return
        }
        originScroll = scope.launch {
            animate(originPx, targetPx, animationSpec = tween(MemixMotion.durationSheet)) { value, _ -> originPx = value }
        }
    }

    private fun clampedOrigin(candidatePx: Float, widthPx: Float): Float = candidatePx.coerceIn(tileSizePx - order.size * pitchPx, widthPx - tileSizePx)
}

/**
 * Follows the editor's reorder mode: entering collapses the main clips into tiles (a new strip with the moving tile
 * under the playhead, or the one a long-press lift already made), order changes slide the tiles, and leaving expands
 * them into the newest timeline, then drops the strip. 200 ms each, 120 ms with reduce motion.
 */
@Composable
internal fun FollowReorderMode(controller: TimelineController, reorder: ReorderUi?, timeline: TimelineUi) {
    val state = controller.state
    val scope = rememberCoroutineScope()
    val reduceMotion = MemixTheme.reduceMotion
    val durationMillis = if (reduceMotion) MemixMotion.durationPress else MemixMotion.durationSheet
    val mainClips = remember(timeline) { timeline.tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO }?.items?.associateBy { it.id }.orEmpty() }
    SideEffect { state.reorder?.clips = mainClips }
    LaunchedEffect(reorder) {
        if (reorder == null) {
            val strip = state.reorder ?: return@LaunchedEffect
            strip.isEnding = true
            strip.dragLeftPx = Float.NaN
            strip.collapse.animateTo(0f, tween(durationMillis))
            if (state.reorder === strip) state.reorder = null
            return@LaunchedEffect
        }
        // The moving tile starts under the playhead, so the timeline's width must be known first.
        snapshotFlow { state.widthPx }.first { it > 0 }
        val strip = state.reorder?.takeUnless { it.isEnding } ?: ReorderStrip(
            reorder.order,
            reorder.movingId,
            anchorLeftPx = state.widthPx / 2f - controller.reorderTileSizePx / 2,
            controller.reorderTileSizePx,
            controller.reorderPitchPx,
            scope,
        ).also {
            it.clips = mainClips
            state.reorder = it
        }
        strip.movingId = reorder.movingId
        strip.showOrder(reorder.order, reduceMotion, state.widthPx.toFloat(), controller.edgeZonePx)
        if (strip.collapse.value != 1f) strip.collapse.animateTo(1f, tween(durationMillis))
    }
}
