package app.memix.feature.videoeditor

import app.memix.core.domain.project.hasSpeedSet
import app.memix.core.domain.project.laneOf
import app.memix.core.domain.project.lengthAtSpeed
import app.memix.core.domain.project.mainClipIds
import app.memix.core.domain.project.mainMediaClip
import app.memix.core.domain.project.minSpeedFor
import app.memix.core.domain.project.resetClipSpeed
import app.memix.core.domain.project.setClipSoundMode
import app.memix.core.domain.project.setClipSpeed
import app.memix.core.domain.project.speedClip
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.Project
import app.memix.core.model.project.SpeedCatalog
import app.memix.core.model.project.SpeedSound
import app.memix.feature.videoeditor.timeline.nameOf

/**
 * The Speed tool of a video clip on the main track or an overlay layer (spec docs/ux/specs/P4-09-speed.md): the panel with the tiles, the log slider and
 * the Sound choice. A tile, a slider release, the Sound choice and Reset are one undo step each ("Speed", "Speed sound", "Reset speed"). The slider only
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
        host.updateUiState { it.copy(speedPanel = panel.copy(speed = usable, lengthUs = project.lengthAtSpeed(panel.itemId, usable))) }
    }

    /** The slider was let go (or stepped): one "Speed" step, none if the speed is where it began. */
    fun released() {
        val panel = host.uiState.speedPanel ?: return
        val itemId = panel.itemId
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
        return panelOf(project, clip)
    }

    private fun open(itemId: String) {
        val project = host.currentProject() ?: return
        val clip = project.speedClip(itemId) ?: return
        moveIntoOverlay(clip, project)
        host.updateUiState { it.copy(speedPanel = panelOf(project, clip)) }
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

    private fun panelOf(project: Project, clip: MediaClip) = SpeedPanelUi(
        itemId = clip.id,
        speed = clip.speed,
        lengthUs = clip.durationUs,
        minSpeed = project.minSpeedFor(clip.id),
        keepSound = clip.soundMode == SpeedSound.KEEP,
        hasSpeedSet = project.hasSpeedSet(clip.id),
    )

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

    private companion object {
        const val VALUE_KEEP = "keep"
        const val VALUE_MUTE = "mute"
    }
}
