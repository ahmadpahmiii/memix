package app.memix.feature.videoeditor

import app.memix.core.domain.project.audioEffectsOf
import app.memix.core.domain.project.laneOf
import app.memix.core.domain.project.resetAudioEffects
import app.memix.core.domain.project.setBassBoost
import app.memix.core.domain.project.setDistortion
import app.memix.core.domain.project.setEchoAmount
import app.memix.core.domain.project.setEchoLength
import app.memix.core.domain.project.setFadeIn
import app.memix.core.domain.project.setFadeOut
import app.memix.core.domain.project.setPitch
import app.memix.core.domain.project.setSpeed
import app.memix.core.domain.project.setVoice
import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.AudioEffects
import app.memix.core.model.project.AudioEffectsCatalog
import app.memix.core.model.project.EchoLength
import app.memix.core.model.project.Project
import app.memix.core.model.project.VoicePreset
import app.memix.feature.videoeditor.timeline.nameOf
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The Audio effects panel (spec docs/ux/specs/P3-09-audio-effects.md) on a meme sound or an audio clip: a slider moves the value
 * on the panel as it is dragged, and each release commits one step named for it ("Undo: Pitch"). Playback keeps going, so the user
 * hears the result as soon as the preview has rebuilt its composition after the release. The sound plays through the one chain
 * the export also builds (`AudioChain` in `:engine:video`), so what is heard here is what the file will hold.
 */
internal class AudioEffectsControl(private val host: EditorHost) {
    // The open tab, kept for the visit (spec P3-09 → Entry and exit).
    private var tab = AudioEffectsTab.entries.first()

    /** Opens on the selected sound; one whose file is missing explains instead (the effects couldn't play). */
    fun open() {
        val itemId = host.uiState.selectedItemId ?: return
        val project = host.currentProject() ?: return
        val effects = project.audioEffectsOf(itemId) ?: return
        val clip = project.audioClip(itemId) ?: return
        if (host.uiState.waveforms?.isMissing(clip.source) == true) return host.showToast(ToastMessage.Explain(Explanation.AUDIO_EFFECTS_MISSING))
        host.updateUiState { it.copy(audioEffects = AudioEffectsPanelUi(itemId, tab, effects, clip.durationUs)) }
    }

    /** Done, back or Escape: the sound stays selected and focus goes back to its Audio effects tool. */
    fun close() {
        host.uiState.waveforms?.clearLiveFades()
        host.updateUiState { it.copy(audioEffects = null) }
        host.returnFocusTo(FocusTarget.AUDIO_EFFECTS_TOOL)
    }

    fun selectTab(selected: AudioEffectsTab) {
        tab = selected
        host.updateUiState { state -> state.copy(audioEffects = state.audioEffects?.copy(tab = selected)) }
    }

    /**
     * The panel follows the project: after an edit or an undo the sliders show what the clip has now (a speed change can have
     * shortened a fade). Null when the sound is gone, which closes the panel.
     */
    fun syncedWith(panel: AudioEffectsPanelUi?, project: Project): AudioEffectsPanelUi? {
        val clip = panel?.let { project.audioClip(it.itemId) } ?: return null
        return panel.copy(effects = clip.effects, clipLengthUs = clip.durationUs)
    }

    /**
     * A slider moved to [value]: kept on its grid and inside its limits (a fade stops at what the other leaves, never pushing it),
     * and shown at once. [part]'s unit: semitones, speed, or seconds of fade.
     */
    fun change(part: AudioEffectPart, value: Float) {
        val panel = host.uiState.audioEffects ?: return
        // A clip too short to fade keeps both fades at 0; the release explains.
        if ((part == AudioEffectPart.FADE_IN || part == AudioEffectPart.FADE_OUT) && !panel.canFade) return
        val effects = panel.effects
        val changed = when (part) {
            AudioEffectPart.PITCH -> effects.copy(pitchSemitones = value.roundToInt().coerceIn(AudioEffectsCatalog.PITCH_RANGE_SEMITONES))
            AudioEffectPart.SPEED -> effects.copy(speed = AudioEffectsCatalog.cleanSpeed(value))
            AudioEffectPart.FADE_IN -> effects.copy(fadeInUs = fadeFrom(value, room = panel.clipLengthUs - effects.fadeOutUs))
            AudioEffectPart.FADE_OUT -> effects.copy(fadeOutUs = fadeFrom(value, room = panel.clipLengthUs - effects.fadeInUs))
            AudioEffectPart.BASS -> effects.copy(bassBoost = AudioEffectsCatalog.cleanLevel(value))
            AudioEffectPart.DISTORTION -> effects.copy(distortion = AudioEffectsCatalog.cleanLevel(value))
            AudioEffectPart.ECHO -> effects.copy(echoAmount = AudioEffectsCatalog.cleanLevel(value))
        }
        if (changed == effects) return
        host.updateUiState { it.copy(audioEffects = panel.copy(effects = changed)) }
        hearWhileMoving(part, panel.itemId, changed)
    }

    // A fade is heard and drawn as it moves, and so are Bass boost and Distortion (they only change the samples). Pitch, speed, echo
    // (it makes the sound longer) and the voice need the composition rebuilt, so they are heard after the release.
    private fun hearWhileMoving(part: AudioEffectPart, itemId: String, effects: AudioEffects) {
        when (part) {
            AudioEffectPart.FADE_IN, AudioEffectPart.FADE_OUT -> {
                host.preview?.previewFades(itemId, effects.fadeInUs, effects.fadeOutUs)
                host.uiState.waveforms?.showLiveFades(itemId, effects.fadeInUs, effects.fadeOutUs)
            }
            AudioEffectPart.BASS, AudioEffectPart.DISTORTION -> host.preview?.previewBass(itemId, effects.bassBoost, effects.distortion)
            AudioEffectPart.PITCH, AudioEffectPart.SPEED, AudioEffectPart.ECHO -> Unit
        }
    }

    /** The slider's last change is already on the panel, whichever way it came (drag, tap, key, TalkBack). */
    fun release(part: AudioEffectPart) {
        val panel = host.uiState.audioEffects ?: return
        val itemId = panel.itemId
        val effects = panel.effects
        if ((part == AudioEffectPart.FADE_IN || part == AudioEffectPart.FADE_OUT) && !panel.canFade) {
            return host.showToast(ToastMessage.Explain(Explanation.FADE_TOO_SHORT))
        }
        val before = host.currentProject()?.laneOf(itemId)
        val edit: (Project) -> Project = when (part) {
            AudioEffectPart.PITCH -> { project -> project.setPitch(itemId, effects.pitchSemitones) }
            AudioEffectPart.SPEED -> { project -> project.setSpeed(itemId, effects.speed, newTrackId = host.newId()) }
            AudioEffectPart.FADE_IN -> { project -> project.setFadeIn(itemId, effects.fadeInUs) }
            AudioEffectPart.FADE_OUT -> { project -> project.setFadeOut(itemId, effects.fadeOutUs) }
            AudioEffectPart.BASS -> { project -> project.setBassBoost(itemId, effects.bassBoost) }
            AudioEffectPart.DISTORTION -> { project -> project.setDistortion(itemId, effects.distortion) }
            AudioEffectPart.ECHO -> { project -> project.setEchoAmount(itemId, effects.echoAmount) }
        }
        val recorded = host.commit(part.edit(), pausesPlayback = false, change = edit)
        host.uiState.waveforms?.clearLiveFades()
        if (recorded) {
            host.announce(part.edit(), host.uiState.timeline.nameOf(itemId))
            announceLaneMove(before, itemId)
        } else {
            // Nothing changed (a drag that came back, or a limit): the panel shows the clip as it is.
            host.currentProject()?.let { project -> host.updateUiState { it.copy(audioEffects = syncedWith(it.audioEffects, project)) } }
        }
    }

    /** A tile of the Voice tab: one step, "Voice", whose event carries the preset id; the voice already set records nothing. */
    fun selectVoice(preset: VoicePreset) {
        val itemId = host.uiState.audioEffects?.itemId ?: return
        if (host.commit(VideoEdit.VOICE, pausesPlayback = false, value = preset.eventValue) { it.setVoice(itemId, preset) }) {
            host.announce(VideoEdit.VOICE, host.uiState.timeline.nameOf(itemId))
        }
    }

    /** A segment of the Echo tab's length: one step; with the echo off it is stored anyway and the sound doesn't change. */
    fun selectEchoLength(length: EchoLength) {
        val itemId = host.uiState.audioEffects?.itemId ?: return
        if (host.commit(VideoEdit.ECHO_LENGTH, pausesPlayback = false) { it.setEchoLength(itemId, length) }) {
            host.announce(VideoEdit.ECHO_LENGTH, host.uiState.timeline.nameOf(itemId))
        }
    }

    /** Reset (spec P3-09 rule 2): the whole clip back to plain, one step; with nothing set it explains and records nothing. */
    fun reset() {
        val panel = host.uiState.audioEffects ?: return
        val itemId = panel.itemId
        val project = host.currentProject() ?: return
        if (project.audioEffectsOf(itemId)?.isDefault != false) return host.showToast(ToastMessage.NothingToReset)
        val before = project.laneOf(itemId)
        if (!host.commit(VideoEdit.RESET_EFFECTS, pausesPlayback = false) { it.resetAudioEffects(itemId, newTrackId = host.newId()) }) return
        host.showToast(ToastMessage.AudioEffectsReset, spokenOnly = true)
        announceLaneMove(before, itemId)
    }

    // A longer clip that didn't fit its lane is on another one now: said once, as the spec's toast (P3-09 rule 4).
    private fun announceLaneMove(laneBefore: String?, itemId: String) {
        if (laneBefore != null && host.currentProject()?.laneOf(itemId) != laneBefore) host.showToast(ToastMessage.SoundMovedLane)
    }

    // Seconds from the slider onto the 0.1 s grid, at most 5 s and at most what the other fade leaves.
    private fun fadeFrom(seconds: Float, room: Long): Long {
        val gridUs = (seconds * MICROS_PER_SECOND / AudioEffectsCatalog.FADE_STEP_US).roundToLong() * AudioEffectsCatalog.FADE_STEP_US
        val limitUs = minOf(AudioEffectsCatalog.MAX_FADE_US, room.coerceAtLeast(0))
        // The room itself may be between two steps; stopping at the last whole step keeps the readout and the edit equal.
        return gridUs.coerceIn(0, limitUs / AudioEffectsCatalog.FADE_STEP_US * AudioEffectsCatalog.FADE_STEP_US)
    }

    private fun AudioEffectPart.edit(): VideoEdit = when (this) {
        AudioEffectPart.PITCH -> VideoEdit.PITCH
        AudioEffectPart.SPEED -> VideoEdit.SPEED
        AudioEffectPart.FADE_IN -> VideoEdit.FADE_IN
        AudioEffectPart.FADE_OUT -> VideoEdit.FADE_OUT
        AudioEffectPart.BASS -> VideoEdit.BASS
        AudioEffectPart.DISTORTION -> VideoEdit.DISTORTION
        AudioEffectPart.ECHO -> VideoEdit.ECHO
    }

    private fun Project.audioClip(itemId: String): AudioClip? = video?.tracks.orEmpty().flatMap { it.items }.filterIsInstance<AudioClip>().firstOrNull { it.id == itemId }

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000f
    }
}
