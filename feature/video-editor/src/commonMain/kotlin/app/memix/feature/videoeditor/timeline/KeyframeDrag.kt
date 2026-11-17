package app.memix.feature.videoeditor.timeline

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import app.memix.feature.videoeditor.KeyframeBarUi
import app.memix.feature.videoeditor.KeyframeMarkerUi
import kotlin.math.abs

/**
 * A keyframe diamond being dragged along the marker row (spec P4-02b → On the timeline). [timeUs] moves at frame rate, so only draw, placement
 * and gesture code read it; nothing is committed until the finger lifts. It stays inside [KeyframeMarkerUi.minUs]..[KeyframeMarkerUi.maxUs]:
 * the item's span, and one frame short of its neighbors, a hard stop that ticks once. It snaps to the playhead, the item's start and end and the
 * other keyframes within `space-2`.
 */
@Stable
internal class KeyframeDrag(
    val marker: KeyframeMarkerUi,
    val row: LaneRow,
    /** Times to snap to, sorted: the item's start and end and the other keyframes (the playhead is checked on each move). */
    private val snapTargets: LongArray,
    /** How far the finger landed from the diamond's center, so a diamond grabbed off-center doesn't jump. */
    val grabOffsetPx: Float,
) {
    /** Where the diamond is now, on the project timeline. */
    var timeUs by mutableLongStateOf(marker.timeUs)
        private set

    /** The time it is snapped to, or [NOT_SNAPPED]; the snap guide is drawn there. */
    var snappedToUs by mutableLongStateOf(NOT_SNAPPED)
        private set

    private var atLimit = false

    /** The finger lifted and the move went to the editor; it stays drawn until the edited timeline arrives. */
    var isReleased = false

    /** True when a snap engaged or a neighbor or the item's edge was reached on this move: the caller gives one tick. */
    fun moveTo(fingerTimeUs: Long, playheadUs: Long, snapRangeUs: Long): Boolean {
        val clampedUs = fingerTimeUs.coerceIn(marker.minUs, marker.maxUs)
        val reachedLimit = clampedUs != fingerTimeUs
        val target = nearestTarget(clampedUs, playheadUs)?.takeIf { abs(it - clampedUs) <= snapRangeUs && it in marker.minUs..marker.maxUs }
        val newlySnapped = target != null && target != snappedToUs
        val newlyAtLimit = reachedLimit && !atLimit
        atLimit = reachedLimit
        snappedToUs = target ?: NOT_SNAPPED
        timeUs = target ?: clampedUs
        return newlySnapped || newlyAtLimit
    }

    private fun nearestTarget(timeUs: Long, playheadUs: Long): Long? {
        val fromTargets = nearestIn(snapTargets, timeUs) ?: return playheadUs
        return if (abs(playheadUs - timeUs) < abs(fromTargets - timeUs)) playheadUs else fromTargets
    }

    companion object {
        const val NOT_SNAPPED = Long.MIN_VALUE
    }
}

/** What a diamond of [bar] snaps to, without the playhead: the item's start and end and the other keyframes. */
internal fun keyframeSnapTargets(bar: KeyframeBarUi, dragged: KeyframeMarkerUi): LongArray =
    (listOf(bar.startUs, bar.endUs) + bar.markers.filter { it !== dragged }.map { it.timeUs }).distinct().sorted().toLongArray()
