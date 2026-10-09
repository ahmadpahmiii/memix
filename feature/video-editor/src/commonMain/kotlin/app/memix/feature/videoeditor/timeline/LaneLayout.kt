package app.memix.feature.videoeditor.timeline

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Density
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.model.project.TrackKind
import kotlin.math.max

/**
 * Where each track's row sits in the lanes, in pixels from the top of the first row (spec P1-05 → Track order).
 * The main video row is `touch-target` tall with a `track-height-video` clip centered in it. Every other row is its
 * item plus `space-1`; an item is `track-height` tall, or its label's line height plus `space-1` above and below
 * when large fonts need more (spec → Accessibility, 200% font). Built once per timeline and text size.
 */
@Immutable
internal class LaneLayout(val rows: List<LaneRow>, val heightPx: Float) {
    /** The row under [y] (pixels from the top of the first row), or null below the last row. */
    fun rowAt(y: Float): LaneRow? = rows.firstOrNull { y >= it.topPx && y < it.topPx + it.heightPx }

    /** The row of the item with [itemId], or null when no row holds it. */
    fun rowOf(itemId: String): LaneRow? = rows.firstOrNull { row -> row.track.items.any { it.id == itemId } }
}

@Immutable
internal class LaneRow(
    val track: TrackUi,
    val topPx: Float,
    val heightPx: Float,
    val itemTopPx: Float,
    val itemHeightPx: Float,
)

internal fun laneLayoutOf(timeline: TimelineUi, density: Density, labelLineHeightPx: Float): LaneLayout = with(density) {
    val itemHeightPx = max(MemixSize.trackHeight.toPx(), labelLineHeightPx + 2 * MemixSpacing.space1.toPx())
    val rowHeightPx = itemHeightPx + MemixSpacing.space1.toPx()
    val mainRowHeightPx = MemixSize.touchTarget.toPx()
    val mainItemHeightPx = MemixSize.trackHeightVideo.toPx()
    var topPx = 0f
    val rows = timeline.tracks.map { track ->
        val row = if (track.kind == TrackKind.MAIN_VIDEO) {
            LaneRow(track, topPx, mainRowHeightPx, topPx + (mainRowHeightPx - mainItemHeightPx) / 2, mainItemHeightPx)
        } else {
            LaneRow(track, topPx, rowHeightPx, topPx + (rowHeightPx - itemHeightPx) / 2, itemHeightPx)
        }
        topPx += row.heightPx
        row
    }
    LaneLayout(rows, topPx)
}
