package app.memix.feature.videoeditor

import app.memix.core.domain.project.OriginalSound
import app.memix.core.domain.project.detachAudio
import app.memix.core.domain.project.isOriginalMuted
import app.memix.core.domain.project.originalSoundOf
import app.memix.core.domain.project.setOriginalMuted
import app.memix.feature.videoeditor.timeline.nameOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * The original audio, the sound in the user's own videos (spec P1-06 → Original audio): the mute-every-clip toggle (on
 * the timeline and in a video clip's Volume panel) and Detach audio.
 */
internal class OriginalAudioControl(private val host: EditorHost) {
    /**
     * Mutes or unmutes every main clip's own sound: one step ("Mute original audio" / "Unmute original audio"). Playback
     * keeps going, so the change is heard at once.
     */
    fun toggleMute() {
        val muted = host.currentProject()?.isOriginalMuted() ?: return
        val edit = if (muted) VideoEdit.UNMUTE_ORIGINAL else VideoEdit.MUTE_ORIGINAL
        host.commit(edit, pausesPlayback = false) { it.setOriginalMuted(!muted) }
    }

    /**
     * Detach audio on the selected video clip: its sound becomes its own "Original audio" clip, which is selected and
     * bonks (no haptic: that's for meme sounds). A clip with no sound, or already detached, explains instead, and so
     * does one whose file is missing ([fileMissing]): its detached clip would play nothing and outlive the Replace that
     * fixes the clip (P1-09 review, M2), so the missing-file toast points to Replace.
     */
    fun detachSelected(fileMissing: Boolean) {
        val itemId = host.uiState.selectedItemId ?: return
        val project = host.currentProject() ?: return
        if (fileMissing) return host.showToast(ToastMessage.MissingMedia)
        when (project.originalSoundOf(itemId)) {
            OriginalSound.NONE -> return host.showToast(ToastMessage.Explain(Explanation.NO_SOUND))
            OriginalSound.DETACHED -> return host.showToast(ToastMessage.Explain(Explanation.DETACHED))
            OriginalSound.PLAYS -> Unit
            null -> return
        }
        val wasMuted = project.isOriginalMuted()
        // Named before the selection moves to the new clip.
        val clipName = host.uiState.timeline.nameOf(itemId)
        val audioId = host.newId()
        if (!host.commit(VideoEdit.DETACH_AUDIO) { it.detachAudio(itemId, audioId, newTrackId = host.newId()) }) return
        val landed = host.newLanding(persistentSetOf(audioId))
        host.updateUiState { it.withSelection(audioId, host.currentProject()).copy(landed = landed) }
        // Nothing gets suddenly louder: detached while muted, it starts at 0% and says so.
        if (wasMuted) host.showToast(ToastMessage.Explain(Explanation.DETACHED_MUTED)) else host.announce(VideoEdit.DETACH_AUDIO, clipName)
    }
}
