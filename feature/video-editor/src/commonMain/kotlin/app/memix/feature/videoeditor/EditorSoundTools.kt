package app.memix.feature.videoeditor

import app.memix.core.domain.Outcome
import app.memix.core.domain.project.addSound
import app.memix.core.domain.sound.SoundPlayer
import app.memix.core.model.catalog.CatalogSound
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.ProjectType
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/**
 * Meme sounds in the editor (spec P1-06 → Meme sounds sheet, Meme sounds): the starter pack, the sheet and its
 * previews, and a sound landing at the playhead. The sheet's player opens on the first play and closes with the
 * editor ([close]).
 */
internal class EditorSoundTools(private val sounds: EditorSounds, private val host: EditorHost) : AutoCloseable {
    // The starter pack (P1-08), loaded once.
    private var starterSounds: List<CatalogSound> = emptyList()

    /** The pack's titles by catalog id; they label the sounds on the timeline. */
    var titles: Map<String, LocalizedText> = emptyMap()
        private set

    // Each sound's file once it's been checked, so a second play or add doesn't measure it again.
    private val preparedSounds = HashMap<String, MediaRef>()
    private var player: SoundPlayer? = null

    // What opened the sheet, so closing it without adding sends focus back there.
    private var sheetOpener = FocusTarget.MEME_SOUNDS_TOOL

    /** Loads the pack into the sheet's rows and [titles]. */
    suspend fun load() {
        starterSounds = sounds.loadStarterSounds()
        titles = starterSounds.associate { it.id to it.title }
        val rows = starterSounds.map { SoundRowUi(it.id, it.title, it.durationUs, it.credit) }.toImmutableList()
        host.updateUiState { it.copy(sounds = it.sounds.copy(sounds = rows)) }
    }

    /** The editor stays paused while sounds play in the sheet. */
    fun openSheet(opener: FocusTarget) {
        sheetOpener = opener
        host.preview?.pause()
        host.updateUiState { it.copy(sounds = it.sounds.copy(isOpen = true)) }
    }

    /** Close, scrim, drag, back or Escape: nothing is added, and focus goes back to the opener. */
    fun dismissSheet() {
        closeSheet()
        host.returnFocusTo(sheetOpener)
    }

    fun togglePreview(soundId: String) {
        if (host.uiState.sounds.playingId == soundId) {
            player?.stop()
            return
        }
        val sound = starterSounds.firstOrNull { it.id == soundId } ?: return
        host.scope.launch {
            val file = preparedSound(sound) ?: return@launch host.showToast(ToastMessage.Explain(Explanation.SOUND_UNREADABLE))
            // Closed while the file was being prepared: nothing plays.
            if (!host.uiState.sounds.isOpen) return@launch
            openedPlayer().play(file)
            sounds.logEvents.previewed(sound.id)
        }
    }

    /** A row's add button: the sheet closes and the sound lands at the playhead. */
    fun add(soundId: String) {
        val sound = starterSounds.firstOrNull { it.id == soundId } ?: return
        val atUs = host.currentPositionUs()
        closeSheet()
        host.preview?.pause()
        host.scope.launch {
            val file = preparedSound(sound)
            if (file == null) {
                host.showToast(ToastMessage.Explain(Explanation.SOUND_UNREADABLE))
                return@launch host.returnFocusTo(sheetOpener)
            }
            land(sound, file, atUs)
        }
    }

    /** The app went to the background: a preview would keep playing. */
    fun stopPreview() {
        player?.stop()
    }

    override fun close() {
        player?.close()
    }

    private fun closeSheet() {
        player?.stop()
        host.updateUiState { it.copy(sounds = it.sounds.copy(isOpen = false)) }
    }

    private fun land(sound: CatalogSound, file: MediaRef, atUs: Long) {
        val clipId = host.newId()
        if (!host.commit(VideoEdit.ADD_SOUND) { it.addSound(file, clipId, atUs, newTrackId = host.newId()) }) return
        sounds.logEvents.added(sound.id, ProjectType.VIDEO)
        // Selected with its tools ready; it bonks and the phone ticks (spec P1-06 → Meme sounds → Landing).
        val landed = host.newLanding(persistentSetOf(clipId), isSound = true)
        host.updateUiState { it.withSelection(clipId, host.currentProject()).copy(landed = landed) }
        val startUs = host.uiState.timeline.item(clipId)?.startUs ?: atUs
        host.showToast(ToastMessage.SoundAdded(sound.title, startUs), spokenOnly = true)
        // The new sound's tools are up; focus goes to its Volume tool (spec P1-06 → Accessibility).
        host.returnFocusTo(FocusTarget.VOLUME_TOOL)
    }

    private suspend fun preparedSound(sound: CatalogSound): MediaRef? {
        preparedSounds[sound.id]?.let { return it }
        return when (val prepared = sounds.prepareSound(sound)) {
            is Outcome.Success -> prepared.value.also { preparedSounds[sound.id] = it }
            is Outcome.Failure -> null
        }
    }

    private fun openedPlayer(): SoundPlayer = player ?: sounds.openSoundPlayer().also { opened ->
        player = opened
        host.scope.launch {
            opened.playing.collect { media ->
                val playingId = (media?.origin as? MediaOrigin.CatalogItem)?.itemId
                host.updateUiState { it.copy(sounds = it.sounds.copy(playingId = playingId)) }
            }
        }
    }
}
