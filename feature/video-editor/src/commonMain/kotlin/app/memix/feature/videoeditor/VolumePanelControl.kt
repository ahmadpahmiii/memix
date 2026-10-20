package app.memix.feature.videoeditor

import app.memix.core.domain.project.setVolume
import app.memix.core.domain.project.volumeOf
import app.memix.feature.videoeditor.timeline.nameOf

/**
 * The Volume panel (spec P1-06 → Volume panel): the slider is heard while it moves (the preview's live gain), and each
 * release commits one Volume step. Playback keeps going, so each change is heard as it's made.
 */
internal class VolumePanelControl(private val host: EditorHost) {
    fun open() {
        val itemId = host.uiState.selectedItemId ?: return
        val volume = host.currentProject()?.volumeOf(itemId) ?: return
        host.updateUiState { it.copy(volumePanel = VolumePanelUi(itemId, volume)) }
    }

    /** Done, back or Escape: the item stays selected and focus goes back to its Volume tool. */
    fun close() {
        host.updateUiState { it.copy(volumePanel = null) }
        host.returnFocusTo(FocusTarget.VOLUME_TOOL)
    }

    fun change(volume: Float) {
        val panel = host.uiState.volumePanel ?: return
        host.preview?.previewVolume(panel.itemId, volume)
        host.updateUiState { it.copy(volumePanel = panel.copy(volume = volume)) }
    }

    /** The slider's last change is already in the panel, whichever way it came (drag, tap, key, TalkBack). */
    fun commit() {
        val panel = host.uiState.volumePanel ?: return
        if (!host.commit(VideoEdit.VOLUME, pausesPlayback = false) { it.setVolume(panel.itemId, panel.volume) }) return
        host.announce(VideoEdit.VOLUME, host.uiState.timeline.nameOf(panel.itemId))
    }
}
