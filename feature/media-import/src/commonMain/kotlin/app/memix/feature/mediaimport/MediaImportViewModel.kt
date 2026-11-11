package app.memix.feature.mediaimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.memix.core.domain.Outcome
import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.domain.media.SaveGalleryProjectUseCase
import app.memix.core.domain.project.CreateVideoProjectUseCase
import app.memix.core.domain.sound.PendingMemeSound
import app.memix.core.domain.sound.PickedSound
import app.memix.core.domain.sound.StartingSound
import app.memix.core.model.project.MediaOrigin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The first import of a new video project (Create → Video meme, spec P1-02): copies what the user picked through a
 * [MediaImportFlow], saves the new project once something went in, and hands it to the editor
 * ([ImportUiState.Done]; the app opens the editor, then sends [ImportIntent.Finish]).
 *
 * Lives as long as the app's screen (activity), so copying carries on across rotation and in the background.
 * The picker itself is opened by the app, which sends the result here as [ImportIntent.Picked]. One immutable
 * [state] out, intents in, like every MemixViewModel; the flow owns the state.
 *
 * It also owns the library sound a Use (P3-15, the Sounds tab) is carrying: [beginWithSound] holds it, the first save
 * puts it into the project and lets it go, and it is dropped whenever the pick ends without a project (nothing picked,
 * the picker could not open, Cancel while copying, Close on "None went in") and when this ViewModel is cleared.
 * "Pick again" keeps it.
 */
class MediaImportViewModel(
    private val createVideoProject: CreateVideoProjectUseCase,
    importMedia: ImportMediaUseCase,
    private val saveGalleryProject: SaveGalleryProjectUseCase,
    private val pendingSound: PendingMemeSound,
) : ViewModel(), SoundMemeImportViewModel {
    private val flow = MediaImportFlow(importMedia, viewModelScope)
    private var keepSoundForPickAgain = false
    private var soundUseOpen = false
    private val soundUseEnded = Channel<Unit>(Channel.BUFFERED)

    override val state: StateFlow<ImportUiState> = flow.state
    override val soundUseEnds: Flow<Unit> = soundUseEnded.receiveAsFlow()

    // Set by the first save, kept until the editor is open (Finish), so a rotation in between still opens it with the sound.
    override var startingSound = StartingSound.NONE
        private set

    init {
        viewModelScope.launch {
            var wasBusy = false
            flow.state.collect { current ->
                if (current == ImportUiState.Idle) {
                    // The pick ended: with a project the sound is already let go; without one, it goes too.
                    if (wasBusy && !keepSoundForPickAgain) pendingSound.drop()
                    keepSoundForPickAgain = false
                }
                wasBusy = current != ImportUiState.Idle
            }
        }
    }

    override fun beginWithSound(sound: PickedSound): Boolean {
        if (state.value != ImportUiState.Idle) {
            soundUseEnded.trySend(Unit)
            return false
        }
        soundUseOpen = true
        pendingSound.hold(sound)
        return true
    }

    override fun onIntent(intent: ImportIntent) {
        when (intent) {
            is ImportIntent.Picked -> picked(intent)
            ImportIntent.PickAgain -> {
                keepSoundForPickAgain = true
                flow.onIntent(ImportIntent.Close)
            }
            ImportIntent.PickerUnavailable -> {
                pendingSound.drop()
                flow.onIntent(intent)
                endSoundUse()
            }
            ImportIntent.Finish -> {
                startingSound = StartingSound.NONE
                flow.onIntent(intent)
            }
            ImportIntent.Cancel, ImportIntent.Continue, ImportIntent.Close, ImportIntent.AppResumed, ImportIntent.FreeUpSpaceUnavailable,
            -> flow.onIntent(intent)
        }
    }

    private fun picked(intent: ImportIntent.Picked) {
        // After Android restarted the app behind the picker, the Use that opened it is only in the saved id.
        intent.restoredSound?.let(pendingSound::adopt)
        if (intent.items.isEmpty()) pendingSound.drop()
        startImport(intent.items)
        endSoundUse()
    }

    private fun endSoundUse() {
        if (!soundUseOpen) return
        soundUseOpen = false
        soundUseEnded.trySend(Unit)
    }

    private fun startImport(picked: List<MediaOrigin>) {
        if (picked.isEmpty() || state.value != ImportUiState.Idle) return
        val project = createVideoProject()
        flow.start(picked, project.id) { added ->
            // Asked again after "Not enough space", so the sound stays pending until a save works.
            val wantsSound = pendingSound.pending != null
            when (val saved = saveGalleryProject(project, added, pendingSound.ready())) {
                is Outcome.Success -> {
                    startingSound = if (wantsSound && saved.value == StartingSound.NONE) StartingSound.MISSING else saved.value
                    pendingSound.drop()
                    LandingResult.Landed
                }
                is Outcome.Failure -> LandingResult.Failed(saved.error)
            }
        }
    }

    override fun onCleared() {
        pendingSound.drop()
        super.onCleared()
    }
}
