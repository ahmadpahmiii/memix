package app.memix.feature.videoeditor

import app.memix.core.domain.project.LaneTarget
import app.memix.core.domain.project.moveItem
import app.memix.core.domain.project.moveItemToPlayhead
import app.memix.feature.videoeditor.timeline.nameOf

/** Move (spec P1-06 → Edits → Move): a long-press drag on any item but a main clip, and the Move here tool. */
internal class MoveControl(private val host: EditorHost) {
    /** A long-press lifted [itemId]: playback pauses and it's selected. */
    fun start(itemId: String) {
        host.preview?.pause()
        host.select(itemId)
    }

    /** The drop: one Move step (none if it's where it was). */
    fun end(itemId: String, startUs: Long, lane: LaneTarget) {
        if (!host.commit(VideoEdit.MOVE) { it.moveItem(itemId, startUs, lane, newTrackId = host.newId()) }) return
        host.announce(VideoEdit.MOVE, host.uiState.timeline.nameOf(itemId))
    }

    /** Move here: the selected item starts at the playhead (ends at the end when the playhead is there). */
    fun moveSelectedToPlayhead() {
        val itemId = host.uiState.selectedItemId ?: return
        host.preview?.pause()
        if (!host.commit(VideoEdit.MOVE) { it.moveItemToPlayhead(itemId, host.currentPositionUs(), newTrackId = host.newId()) }) return
        host.announce(VideoEdit.MOVE, host.uiState.timeline.nameOf(itemId))
    }
}
