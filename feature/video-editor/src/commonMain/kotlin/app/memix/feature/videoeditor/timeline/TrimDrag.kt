package app.memix.feature.videoeditor.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.domain.project.TrimEdge
import app.memix.core.domain.project.TrimRange
import app.memix.core.ui.formatTimecode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Where each end of the selected item may be trimmed to, in project µs; the domain works them out (TimelineEdits). */
@Immutable
data class TrimRanges(val start: TrimRange, val end: TrimRange) {
    fun of(edge: TrimEdge): TrimRange = when (edge) {
        TrimEdge.START -> start
        TrimEdge.END -> end
    }
}

/**
 * A trim handle being dragged (spec P1-06 → Trim). [edgeUs] moves at frame rate, so only draw, placement and gesture
 * code read it; nothing is committed until the finger lifts. Only the moving edge changes: the item's other edge
 * and every other item stay where they are, so on the main track a gap or an overlap shows until the release
 * re-packs it.
 */
@Stable
internal class TrimDrag(
    val item: ItemUi,
    /** The item's row, where it's drawn while dragged. */
    val row: LaneRow,
    val edge: TrimEdge,
    val range: TrimRange,
    /** Times the edge snaps to, sorted (see [snapTargetsFor]). */
    private val snapTargets: LongArray,
    /** How far the finger landed from the edge, in px, so grabbing a handle off-center doesn't make it jump. */
    val grabOffsetPx: Float,
) {
    /** Where the moving edge is now, µs. */
    var edgeUs by mutableLongStateOf(if (edge == TrimEdge.START) item.startUs else item.endUs)
        private set

    /** The time the edge is snapped to, or [NOT_SNAPPED]; the snap guide is drawn there. */
    var snappedToUs by mutableLongStateOf(NOT_SNAPPED)
        private set

    private var atLimit = false

    /** The finger lifted and the trim went to the editor; it stays drawn until the edited timeline arrives. */
    var isReleased = false

    /** The item's start and end while dragged. */
    val startUs: Long get() = if (edge == TrimEdge.START) edgeUs else item.startUs
    val endUs: Long get() = if (edge == TrimEdge.END) edgeUs else item.endUs

    /** Where the strip's first tile starts in the source while dragged (main video clips). */
    val trimInUs: Long get() = if (edge == TrimEdge.START) item.trimInUs + (edgeUs - item.startUs) else item.trimInUs
    val trimOutUs: Long get() = if (edge == TrimEdge.END) item.trimOutUs + (edgeUs - item.endUs) else item.trimOutUs

    /**
     * Moves the edge to the time under [fingerTimeUs] (already corrected by [grabOffsetPx]), clamped to [range], then
     * snapped to a target within [snapRangeUs] (the playhead [playheadUs] included). Returns true when a snap engaged
     * or the footage or neighbor limit was reached on this move: the caller gives one tick for it.
     */
    fun moveTo(fingerTimeUs: Long, playheadUs: Long, snapRangeUs: Long): Boolean {
        val clampedUs = fingerTimeUs.coerceIn(range.minUs, range.maxUs)
        val reachedLimit = clampedUs != fingerTimeUs
        val target = nearestTarget(clampedUs, playheadUs)?.takeIf { abs(it - clampedUs) <= snapRangeUs && it in range.minUs..range.maxUs }
        val newlySnapped = target != null && target != snappedToUs
        val newlyAtLimit = reachedLimit && !atLimit
        atLimit = reachedLimit
        snappedToUs = target ?: NOT_SNAPPED
        edgeUs = target ?: clampedUs
        return newlySnapped || newlyAtLimit
    }

    private fun nearestTarget(timeUs: Long, playheadUs: Long): Long? {
        val fromTargets = nearestIn(snapTargets, timeUs)
        if (fromTargets == null) return playheadUs
        return if (abs(playheadUs - timeUs) < abs(fromTargets - timeUs)) playheadUs else fromTargets
    }

    companion object {
        const val NOT_SNAPPED = Long.MIN_VALUE
    }
}

/**
 * What a trimmed edge snaps to (spec P1-06 → Snapping), without the playhead, which moves and is checked on each move:
 * 0:00, the end of the video, every boundary between main clips, and the start and end of every other item except
 * [draggedItemId].
 */
internal fun snapTargetsFor(timeline: TimelineUi, draggedItemId: String): LongArray {
    val targets = ArrayList<Long>()
    targets += 0L
    targets += timeline.lengthUs
    for (track in timeline.tracks) {
        for (item in track.items) {
            if (item.id == draggedItemId) continue
            targets += item.startUs
            targets += item.endUs
        }
    }
    return targets.distinct().sorted().toLongArray()
}

/** The value in sorted [values] nearest to [timeUs], or null when there are none. */
internal fun nearestIn(values: LongArray, timeUs: Long): Long? {
    if (values.isEmpty()) return null
    // The first value at or after timeUs (LongArray.binarySearch is JVM-only).
    var low = 0
    var high = values.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (values[middle] < timeUs) low = middle + 1 else high = middle
    }
    val before = values.getOrNull(low - 1)
    val after = values.getOrNull(low)
    return when {
        before == null -> after
        after == null -> before
        else -> if (timeUs - before <= after - timeUs) before else after
    }
}

/**
 * Where the snap guide goes while a trim or a move is snapped, or null; none when the target is the playhead, already a
 * line.
 */
internal fun snapGuideX(state: TimelineState, density: Float, widthPx: Float): Float? {
    val trim = state.trim?.takeUnless { it.isReleased }
    val move = state.move?.takeUnless { it.isReleased }
    val snappedUs = trim?.snappedToUs ?: move?.snappedToUs ?: return null
    if (snappedUs == TrimDrag.NOT_SNAPPED || snappedUs == MoveDrag.NOT_SNAPPED || snappedUs == state.playheadUs) return null
    return xOf(snappedUs, state.playheadUs, widthPx / 2, pxPerUs(state.dpPerSecond, density))
}

/** System back while a handle is dragged cancels the trim (spec P1-04 → System back order). */
@Composable
internal fun CancelTrimOnBack(controller: TimelineController) {
    val dragging = controller.state.trim?.isReleased == false
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = dragging,
        onBackCompleted = controller::cancelTrim,
    )
}

/**
 * The value bubble of a drag (spec P1-06 → Bubbles and guides): a trim's new length, centered on the moving edge, or a
 * move's new start, centered on the item's start. A `surface-raised` pill (no shadow: the value step sets it apart),
 * `space-1` above the dragged item's row and kept inside the timeline on both axes. Only its text recomposes (when the
 * shown hundredths change); where it sits is read at placement.
 */
@Composable
internal fun EditValueBubble(controller: TimelineController, rulerHeightPx: Float, laneScroll: ScrollState) {
    val state = controller.state
    val trim = state.trim?.takeUnless { it.isReleased }
    val move = state.move?.takeUnless { it.isReleased }
    when {
        trim != null -> ValueBubble(trim, { formatTimecode(trim.endUs - trim.startUs) }, { trim.edgeUs }, { trim.row.topPx }, controller, rulerHeightPx, laneScroll)
        move != null -> ValueBubble(move, { formatTimecode(move.startUs) }, { move.startUs }, { move.row.topPx }, controller, rulerHeightPx, laneScroll)
    }
}

@Composable
private fun ValueBubble(
    drag: Any,
    value: () -> String,
    anchorUs: () -> Long,
    rowTopPx: () -> Float,
    controller: TimelineController,
    rulerHeightPx: Float,
    laneScroll: ScrollState,
) {
    val state = controller.state
    val text by remember(drag) { derivedStateOf(value) }
    val fadeIn = remember(drag) { Animatable(0f) }
    LaunchedEffect(drag) { fadeIn.animateTo(1f, tween(MemixMotion.durationPress)) }
    val gapPx = with(LocalDensity.current) { MemixSpacing.space1.toPx() }
    Layout(
        content = {
            Text(
                text,
                MemixTheme.type.timecode,
                Modifier
                    .graphicsLayer { alpha = fadeIn.value }
                    .clip(MemixShapes.radiusFull)
                    .background(MemixColors.surfaceRaised)
                    .padding(horizontal = MemixSpacing.space2, vertical = MemixSpacing.space1),
            )
        },
    ) { measurables, constraints ->
        val bubble = measurables.first().measure(Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            val pxPerUs = pxPerUs(state.dpPerSecond, controller.density)
            val anchorX = xOf(anchorUs(), state.playheadUs, constraints.maxWidth / 2f, pxPerUs)
            val x = (anchorX - bubble.width / 2f).roundToInt().coerceIn(0, max(0, constraints.maxWidth - bubble.width))
            // Inside the timeline's top edge: on the main row it sits over the ruler, never over the transport row.
            val y = (rulerHeightPx + rowTopPx() - laneScroll.value - gapPx - bubble.height).roundToInt().coerceAtLeast(0)
            bubble.place(x, y)
        }
    }
}
