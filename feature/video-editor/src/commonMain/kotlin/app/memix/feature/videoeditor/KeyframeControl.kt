package app.memix.feature.videoeditor

import app.memix.core.domain.EditorTool
import app.memix.core.domain.project.KeyframeProblem
import app.memix.core.domain.project.addKeyframe
import app.memix.core.domain.project.clearKeyframes
import app.memix.core.domain.project.hasKeyframes
import app.memix.core.domain.project.keyframeAddProblem
import app.memix.core.domain.project.keyframedItem
import app.memix.core.domain.project.lookOf
import app.memix.core.domain.project.moveKeyframe
import app.memix.core.domain.project.removeKeyframe
import app.memix.core.domain.project.setKeyframeEasing
import app.memix.core.domain.project.setLookAt
import app.memix.core.domain.project.setLookAtAddsKeyframe
import app.memix.core.model.project.Easing
import app.memix.core.model.project.ItemLook
import app.memix.core.model.project.KeyframeCatalog
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keyframes in the video editor (spec P4-02b): the keyframe bar, its tools, the Easing panel and the auto-keying that every edit of a keyed
 * item goes through. Each user action is one undo step; a diamond's drag commits on release only.
 */
internal class KeyframeControl(private val host: EditorHost) {
    // Items whose first keyframe this visit already said "Keyframes on" (spec → Auto-keying: once per item per visit).
    private val announced = HashSet<String>()
    private var segment: Job? = null

    /** Whether every edit of [itemId] is a keyframe edit: it has keyframes, or its keyframe bar is open. */
    fun routes(itemId: String): Boolean =
        host.uiState.keyframeBar?.itemId == itemId || host.currentProject()?.hasKeyframes(itemId) == true

    /** The auto-keying edit ([EditorHost.commitLook]). */
    fun commitLook(edit: VideoEdit, itemId: String, pausesPlayback: Boolean, change: (ItemLook) -> ItemLook): Boolean {
        val project = host.currentProject() ?: return false
        val atUs = host.currentPositionUs()
        val look = project.lookOf(itemId, atUs) ?: return false
        val wanted = change(look)
        if (wanted == look) return false
        val adds = project.setLookAtAddsKeyframe(itemId, atUs)
        val first = !project.hasKeyframes(itemId)
        val loggedAs = if (adds) listOf(EditorTool.KEYFRAME_ADD) else null
        if (!host.commit(edit, pausesPlayback, loggedAs = loggedAs) { it.setLookAt(itemId, atUs, wanted) }) {
            // At the limit a new keyframe doesn't happen, and the editor says why (an update still works).
            if (adds && (project.keyframedItem(itemId)?.count ?: 0) >= KeyframeCatalog.MAX_PER_ITEM) host.showToast(ToastMessage.Explain(Explanation.KEYFRAME_MAX))
            return false
        }
        if (adds) host.showToast(if (first && announced.add(itemId)) ToastMessage.KeyframesOn else ToastMessage.KeyframeAdded(atUs))
        return true
    }

    /** The Keyframes tool of the selected item: the bar replaces the tool bar, the playhead stays. */
    fun open() {
        val itemId = host.uiState.selectedItemId ?: return
        val bar = host.currentProject()?.keyframeBarOf(itemId, null)?.takeIf { it.properties.any } ?: return
        host.updateUiState { it.copy(keyframeBar = bar) }
        host.returnFocusTo(FocusTarget.ADD_KEYFRAME_TOOL)
    }

    fun close() {
        stopSegment()
        host.updateUiState { it.copy(keyframeBar = null) }
        host.returnFocusTo(FocusTarget.KEYFRAMES_TOOL)
    }

    fun previous() = jump { markers, playheadUs -> markers.lastOrNull { it.timeUs < playheadUs - ON_US } } ?: host.showToast(ToastMessage.Explain(Explanation.KEYFRAME_NONE_PREVIOUS))

    fun next() = jump { markers, playheadUs -> markers.firstOrNull { it.timeUs > playheadUs + ON_US } } ?: host.showToast(ToastMessage.Explain(Explanation.KEYFRAME_NONE_NEXT))

    // Null when there is nothing that way; else pauses, moves the playhead there and selects it.
    private fun jump(pick: (List<KeyframeMarkerUi>, Long) -> KeyframeMarkerUi?): Unit? {
        val bar = host.uiState.keyframeBar ?: return Unit
        val target = pick(bar.markers, host.currentPositionUs()) ?: return null
        host.preview?.pause()
        host.preview?.seekTo(target.timeUs)
        select(target.timeUs)
        return Unit
    }

    /** Add keyframe, or Remove keyframe when the playhead is on one. */
    fun toggle() {
        val bar = host.uiState.keyframeBar ?: return
        val project = host.currentProject() ?: return
        host.preview?.pause()
        val atUs = host.currentPositionUs()
        val onKeyframe = bar.markerAt(atUs)
        if (onKeyframe != null) {
            if (host.commit(VideoEdit.KEYFRAME_REMOVE) { it.removeKeyframe(bar.itemId, onKeyframe.timeUs) }) host.showToast(ToastMessage.KeyframeRemoved, spokenOnly = true)
            return
        }
        when (project.keyframeAddProblem(bar.itemId, atUs)) {
            KeyframeProblem.OUTSIDE_ITEM -> return host.showToast(ToastMessage.Explain(Explanation.KEYFRAME_OUTSIDE))
            KeyframeProblem.LIMIT -> return host.showToast(ToastMessage.Explain(Explanation.KEYFRAME_MAX))
            // A keyframe within a frame, hidden by a trim: nothing to add or select.
            KeyframeProblem.TOO_CLOSE, KeyframeProblem.NOT_KEYFRAMEABLE -> return
            null -> Unit
        }
        if (!host.commit(VideoEdit.KEYFRAME_ADD) { it.addKeyframe(bar.itemId, atUs) }) return
        host.updateUiState { it.copy(keyframeBar = it.keyframeBar?.copy(selectedUs = atUs)) }
        host.showToast(ToastMessage.KeyframeAdded(atUs), spokenOnly = true)
    }

    /** A tap on a diamond (the timeline moved the playhead): it is selected. */
    fun select(timeUs: Long) {
        host.updateUiState { it.copy(keyframeBar = it.keyframeBar?.copy(selectedUs = timeUs)) }
        // Said, never only seen: which keyframe it is (spec P4-02b → Accessibility).
        val markers = host.uiState.keyframeBar?.markers ?: return
        val index = markers.indexOfFirst { it.timeUs == timeUs }
        if (index >= 0) host.showToast(ToastMessage.KeyframePicked(index + 1, markers.size, timeUs, markers[index].easing), spokenOnly = true)
    }

    /** A diamond's drag released, or a screen reader's Move here: one "Move keyframe" step. */
    fun moved(fromUs: Long, toUs: Long) {
        val bar = host.uiState.keyframeBar ?: return
        if (fromUs == toUs) return
        if (host.commit(VideoEdit.KEYFRAME_MOVE) { it.moveKeyframe(bar.itemId, fromUs, toUs) }) select(toUs)
    }

    /** Move here: the selected keyframe goes to the playhead. */
    fun moveHere() {
        val bar = host.uiState.keyframeBar ?: return
        val selected = bar.selected ?: return host.showToast(ToastMessage.Explain(Explanation.KEYFRAME_MOVE_NONE))
        val atUs = host.currentPositionUs()
        if (atUs < bar.startUs || atUs > bar.endUs) return host.showToast(ToastMessage.Explain(Explanation.KEYFRAME_OUTSIDE))
        if (atUs == selected.timeUs) return
        if (atUs !in selected.minUs..selected.maxUs) return host.showToast(ToastMessage.Explain(Explanation.KEYFRAME_MOVE_BLOCKED))
        moved(selected.timeUs, atUs)
    }

    /** Remove from a diamond's screen-reader actions. */
    fun removeAt(timeUs: Long) {
        val bar = host.uiState.keyframeBar ?: return
        if (host.commit(VideoEdit.KEYFRAME_REMOVE) { it.removeKeyframe(bar.itemId, timeUs) }) host.showToast(ToastMessage.KeyframeRemoved, spokenOnly = true)
    }

    /** Clear all: the item keeps the look it has at the playhead. */
    fun clear() {
        val bar = host.uiState.keyframeBar ?: return
        val atUs = host.currentPositionUs().coerceIn(bar.startUs, bar.endUs)
        host.commit(VideoEdit.KEYFRAME_CLEAR) { it.clearKeyframes(bar.itemId, atUs) }
    }

    /** The Easing tool: the keyframe at the playhead, else the selected one; explains when there is none or it is the last. */
    fun openEasing() {
        val bar = host.uiState.keyframeBar ?: return
        val marker = bar.markerAt(host.currentPositionUs()) ?: bar.selected ?: return host.showToast(ToastMessage.Explain(Explanation.EASING_NONE))
        openEasingOf(bar, marker)
    }

    /** Easing from a diamond's screen-reader actions. */
    fun openEasingAt(timeUs: Long) {
        val bar = host.uiState.keyframeBar ?: return
        val marker = bar.markerAt(timeUs) ?: return
        openEasingOf(bar, marker)
    }

    private fun openEasingOf(bar: KeyframeBarUi, marker: KeyframeMarkerUi) {
        if (bar.markers.lastOrNull()?.timeUs == marker.timeUs) return host.showToast(ToastMessage.Explain(Explanation.EASING_LAST))
        host.preview?.pause()
        host.updateUiState { it.copy(keyframeBar = it.keyframeBar?.copy(selectedUs = marker.timeUs, easingOpen = true)) }
    }

    fun closeEasing() {
        stopSegment()
        host.updateUiState { it.copy(keyframeBar = it.keyframeBar?.copy(easingOpen = false)) }
        host.returnFocusTo(FocusTarget.EASING_TOOL)
    }

    /** An Easing tile: a different one is one step; the segment plays from this keyframe to the next either way. */
    fun chooseEasing(easing: Easing) {
        val bar = host.uiState.keyframeBar ?: return
        val marker = bar.selected ?: return
        if (marker.easing != easing && host.commit(VideoEdit.KEYFRAME_EASING) { it.setKeyframeEasing(bar.itemId, marker.timeUs, easing) }) {
            host.showToast(ToastMessage.EasingChanged(easing), spokenOnly = true)
        }
        val nextUs = bar.markers.firstOrNull { it.timeUs > marker.timeUs }?.timeUs ?: return
        playSegment(marker.timeUs, nextUs)
    }

    // Plays from [fromUs] to [toUs] once; it stops there, after four seconds, or when the user pauses (a tap on the stage).
    private fun playSegment(fromUs: Long, toUs: Long) {
        val session = host.preview ?: return
        segment?.cancel()
        segment = host.scope.launch {
            session.seekTo(fromUs)
            session.play()
            val started = TimeSource.Monotonic.markNow()
            while (isActive && session.positionUs.value < toUs && started.elapsedNow() < SEGMENT_LIMIT) {
                delay(POLL)
                // Playback takes a moment to start; after that, not playing means the user stopped it.
                if (started.elapsedNow() > GRACE && !session.playback.value.isPlaying) return@launch
            }
            session.pause()
        }
    }

    private fun stopSegment() {
        segment?.cancel()
        segment = null
    }

    private companion object {
        const val ON_US = KeyframeCatalog.ON_KEYFRAME_US
        val SEGMENT_LIMIT = 4_000.milliseconds
        val GRACE = 300.milliseconds
        val POLL = 16.milliseconds
    }
}
