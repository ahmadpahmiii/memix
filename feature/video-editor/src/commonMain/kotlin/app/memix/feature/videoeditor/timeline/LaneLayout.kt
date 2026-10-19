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
 * when large fonts need more (spec → Accessibility, 200% font). The "Add a meme sound" row ([addSoundRow], P1-08) is
 * `touch-target` tall, right under the last meme-sound lane, or where those lanes go when there are none; an empty
 * project has none. Built once per timeline and text size.
 */
@Immutable
internal class LaneLayout(val rows: List<LaneRow>, val heightPx: Float, val addSoundRow: AddSoundRow?) {
    /** The track row under [y] (pixels from the top of the first row), or null in the add row and below the last row. */
    fun rowAt(y: Float): LaneRow? = rows.firstOrNull { y >= it.topPx && y < it.topPx + it.heightPx }

    /** True when [y] (pixels from the top of the first row) is in the "Add a meme sound" row. */
    fun isInAddSoundRow(y: Float): Boolean = addSoundRow != null && y >= addSoundRow.topPx && y < addSoundRow.topPx + addSoundRow.heightPx

    /** The row of the item with [itemId], or null when no row holds it. */
    fun rowOf(itemId: String): LaneRow? = rows.firstOrNull { row -> row.track.items.any { it.id == itemId } }
}

/** The "Add a meme sound" row's place: its pill sits in it, and a meme sound dropped on it gets a new lane. */
@Immutable
internal class AddSoundRow(val topPx: Float, val heightPx: Float)

@Immutable
internal class LaneRow(
    val track: TrackUi,
    val topPx: Float,
    val heightPx: Float,
    val itemTopPx: Float,
    val itemHeightPx: Float,
)

/**
 * Where each lane and the "Add a meme sound" row sit, top to bottom. [labelLineHeightPx] is one item label's line;
 * [addPillLabelHeightPx] is the pill label's height, one or two lines ([rememberAddPillLabelHeightPx]).
 */
internal fun laneLayoutOf(timeline: TimelineUi, density: Density, labelLineHeightPx: Float, addPillLabelHeightPx: Float): LaneLayout = with(density) {
    val itemHeightPx = max(MemixSize.trackHeight.toPx(), labelLineHeightPx + 2 * MemixSpacing.space1.toPx())
    val rowHeightPx = itemHeightPx + MemixSpacing.space1.toPx()
    val mainRowHeightPx = MemixSize.touchTarget.toPx()
    val mainItemHeightPx = MemixSize.trackHeightVideo.toPx()
    // Grows with the pill's label (one or two lines, at any text size), so it's never cut: `space-1` inside the pill
    // above and below it, and the row's `space-1` margins.
    val addRowHeightPx = max(MemixSize.touchTarget.toPx(), addPillLabelHeightPx + 4 * MemixSpacing.space1.toPx())
    val addRowGoesBefore = addSoundRowPosition(timeline)
    var topPx = 0f
    var addSoundRow: AddSoundRow? = null
    val rows = timeline.tracks.mapIndexed { index, track ->
        if (index == addRowGoesBefore) {
            addSoundRow = AddSoundRow(topPx, addRowHeightPx)
            topPx += addRowHeightPx
        }
        val row = if (track.kind == TrackKind.MAIN_VIDEO) {
            LaneRow(track, topPx, mainRowHeightPx, topPx + (mainRowHeightPx - mainItemHeightPx) / 2, mainItemHeightPx)
        } else {
            LaneRow(track, topPx, rowHeightPx, topPx + (rowHeightPx - itemHeightPx) / 2, itemHeightPx)
        }
        topPx += row.heightPx
        row
    }
    if (addRowGoesBefore == timeline.tracks.size) {
        addSoundRow = AddSoundRow(topPx, addRowHeightPx)
        topPx += addRowHeightPx
    }
    LaneLayout(rows, topPx, addSoundRow)
}

/**
 * The index of the track the add row goes before (the track count to go last), or -1 for none: after the last
 * meme-sound lane, else before the first lane that comes after meme sounds in the track order (spec P1-05 → Track
 * order). An empty project has no add row.
 */
private fun addSoundRowPosition(timeline: TimelineUi): Int {
    if (timeline.isEmpty) return -1
    val tracks = timeline.tracks
    val lastSoundLane = tracks.indexOfLast { it.kind == TrackKind.MEME_SOUND }
    if (lastSoundLane >= 0) return lastSoundLane + 1
    val firstAfterSounds = tracks.indexOfFirst { it.kind.ordinal > TrackKind.MEME_SOUND.ordinal }
    return if (firstAfterSounds >= 0) firstAfterSounds else tracks.size
}
