package app.memix.feature.videoeditor.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.domain.project.LaneTarget
import kotlin.math.abs

/**
 * An item being moved by a long-press drag (spec P1-06 → Edits → Move): its new start follows the finger at frame rate,
 * snapping by whichever end is nearer a target, and its lane follows the finger over lanes of its own kind; a meme sound
 * over the "Add a meme sound" row goes to a new lane. Only draw, placement and gesture code read it; nothing is
 * committed until the finger lifts.
 */
@Stable
internal class MoveDrag(
    val item: ItemUi,
    /** The row it was lifted from. */
    val fromRow: LaneRow,
    /** The rows it can be dropped on: lanes of its own kind. */
    private val rowsOfKind: List<LaneRow>,
    /** Where it's drawn over the add row when it would get a new lane; null for items that can't (all but meme sounds). */
    private val newLaneRow: LaneRow?,
    /** Times either end snaps to, sorted (see [snapTargetsFor]); the playhead is checked on each move. */
    private val snapTargets: LongArray,
    /** How far right of the item's start the finger landed, in px, so the item doesn't jump under the finger. */
    val grabOffsetPx: Float,
) {
    /** The item's start now, µs; never before 0:00. */
    var startUs by mutableLongStateOf(item.startUs)
        private set

    /** The time an end is snapped to, or [NOT_SNAPPED]; the snap guide is drawn there. */
    var snappedToUs by mutableLongStateOf(NOT_SNAPPED)
        private set

    /** The row it's drawn on and would land on: one of [rowsOfKind], or [newLaneRow]. */
    var row by mutableStateOf(fromRow)
        private set

    /** The finger lifted and the move went to the editor; it stays drawn until the edited timeline arrives. */
    var isReleased = false

    val endUs: Long get() = startUs + item.durationUs

    /** Where the drop puts it: the lane under it, or a new one. */
    val laneTarget: LaneTarget get() = if (row === newLaneRow) LaneTarget.NewLane else LaneTarget.Lane(row.track.id)

    /**
     * Moves the start to [fingerStartUs] (the time under the finger less the grab offset), then snaps the nearer end to a
     * target within [snapRangeUs] (the playhead [playheadUs] included). Returns true when a snap engaged on this move:
     * the caller gives one tick for it.
     */
    fun moveTo(fingerStartUs: Long, playheadUs: Long, snapRangeUs: Long): Boolean {
        val freeStartUs = fingerStartUs.coerceAtLeast(0)
        val snap = nearestSnap(freeStartUs, playheadUs)?.takeIf { abs(it.distanceUs) <= snapRangeUs && it.startUs >= 0 }
        val newlySnapped = snap != null && snap.targetUs != snappedToUs
        snappedToUs = snap?.targetUs ?: NOT_SNAPPED
        startUs = snap?.startUs ?: freeStartUs
        return newlySnapped
    }

    /**
     * Follows the finger to the lane at [laneY] (pixels from the top of the first lane): a lane of the item's kind, or
     * the add row for a meme sound. Over anything else it stays where it was: lanes of other kinds are skipped.
     */
    fun hoverLaneAt(laneY: Float, lanes: LaneLayout) {
        val hovered = lanes.rowAt(laneY)?.let { candidate -> rowsOfKind.firstOrNull { it.track.id == candidate.track.id } }
        when {
            hovered != null -> row = hovered
            newLaneRow != null && lanes.isInAddSoundRow(laneY) -> row = newLaneRow
        }
    }

    // The target nearest to either end, as the start that puts that end on it.
    private fun nearestSnap(startUs: Long, playheadUs: Long): Snap? {
        val endUs = startUs + item.durationUs
        val candidates = listOfNotNull(
            nearestIn(snapTargets, startUs)?.let { Snap(it, it - startUs, startUs = it) },
            nearestIn(snapTargets, endUs)?.let { Snap(it, it - endUs, startUs = it - item.durationUs) },
            Snap(playheadUs, playheadUs - startUs, startUs = playheadUs),
            Snap(playheadUs, playheadUs - endUs, startUs = playheadUs - item.durationUs),
        )
        return candidates.minByOrNull { abs(it.distanceUs) }
    }

    private class Snap(val targetUs: Long, val distanceUs: Long, val startUs: Long)

    companion object {
        const val NOT_SNAPPED = Long.MIN_VALUE
    }
}

/** System back while an item is moved cancels the move (spec P1-04 → System back order). */
@Composable
internal fun CancelMoveOnBack(controller: TimelineController) {
    val moving = controller.state.move?.isReleased == false
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = moving,
        onBackCompleted = controller::cancelMove,
    )
}
