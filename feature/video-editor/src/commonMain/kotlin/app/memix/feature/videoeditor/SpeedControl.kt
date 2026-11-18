package app.memix.feature.videoeditor

import app.memix.core.domain.project.addSpeedPoint
import app.memix.core.domain.project.applySpeedPreset
import app.memix.core.domain.project.canAddPoint
import app.memix.core.domain.project.curveTooLong
import app.memix.core.domain.project.withPointAdded
import app.memix.core.domain.project.withPointMoved
import app.memix.core.domain.project.hasSpeedSet
import app.memix.core.domain.project.laneOf
import app.memix.core.domain.project.lengthAtSpeed
import app.memix.core.domain.project.lengthWithCurve
import app.memix.core.domain.project.mainClipIds
import app.memix.core.domain.project.mainMediaClip
import app.memix.core.domain.project.minSpeedFor
import app.memix.core.domain.project.moveSpeedPoint
import app.memix.core.domain.project.removeSpeedPoint
import app.memix.core.domain.project.resetClipSpeed
import app.memix.core.domain.project.setClipSoundMode
import app.memix.core.domain.project.setClipSpeed
import app.memix.core.domain.project.shownSpeedCurve
import app.memix.core.domain.project.speedClip
import app.memix.core.domain.project.widestGapMiddle
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.Project
import app.memix.core.model.project.SpeedCatalog
import app.memix.core.model.project.SpeedPoint
import app.memix.core.model.project.SpeedPreset
import app.memix.core.model.project.SpeedSound
import kotlin.math.abs
import app.memix.feature.videoeditor.timeline.nameOf

/**
 * The Speed tool of a video clip on the main track or an overlay layer (spec docs/ux/specs/P4-09-speed.md): the panel with the tiles, the log slider and
 * the Sound choice, and, from P4-09b, the Curve tab (presets, the graph, the point controls). A tile, a slider release, the Sound choice, a preset, a point
 * gesture and Reset are one undo step each ("Speed", "Speed sound", "Speed curve", "Reset speed"). The slider only
 * moves the readout while it is dragged: changing the speed changes the clip's length, so the preview shows it when the finger lifts (the same rule as
 * P3-09's speed). What a speed does to the lanes, the keyframes and the transitions is decided in `:core:domain` (`SpeedEdits`), in the same step.
 */
internal class SpeedControl(private val host: EditorHost) {
    /** The Speed tool of the selected clip: the panel opens, playback left as it was. */
    fun open() {
        val itemId = host.uiState.selectedItemId ?: return
        open(itemId)
    }

    /**
     * The Speed tool of the main bar (nothing selected): it selects the video clip under the playhead (the last one at the very end; a photo there picks
     * the nearest video clip to the left, else to the right) and opens (spec P4-09 → Entry and exit). Nothing happens with no video clip.
     */
    fun openFromMainBar() {
        val project = host.currentProject() ?: return
        val clip = videoClipNearPlayhead(project) ?: return
        host.select(clip.id)
        open(clip.id)
    }

    /** Done, back or Escape: the clip stays selected, focus goes back to the Speed tool. */
    fun closePanel() {
        if (host.uiState.speedPanel == null) return
        dragging = null
        host.updateUiState { it.copy(speedPanel = null) }
        host.returnFocusTo(FocusTarget.SPEED_TOOL)
    }

    /** A tile: the speed is set and committed at once; the tile that is already chosen changes nothing. */
    fun pickTile(speed: Float) {
        changed(speed)
        released()
    }

    /** The slider moved to [speed]: the readout follows and the thumb stops at the slowest the 10-minute limit allows; nothing is committed yet. */
    fun changed(speed: Float) {
        val panel = host.uiState.speedPanel ?: return
        val project = host.currentProject() ?: return
        val usable = SpeedCatalog.cleanSpeed(speed).coerceAtLeast(panel.minSpeed)
        // The thumb stops at the 10-minute limit; the panel says why when the finger lets go.
        hitLengthLimit = SpeedCatalog.cleanSpeed(speed) < panel.minSpeed
        host.updateUiState { it.copy(speedPanel = panel.copy(speed = usable, lengthUs = project.lengthAtSpeed(panel.itemId, usable))) }
    }

    /** The slider was let go (or stepped): one "Speed" step, none if the speed is where it began. */
    fun released() {
        val panel = host.uiState.speedPanel ?: return
        val itemId = panel.itemId
        if (hitLengthLimit) {
            hitLengthLimit = false
            host.showToast(ToastMessage.Explain(Explanation.SPEED_TOO_LONG))
        }
        val laneBefore = host.currentProject()?.laneOf(itemId)
        val recorded = host.commit(VideoEdit.CLIP_SPEED, pausesPlayback = false, value = speedText(panel.speed)) { it.setClipSpeed(itemId, panel.speed, newTrackId = host.newId()) }
        if (!recorded) return resync()
        host.announce(VideoEdit.CLIP_SPEED, host.uiState.timeline.nameOf(itemId))
        if (laneBefore != null && host.currentProject()?.laneOf(itemId) != laneBefore) host.showToast(ToastMessage.SpeedMovedLane)
    }

    /** Keep or Mute: one "Speed sound" step; the choice that is already set changes nothing. */
    fun selectSound(keep: Boolean) {
        val panel = host.uiState.speedPanel ?: return
        val mode = if (keep) SpeedSound.KEEP else SpeedSound.MUTE
        val recorded = host.commit(VideoEdit.CLIP_SPEED_SOUND, pausesPlayback = false, value = if (keep) VALUE_KEEP else VALUE_MUTE) { it.setClipSoundMode(panel.itemId, mode) }
        if (!recorded) resync()
    }

    /** Reset: 1x, no curve, Keep, one step; with nothing set it only says so. */
    fun reset() {
        val panel = host.uiState.speedPanel ?: return
        if (host.currentProject()?.hasSpeedSet(panel.itemId) != true) return host.showToast(ToastMessage.Explain(Explanation.SPEED_NOTHING_TO_RESET))
        val laneBefore = host.currentProject()?.laneOf(panel.itemId)
        if (!host.commit(VideoEdit.CLIP_SPEED_RESET, pausesPlayback = false) { it.resetClipSpeed(panel.itemId, newTrackId = host.newId()) }) return
        if (laneBefore != null && host.currentProject()?.laneOf(panel.itemId) != laneBefore) host.showToast(ToastMessage.SpeedMovedLane)
    }

    /** The project changed (an edit, undo or redo): the panel shows what its clip has now, and closes when the clip is gone. */
    fun syncedWith(panel: SpeedPanelUi?, project: Project): SpeedPanelUi? {
        val shown = panel ?: return null
        val clip = project.speedClip(shown.itemId) ?: return null
        return panelOf(project, clip, previous = shown)
    }

    /** The Normal or Curve tab: only what shows changes. */
    fun selectTab(tab: SpeedTab) {
        host.updateUiState { it.copy(speedPanel = it.speedPanel?.copy(tab = tab)) }
    }

    /** A preset tile: its points on the clip, one "Speed curve" step with the preset's id as the value; the tile that is already chosen changes nothing. */
    fun pickPreset(preset: SpeedPreset) {
        val panel = host.uiState.speedPanel ?: return
        if (host.currentProject()?.curveTooLong(panel.itemId, preset.points) == true) return host.showToast(ToastMessage.Explain(Explanation.SPEED_TOO_LONG))
        commitCurve(panel.itemId, preset.id) { it.applySpeedPreset(panel.itemId, preset, newTrackId = host.newId()) }
    }

    /** One thing done on the Curve tab (a point picked, dragged, stepped, added or removed). */
    fun editCurve(action: SpeedCurveAction) {
        val panel = host.uiState.speedPanel ?: return
        when (action) {
            is SpeedCurveAction.SelectPoint -> select(action.index)
            SpeedCurveAction.PreviousPoint -> select((panel.selectedPoint - 1).coerceAtLeast(0))
            SpeedCurveAction.NextPoint -> select((panel.selectedPoint + 1).coerceAtMost(panel.curve.lastIndex))
            is SpeedCurveAction.Drag -> dragPoint(panel, action.index, action.fraction, action.speed)
            SpeedCurveAction.DragEnd -> endDrag(panel)
            is SpeedCurveAction.Tap -> addPointAt(panel, action.fraction)
            SpeedCurveAction.AddPoint -> addPointAt(panel, panel.curve.widestGapMiddle())
            SpeedCurveAction.RemovePoint -> removeSelectedPoint(panel)
            is SpeedCurveAction.StepTime -> stepSelected(panel, action.count, action.held, byTime = true)
            is SpeedCurveAction.StepSpeed -> stepSelected(panel, action.count, action.held, byTime = false)
        }
    }

    private fun select(index: Int) {
        host.updateUiState { it.copy(speedPanel = it.speedPanel?.let { panel -> panel.copy(selectedPoint = index.coerceIn(NO_POINT, panel.curve.lastIndex)) }) }
    }

    // The point follows the finger or the held button; the curve and its length are drawn as they would be, from the pure curve functions (no edit runs per pointer
    // move). Nothing is committed (a change of the curve rebuilds the composition).
    private fun dragPoint(panel: SpeedPanelUi, index: Int, fraction: Float, speed: Float) {
        val project = host.currentProject() ?: return
        dragging = CurveDrag(index, fraction, speed)
        val moved = (project.shownSpeedCurve(panel.itemId)).withPointMoved(index, fraction, speed)
        host.updateUiState { it.copy(speedPanel = it.speedPanel?.copy(curve = moved, lengthUs = project.lengthWithCurve(panel.itemId, moved), selectedPoint = index)) }
    }

    private fun endDrag(panel: SpeedPanelUi) {
        val drag = dragging ?: return
        dragging = null
        val project = host.currentProject() ?: return
        if (project.curveTooLong(panel.itemId, project.shownSpeedCurve(panel.itemId).withPointMoved(drag.index, drag.fraction, drag.speed))) {
            host.showToast(ToastMessage.Explain(Explanation.SPEED_TOO_LONG))
            return resync()
        }
        commitCurve(panel.itemId, VALUE_CUSTOM) { it.moveSpeedPoint(panel.itemId, drag.index, drag.fraction, drag.speed, newTrackId = host.newId()) }
    }

    private fun addPointAt(panel: SpeedPanelUi, fraction: Float) {
        if (!panel.curve.canAddPoint()) return host.showToast(ToastMessage.Explain(Explanation.SPEED_POINTS_MAX))
        val project = host.currentProject()
        if (project?.curveTooLong(panel.itemId, project.shownSpeedCurve(panel.itemId).withPointAdded(fraction)) == true) return host.showToast(ToastMessage.Explain(Explanation.SPEED_TOO_LONG))
        val recorded = commitCurve(panel.itemId, VALUE_CUSTOM) { it.addSpeedPoint(panel.itemId, fraction, newTrackId = host.newId()) }
        if (!recorded) return
        val curve = host.currentProject()?.shownSpeedCurve(panel.itemId) ?: return
        select(curve.indices.minByOrNull { abs(curve[it].positionFraction - fraction) } ?: NO_POINT)
    }

    private fun removeSelectedPoint(panel: SpeedPanelUi) {
        val index = panel.selectedPoint
        if (panel.selected == null) return
        if (index == 0 || index == panel.curve.lastIndex) return host.showToast(ToastMessage.Explain(Explanation.SPEED_POINT_END))
        commitCurve(panel.itemId, VALUE_CUSTOM) { it.removeSpeedPoint(panel.itemId, index, newTrackId = host.newId()) }
        select(index - 1)
    }

    // A step of a stepper: one tap commits; a hold repeats as a live preview (the same drag as a finger) and commits once on release (the button reports the total).
    private fun stepSelected(panel: SpeedPanelUi, count: Int, held: Boolean, byTime: Boolean) {
        // The base is the committed point, not the one a hold already moved on screen: the button's release reports every step of the hold.
        val point = host.currentProject()?.shownSpeedCurve(panel.itemId)?.getOrNull(panel.selectedPoint) ?: return
        val fraction = if (byTime) point.positionFraction + count * SpeedCatalog.TIME_STEP else point.positionFraction
        val speed = if (byTime) point.speed else SpeedCatalog.gridStep(point.speed, count)
        dragPoint(panel, panel.selectedPoint, fraction, speed)
        if (!held) endDrag(panel)
    }

    // One "Speed curve" step; [value] is the preset's id or "custom". Returns whether a step was recorded; the panel shows the clip as it is either way.
    private fun commitCurve(itemId: String, value: String, change: (Project) -> Project): Boolean {
        val laneBefore = host.currentProject()?.laneOf(itemId)
        val recorded = host.commit(VideoEdit.CLIP_SPEED_CURVE, pausesPlayback = false, value = value, change = change)
        if (!recorded) {
            resync()
            return false
        }
        host.announce(VideoEdit.CLIP_SPEED_CURVE, host.uiState.timeline.nameOf(itemId))
        if (laneBefore != null && host.currentProject()?.laneOf(itemId) != laneBefore) host.showToast(ToastMessage.SpeedMovedLane)
        return true
    }

    private fun open(itemId: String) {
        val project = host.currentProject() ?: return
        val clip = project.speedClip(itemId) ?: return
        moveIntoOverlay(clip, project)
        host.updateUiState { it.copy(speedPanel = panelOf(project, clip, previous = null)) }
    }

    // Nothing changed (a drag that came back, or a limit): the panel shows the clip as it is.
    private fun resync() {
        val project = host.currentProject() ?: return
        host.updateUiState { it.copy(speedPanel = syncedWith(it.speedPanel, project)) }
    }

    // Opening with the playhead outside an overlay moves it to the overlay's start, so the change can be seen (P4-01b's rule); a main clip keeps the playhead.
    private fun moveIntoOverlay(clip: MediaClip, project: Project) {
        if (project.mainMediaClip(clip.id) != null) return
        val atUs = host.currentPositionUs()
        if (atUs >= clip.startUs && atUs < clip.startUs + clip.durationUs) return
        host.preview?.seekTo(clip.startUs)
    }

    // The panel shows the clip as it is; the tab and the selected point stay as they were (a clip with a curve opens on the Curve tab).
    private fun panelOf(project: Project, clip: MediaClip, previous: SpeedPanelUi?): SpeedPanelUi {
        val curve = project.shownSpeedCurve(clip.id)
        val hasCurve = SpeedCatalog.curveOf(clip) != null
        return SpeedPanelUi(
            itemId = clip.id,
            speed = clip.speed,
            lengthUs = clip.durationUs,
            minSpeed = project.minSpeedFor(clip.id),
            keepSound = clip.soundMode == SpeedSound.KEEP,
            hasSpeedSet = project.hasSpeedSet(clip.id),
            tab = previous?.tab ?: if (hasCurve) SpeedTab.CURVE else SpeedTab.NORMAL,
            curve = curve,
            hasCurve = hasCurve,
            selectedPoint = (previous?.selectedPoint ?: NO_POINT).coerceAtMost(curve.lastIndex),
        )
    }

    private fun videoClipNearPlayhead(project: Project): MediaClip? {
        val clips = project.mainClipIds().mapNotNull(project::mainMediaClip)
        val atUs = host.currentPositionUs()
        val underIndex = clips.indexOfFirst { atUs >= it.startUs && atUs < it.startUs + it.durationUs }.takeIf { it >= 0 } ?: clips.lastIndex
        if (underIndex < 0) return null
        fun MediaClip.isVideo() = source.kind == MediaKind.VIDEO
        return clips.getOrNull(underIndex)?.takeIf { it.isVideo() }
            ?: clips.take(underIndex).lastOrNull { it.isVideo() }
            ?: clips.drop(underIndex + 1).firstOrNull { it.isVideo() }
    }

    // The point gesture in progress: where it would put the point, committed when it ends.
    private data class CurveDrag(val index: Int, val fraction: Float, val speed: Float)

    private var dragging: CurveDrag? = null

    // The slider or a tile asked for a speed slower than the 10-minute limit allows.
    private var hitLengthLimit = false

    private companion object {
        const val VALUE_KEEP = "keep"
        const val VALUE_MUTE = "mute"
        const val VALUE_CUSTOM = "custom"

        // A layer id for the preview of a drag; the project it makes is only read for its curve and never kept.
        const val PREVIEW_TRACK_ID = "speed-curve-preview"
    }
}
