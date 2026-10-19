package app.memix.feature.videoeditor.timeline

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.memix.core.designsystem.MemixMotion
import app.memix.core.domain.project.TrimEdge
import app.memix.core.domain.project.TrimRange
import app.memix.core.model.project.TrackKind
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What the timeline's gestures and accessibility actions do to its [state] and the preview (spec P1-05 → Scroll, zoom
 * and scrub; Motion). The composable hands it the newest timeline, intents and settings on every composition.
 * Main thread only.
 */
@Stable
internal class TimelineController(val state: TimelineState, private val scope: CoroutineScope) {
    var timeline: TimelineUi = TimelineUi.Empty
        set(value) {
            // A released trim or move keeps its drawn place until the edited timeline arrives, so nothing jumps back.
            if (field !== value && state.trim?.isReleased == true) state.trim = null
            if (field !== value && state.move?.isReleased == true) state.move = null
            field = value
        }
    var onIntent: (VideoEditorIntent) -> Unit = {}
    var reduceMotion = false
    var density = 1f
    var haptics: HapticFeedback? = null

    /** A trimmed edge snaps to a target closer than this on screen (`space-2`). */
    var snapDistancePx = 0f

    /** A finger this close to a side of the timeline scrolls it while trimming (`touch-target`). */
    var edgeZonePx = 0f

    private var seekAnimation: Job? = null
    private var zoomAnimation: Job? = null
    private var edgeScroll: Job? = null
    private var trimFingerX = 0f

    /** Reorder tiles: `track-height-video` squares, a `space-1` gap apart. Set from the composition. */
    var reorderTileSizePx = 0f
    var reorderPitchPx = 0f
    private var reorderFingerX = 0f
    private var lastReorderPlace = -1
    private var moveFingerX = 0f
    private var moveLanes: LaneLayout? = null

    /** True in reorder mode (not while its tiles expand back after it ended). */
    val isReordering: Boolean get() = state.reorder?.isEnding == false

    /** The most zoomed-out scale for this video and screen: the whole video fits in half the width. */
    val minDpPerSecond: Float get() = minDpPerSecond(timeline.lengthUs, state.widthPx / density)

    val canZoomIn: Boolean get() = state.dpPerSecond < MAX_DP_PER_SECOND
    val canZoomOut: Boolean get() = state.dpPerSecond > minDpPerSecond

    /** A finger landed: playback pauses, and an animated seek or zoom stops where it is. */
    fun onTouchDown() {
        state.isFingerDown = true
        zoomAnimation?.cancel()
        if (seekAnimation?.isActive == true) {
            seekAnimation?.cancel()
            // The preview went straight to the target; bring it back to where the timeline stopped.
            onIntent(VideoEditorIntent.SeekTo(state.playheadUs))
        }
        onIntent(VideoEditorIntent.TimelineTouched)
    }

    fun onTouchUp() {
        state.isFingerDown = false
    }

    /** A drag or fling frame of [deltaPx]; returns what was used, so a fling stops at 0:00 and at the end. */
    fun scrollBy(deltaPx: Float): Float {
        val consumedPx = state.scrollBy(deltaPx, timeline.lengthUs, density)
        if (consumedPx != 0f) onIntent(VideoEditorIntent.ScrubTo(state.playheadUs))
        return consumedPx
    }

    /** One pinch step: the scale follows the fingers; the time under the playhead stays put. Not while reordering. */
    fun zoomBy(zoomChange: Float) {
        if (isReordering) return
        state.dpPerSecond = clampDpPerSecond(state.dpPerSecond * zoomChange, minDpPerSecond)
    }

    fun zoomIn() = zoomTo(clampDpPerSecond(state.dpPerSecond * ZOOM_MENU_STEP, minDpPerSecond))

    fun zoomOut() = zoomTo(clampDpPerSecond(state.dpPerSecond / ZOOM_MENU_STEP, minDpPerSecond))

    fun showWholeVideo() = zoomTo(minDpPerSecond)

    /** A tap on the ruler: [timeUs] scrolls under the playhead over 200 ms (a jump with reduce motion). */
    fun seekAnimated(timeUs: Long) {
        val targetUs = timeUs.coerceIn(0, timeline.lengthUs.coerceAtLeast(0))
        seekAnimation?.cancel()
        onIntent(VideoEditorIntent.SeekTo(targetUs))
        if (reduceMotion) {
            state.playheadUs = targetUs
            return
        }
        val startUs = state.playheadUs
        seekAnimation = scope.launch {
            state.isSeekAnimating = true
            try {
                animate(0f, 1f, animationSpec = tween(MemixMotion.durationSheet)) { fraction, _ ->
                    state.playheadUs = startUs + ((targetUs - startUs) * fraction).roundToLong()
                }
                state.playheadUs = targetUs
            } finally {
                state.isSeekAnimating = false
            }
        }
    }

    /** Accessibility steps and jumps: the playhead moves at once. */
    fun seekNow(timeUs: Long) {
        seekAnimation?.cancel()
        val targetUs = timeUs.coerceIn(0, timeline.lengthUs.coerceAtLeast(0))
        state.playheadUs = targetUs
        onIntent(VideoEditorIntent.SeekTo(targetUs))
    }

    /**
     * A finger landed on [edge]'s handle of the selected [item] in [row]: the drag starts at once (spec P1-06 → Trim).
     * [range] is where the edge may go; [fingerX] is where the finger is on screen.
     */
    fun startTrim(item: ItemUi, row: LaneRow, edge: TrimEdge, range: TrimRange, fingerX: Float) {
        val pxPerUs = pxPerUs(state.dpPerSecond, density)
        if (pxPerUs <= 0f) return
        val edgeUs = if (edge == TrimEdge.START) item.startUs else item.endUs
        val edgeX = xOf(edgeUs, state.playheadUs, state.widthPx / 2f, pxPerUs)
        state.trim = TrimDrag(item, row, edge, range, snapTargetsFor(timeline, item.id), grabOffsetPx = edgeX - fingerX)
        trimFingerX = fingerX
        onIntent(VideoEditorIntent.TrimStarted(item.id, edge))
        edgeScroll = scope.launch { scrollAtEdgesWhileTrimming() }
    }

    /** The finger moved to [fingerX]: the edge follows it, snapping and stopping at its limits. */
    fun dragTrim(fingerX: Float) {
        trimFingerX = fingerX
        followTrimFinger()
    }

    /** The finger lifted: the trim is committed as one step. */
    fun endTrim() {
        val drag = state.trim?.takeUnless { it.isReleased } ?: return
        edgeScroll?.cancel()
        drag.isReleased = true
        onIntent(VideoEditorIntent.TrimEnded(drag.item.id, drag.edge, drag.edgeUs, state.playheadUs))
        // A trim released where it started changes nothing and brings no new timeline; let it go after two frames.
        scope.launch {
            repeat(FRAMES_TO_WAIT_FOR_EDIT) { withFrameNanos { } }
            if (state.trim === drag) state.trim = null
        }
    }

    /** System back or a lost gesture: the item goes back as it was and nothing is committed. */
    fun cancelTrim() {
        state.trim?.takeUnless { it.isReleased } ?: return
        edgeScroll?.cancel()
        state.trim = null
        onIntent(VideoEditorIntent.TrimCancelled(state.playheadUs))
    }

    private fun followTrimFinger() {
        val drag = state.trim?.takeUnless { it.isReleased } ?: return
        val pxPerUs = pxPerUs(state.dpPerSecond, density)
        if (pxPerUs <= 0f) return
        val fingerTimeUs = timeAt(trimFingerX + drag.grabOffsetPx, state.playheadUs, state.widthPx / 2f, pxPerUs)
        val edgeBeforeUs = drag.edgeUs
        val engaged = drag.moveTo(fingerTimeUs, state.playheadUs, snapRangeUs = (snapDistancePx / pxPerUs).roundToLong())
        // One tick per snap and per limit reached (spec P1-06 → Motion and haptics).
        if (engaged) haptics?.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (drag.edgeUs != edgeBeforeUs) onIntent(VideoEditorIntent.TrimMoved(drag.edgeUs))
    }

    /**
     * While the finger is within [edgeZonePx] of a side, time scrolls that way, faster the closer it gets, up to one
     * screen width a second (spec P1-06 → Edge auto-scroll); the edge keeps following the finger as time moves.
     */
    private suspend fun scrollAtEdgesWhileTrimming() {
        var previousFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val seconds = (frameNanos - previousFrameNanos) / NANOS_PER_SECOND
            previousFrameNanos = frameNanos
            val speedPxPerSecond = edgeScrollSpeed(trimFingerX, state.widthPx.toFloat(), edgeZonePx)
            if (speedPxPerSecond != 0f && state.scrollBy(speedPxPerSecond * seconds, timeline.lengthUs, density) != 0f) {
                followTrimFinger()
            }
        }
    }

    /**
     * A long-press on main [clip] lifts it (spec P1-06 → Reorder → By drag): reorder mode starts with its tile under
     * the finger at [fingerX], and the same finger drags it. False when there's only one clip (the editor explains).
     */
    fun liftForReorder(clip: ItemUi, fingerX: Float): Boolean {
        val mainClips = timeline.tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO }?.items ?: return false
        onIntent(VideoEditorIntent.StartReorder(clip.id, byDrag = true))
        if (mainClips.size < 2) return false
        haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
        val strip = ReorderStrip(mainClips.map { it.id }, clip.id, fingerX - reorderTileSizePx / 2, reorderTileSizePx, reorderPitchPx, scope)
        strip.clips = mainClips.associateBy { it.id }
        state.reorder = strip
        startReorderDrag(strip, fingerX, grabOffsetPx = reorderTileSizePx / 2)
        return true
    }

    /** In reorder mode, a press at [fingerX] on the moving tile picks it up; false when it's elsewhere. */
    fun grabMovingTile(fingerX: Float): Boolean {
        val strip = state.reorder?.takeUnless { it.isEnding } ?: return false
        val left = strip.tileLeftPx(strip.movingId)
        if (fingerX < left || fingerX > left + strip.tileSizePx) return false
        startReorderDrag(strip, fingerX, grabOffsetPx = fingerX - left)
        return true
    }

    /** The dragged tile follows the finger; passing a neighbor's center moves it to that place. */
    fun dragReorder(fingerX: Float) {
        val strip = state.reorder?.takeIf { it.isDragging } ?: return
        reorderFingerX = fingerX
        strip.dragLeftPx = fingerX - strip.grabOffsetPx
        val place = strip.placeUnderDraggedTile()
        if (place == lastReorderPlace) return
        lastReorderPlace = place
        onIntent(VideoEditorIntent.ReorderMoveTo(place))
    }

    /** The drop applies the new order, as Done does. */
    fun dropReorder() {
        edgeScroll?.cancel()
        val strip = state.reorder?.takeIf { it.isDragging } ?: return
        strip.dragLeftPx = Float.NaN
        onIntent(VideoEditorIntent.ReorderDone)
    }

    /** A tap on another tile moves the clip to that tile's place. */
    fun tapReorderTile(x: Float) {
        val strip = state.reorder?.takeUnless { it.isEnding } ?: return
        val id = strip.tileAt(x) ?: return
        if (id != strip.movingId) onIntent(VideoEditorIntent.ReorderMoveTo(strip.order.indexOf(id)))
    }

    private fun startReorderDrag(strip: ReorderStrip, fingerX: Float, grabOffsetPx: Float) {
        strip.grabOffsetPx = grabOffsetPx
        strip.dragLeftPx = fingerX - grabOffsetPx
        reorderFingerX = fingerX
        lastReorderPlace = strip.order.indexOf(strip.movingId)
        edgeScroll?.cancel()
        edgeScroll = scope.launch { scrollStripAtEdges(strip) }
    }

    // As for trims: near a side the strip scrolls, up to a screen width a second, and the tile stays under the finger.
    private suspend fun scrollStripAtEdges(strip: ReorderStrip) {
        var previousFrameNanos = withFrameNanos { it }
        while (strip.isDragging) {
            val frameNanos = withFrameNanos { it }
            val seconds = (frameNanos - previousFrameNanos) / NANOS_PER_SECOND
            previousFrameNanos = frameNanos
            val speedPxPerSecond = edgeScrollSpeed(reorderFingerX, state.widthPx.toFloat(), edgeZonePx)
            if (speedPxPerSecond == 0f) continue
            strip.scrollBy(speedPxPerSecond * seconds, state.widthPx.toFloat())
            dragReorder(reorderFingerX)
        }
    }

    /**
     * A long-press on [item] in [row] (any lane but the main track) lifts it to move (spec P1-06 → Move → By drag): it
     * follows the finger at [fingerX], over the lanes of [lanes].
     */
    fun liftForMove(item: ItemUi, row: LaneRow, lanes: LaneLayout, fingerX: Float) {
        val pxPerUs = pxPerUs(state.dpPerSecond, density)
        if (pxPerUs <= 0f) return
        val itemX = xOf(item.startUs, state.playheadUs, state.widthPx / 2f, pxPerUs)
        state.move = MoveDrag(
            item = item,
            fromRow = row,
            rowsOfKind = lanes.rows.filter { it.track.kind == row.track.kind },
            newLaneRow = newSoundLaneRow(row, lanes),
            snapTargets = snapTargetsFor(timeline, item.id),
            grabOffsetPx = fingerX - itemX,
        )
        moveFingerX = fingerX
        moveLanes = lanes
        haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
        onIntent(VideoEditorIntent.MoveStarted(item.id))
        edgeScroll?.cancel()
        edgeScroll = scope.launch { scrollAtEdgesWhileMoving() }
    }

    /** The finger moved to [fingerX], over [laneY] (pixels from the top of the first lane). */
    fun dragMove(fingerX: Float, laneY: Float) {
        val drag = state.move?.takeUnless { it.isReleased } ?: return
        moveFingerX = fingerX
        moveLanes?.let { drag.hoverLaneAt(laneY, it) }
        followMoveFinger()
    }

    /** The finger lifted: the move is committed as one step. */
    fun dropMove() {
        val drag = state.move?.takeUnless { it.isReleased } ?: return
        edgeScroll?.cancel()
        drag.isReleased = true
        onIntent(VideoEditorIntent.MoveEnded(drag.item.id, drag.startUs, drag.laneTarget))
        // A drop where it was changes nothing and brings no new timeline; let it go after two frames.
        scope.launch {
            repeat(FRAMES_TO_WAIT_FOR_EDIT) { withFrameNanos { } }
            if (state.move === drag) state.move = null
        }
    }

    /** System back or a lost gesture: the item stays where it was. */
    fun cancelMove() {
        state.move?.takeUnless { it.isReleased } ?: return
        edgeScroll?.cancel()
        state.move = null
        onIntent(VideoEditorIntent.MoveCancelled)
    }

    private fun followMoveFinger() {
        val drag = state.move?.takeUnless { it.isReleased } ?: return
        val pxPerUs = pxPerUs(state.dpPerSecond, density)
        if (pxPerUs <= 0f) return
        val fingerStartUs = timeAt(moveFingerX - drag.grabOffsetPx, state.playheadUs, state.widthPx / 2f, pxPerUs)
        val engaged = drag.moveTo(fingerStartUs, state.playheadUs, snapRangeUs = (snapDistancePx / pxPerUs).roundToLong())
        // One tick per snap (spec P1-06 → Motion and haptics).
        if (engaged) haptics?.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    // As for trims: near a side, time scrolls that way and the item keeps following the finger.
    private suspend fun scrollAtEdgesWhileMoving() {
        var previousFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val seconds = (frameNanos - previousFrameNanos) / NANOS_PER_SECOND
            previousFrameNanos = frameNanos
            val speedPxPerSecond = edgeScrollSpeed(moveFingerX, state.widthPx.toFloat(), edgeZonePx)
            if (speedPxPerSecond != 0f && state.scrollBy(speedPxPerSecond * seconds, timeline.lengthUs, density) != 0f) {
                followMoveFinger()
            }
        }
    }

    // A meme sound over the add row lands on a new lane: drawn there at a sound lane's size (spec P1-06 → Move).
    private fun newSoundLaneRow(row: LaneRow, lanes: LaneLayout): LaneRow? {
        val addRow = lanes.addSoundRow ?: return null
        if (row.track.kind != TrackKind.MEME_SOUND) return null
        val itemTopPx = addRow.topPx + (addRow.heightPx - row.itemHeightPx) / 2
        return LaneRow(row.track, addRow.topPx, addRow.heightPx, itemTopPx, row.itemHeightPx)
    }

    private fun zoomTo(dpPerSecond: Float) {
        zoomAnimation?.cancel()
        if (reduceMotion) {
            state.dpPerSecond = dpPerSecond
            return
        }
        zoomAnimation = scope.launch {
            animate(state.dpPerSecond, dpPerSecond, animationSpec = tween(MemixMotion.durationSheet)) { value, _ ->
                state.dpPerSecond = value
            }
        }
    }
}

/**
 * The edge auto-scroll speed in px per second for a finger at [fingerX]: positive (towards earlier time) within
 * [zonePx] of the left side, negative near the right, 0 elsewhere; one screen width a second at the very edge.
 */
internal fun edgeScrollSpeed(fingerX: Float, widthPx: Float, zonePx: Float): Float {
    if (zonePx <= 0f || widthPx <= 0f) return 0f
    return when {
        fingerX < zonePx -> widthPx * (1f - fingerX.coerceAtLeast(0f) / zonePx)
        fingerX > widthPx - zonePx -> -widthPx * (1f - (widthPx - fingerX).coerceAtLeast(0f) / zonePx)
        else -> 0f
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f

/** Long enough for the editor to commit a trim and hand back the edited timeline. */
private const val FRAMES_TO_WAIT_FOR_EDIT = 2
