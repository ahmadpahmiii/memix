package app.memix.feature.videoeditor

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.OVERHANG_NOTICE_US
import app.memix.core.domain.project.addSound
import app.memix.core.domain.project.unheardTailUs
import app.memix.core.domain.project.videoLengthUs
import app.memix.core.domain.sound.PickedSound
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import app.memix.core.model.project.TrackKind
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.launch

/**
 * Meme sounds in the editor (spec P1-06 → Meme sounds, P3-05): the starter pack's titles, which label sounds on the
 * timeline, the sheet's open state, and a picked sound landing at the playhead. Choosing, previewing and downloading
 * happen in the picker (`:feature:sounds`); it hands over a [PickedSound] with its file ready.
 */
internal class EditorSoundTools(private val sounds: EditorSounds, private val host: EditorHost) {
    /** Titles of the sounds on the timeline by catalog id: the starter pack, then each library sound landed in this session. */
    var titles: Map<String, LocalizedText> = emptyMap()
        private set

    // What opened the sheet, so closing it without adding sends focus back there.
    private var sheetOpener = FocusTarget.MEME_SOUNDS_TOOL

    // Where the playhead was when the sheet opened: nothing can move it behind the sheet, and a download that takes
    // a while must still land where the user was (spec P3-05 → Entry and exit).
    private var playheadAtOpenUs = 0L

    /** Loads the starter pack's titles. */
    suspend fun load() {
        titles = sounds.loadStarterSounds().associate { it.id to it.title } + titles
    }

    /**
     * Labels for the library sounds [project] already uses: a project keeps only the catalog id, so the titles come from
     * the phone's copy of the catalog. Returns true when it found any, so the timeline needs redrawing.
     */
    suspend fun loadLibraryTitles(project: Project): Boolean {
        val found = sounds.loadTitles(project, known = titles.keys)
        if (found.isEmpty()) return false
        titles = titles + found
        return true
    }

    /** The editor stays paused while sounds play in the sheet. */
    fun openSheet(opener: FocusTarget) {
        sheetOpener = opener
        // Paused first, so the playhead read is where the picture stopped.
        host.preview?.pause()
        playheadAtOpenUs = host.currentPositionUs()
        host.updateUiState { it.copy(sounds = it.sounds.copy(isOpen = true)) }
    }

    /** Close, scrim, drag, back or Escape: nothing is added, and focus goes back to the opener. */
    fun dismissSheet() {
        closeSheet()
        host.returnFocusTo(sheetOpener)
    }

    /**
     * A row's Add block or "Add to video": the sound gets its file in this project, lands at the playhead and the sheet
     * closes. The sheet stays up until then, so a user who closes it first cancels the landing.
     */
    fun add(picked: PickedSound) {
        val project = host.currentProject() ?: return closeSheet()
        host.scope.launch {
            val file = when (val placed = sounds.placeInProject(project.id, picked)) {
                is Outcome.Success -> placed.value
                is Outcome.Failure -> return@launch cannotAdd(placed.error)
            }
            // Closed while the file was being copied: the user walked away, so nothing lands.
            if (!host.uiState.sounds.isOpen) return@launch
            land(picked, file)
        }
    }

    /**
     * The editor opened from the Sounds tab's Use flow (spec P3-15 → Arriving): the project already has the sound at 0:00.
     * It bonks once and the phone gives the Confirm haptic, as for any sound that lands, but nothing is selected, so the main tool
     * bar stays, and no step is recorded, so Undo stays empty. A sound that runs more than half a second past the video's end
     * gets the trim-or-move toast; otherwise the landing is only spoken.
     */
    fun showStartingSound() {
        val project = host.currentProject() ?: return
        val clip = project.firstMemeSound() ?: return
        // The title is known by now: the draft's titles load as it opens, a push earlier. Without it the sentence has no name.
        val title = when (val origin = clip.source.origin) {
            is MediaOrigin.CatalogItem -> titles[origin.itemId]
            is MediaOrigin.MySound -> origin.name.takeIf { it.isNotBlank() }?.let { LocalizedText(mapOf("en" to it)) }
            is MediaOrigin.GalleryUri, is MediaOrigin.PhotoAsset -> null
        }
        host.updateUiState { it.copy(landed = host.newLanding(persistentSetOf(clip.id), isSound = true)) }
        announceLanding(title, clip.startUs, clip.durationUs)
    }

    private fun Project.firstMemeSound(): AudioClip? = video?.tracks.orEmpty()
        .firstOrNull { it.kind == TrackKind.MEME_SOUND }?.items?.firstOrNull() as? AudioClip

    // The file could not be copied: no room (the phone-is-full line) or the cache file is gone (the "won't play" line).
    private fun cannotAdd(error: AppError) {
        host.showToast(ToastMessage.Explain(if (error == AppError.StorageFull) Explanation.SOUND_NO_SPACE else Explanation.SOUND_UNREADABLE))
        dismissSheet()
    }

    private fun closeSheet() {
        host.updateUiState { it.copy(sounds = it.sounds.copy(isOpen = false)) }
    }

    private fun land(picked: PickedSound, file: MediaRef) {
        val clipId = host.newId()
        titles = titles + (picked.soundId to picked.title)
        closeSheet()
        host.preview?.pause()
        if (!host.commit(VideoEdit.ADD_SOUND) { it.addSound(file, clipId, playheadAtOpenUs, newTrackId = host.newId()) }) return
        sounds.logEvents.added(picked.eventSoundId, picked.tab, ProjectType.VIDEO)
        // A My sound has its own tab and never joins Recently used (spec P3-07).
        if (!picked.isMine) host.scope.launch { sounds.recordRecent(picked.soundId) }
        // Selected with its tools ready; it bonks and the phone ticks (spec P1-06 → Meme sounds → Landing).
        val landed = host.newLanding(persistentSetOf(clipId), isSound = true)
        host.updateUiState { it.withSelection(clipId, host.currentProject()).copy(landed = landed) }
        val startUs = host.uiState.timeline.item(clipId)?.startUs ?: playheadAtOpenUs
        announceLanding(picked.title.takeIf { it.byLanguage.values.any(String::isNotBlank) }, startUs, file.durationUs ?: 0L)
        // The new sound's tools are up; focus goes to its Volume tool (spec P1-06 → Accessibility).
        host.returnFocusTo(FocusTarget.VOLUME_TOOL)
    }

    // A sound that runs past the end of the video lands where the user chose; more than half a second that nobody hears gets a
    // toast with the two fixes (spec P3-05 → Where it lands). That toast also speaks "{name} added at {time}", so a screen
    // reader hears both in one message; otherwise the landing is only spoken.
    private fun announceLanding(title: LocalizedText?, startUs: Long, lengthUs: Long) {
        val videoLengthUs = host.currentProject()?.videoLengthUs() ?: 0L
        val pastEnd = unheardTailUs(startUs, lengthUs, videoLengthUs) > OVERHANG_NOTICE_US
        host.showToast(if (pastEnd) ToastMessage.SoundPastEnd(title, startUs) else ToastMessage.SoundAdded(title, startUs), spokenOnly = !pastEnd)
    }
}
