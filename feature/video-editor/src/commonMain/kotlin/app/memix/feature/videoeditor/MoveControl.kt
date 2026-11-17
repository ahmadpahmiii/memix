package app.memix.feature.videoeditor

import app.memix.core.domain.project.LaneTarget
import app.memix.core.domain.project.effectItem
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
        val edit = moveEditOf(itemId)
        if (!host.commit(edit) { it.moveItem(itemId, startUs, lane, newTrackId = host.newId()) }) return
        host.announce(edit, host.uiState.timeline.nameOf(itemId))
    }

    /** Move here: the selected item starts at the playhead (ends at the end when the playhead is there). */
    fun moveSelectedToPlayhead() {
        val itemId = host.uiState.selectedItemId ?: return
        host.preview?.pause()
        val edit = moveEditOf(itemId)
        if (!host.commit(edit) { it.moveItemToPlayhead(itemId, host.currentPositionUs(), newTrackId = host.newId()) }) return
        host.announce(edit, host.uiState.timeline.nameOf(itemId))
    }

    // An effect's move is named "Move effect" in the undo history (spec P4-06 → Undo steps); both log the shared `move`.
    private fun moveEditOf(itemId: String): VideoEdit = if (host.currentProject()?.effectItem(itemId) != null) VideoEdit.EFFECT_MOVE else VideoEdit.MOVE
}
