package app.memix.feature.videoeditor

import app.memix.core.domain.project.OriginalSound
import app.memix.core.domain.project.originalSoundOf
import app.memix.core.domain.project.setVolume
import app.memix.core.domain.project.volumeOf
import app.memix.feature.videoeditor.timeline.nameOf

/**
 * The Volume panel (spec P1-06 → Volume panel) on a sound or a video clip: the slider is heard while it moves (the
 * preview's live gain), and each release commits one Volume step. Playback keeps going, so each change is heard as it's
 * made. A video clip's panel also holds the "Mute original audio of every clip" row ([OriginalAudioControl]).
 */
internal class VolumePanelControl(private val host: EditorHost) {
    /**
     * Opens on the selected sound or video clip. A video clip with no sound, or whose sound is already on its own
     * track, explains instead (spec P1-06 → Clip volume).
     */
    fun open() {
        val itemId = host.uiState.selectedItemId ?: return
        val project = host.currentProject() ?: return
        val originalSound = project.originalSoundOf(itemId)
        when (originalSound) {
            OriginalSound.NONE -> return host.showToast(ToastMessage.Explain(Explanation.NO_SOUND))
            OriginalSound.DETACHED -> return host.showToast(ToastMessage.Explain(Explanation.DETACHED))
            OriginalSound.PLAYS, null -> Unit
        }
        val volume = project.volumeOf(itemId) ?: return
        host.updateUiState { it.copy(volumePanel = VolumePanelUi(itemId, volume, isVideoClip = originalSound == OriginalSound.PLAYS)) }
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
