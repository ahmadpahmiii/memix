package app.memix.feature.mediaimport

import app.memix.core.domain.sound.StartingSound
import app.memix.core.domain.sound.PickedSound
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The first import of a new meme project (Create → Video meme or Photo meme): one state out, intents in, as the app root drives it. */
interface MemeImportViewModel {
    val state: StateFlow<ImportUiState>

    fun onIntent(intent: ImportIntent)

    /**
     * How the starting sound of the project that [ImportUiState.Done] names went (P3-15), until [ImportIntent.Finish];
     * [StartingSound.NONE] for a project made without one, and always for a photo meme.
     */
    val startingSound: StartingSound get() = StartingSound.NONE
}

/** The video meme's import, which can also start with a library sound chosen with Use (P3-15). */
interface SoundMemeImportViewModel : MemeImportViewModel {
    /** Each time a Use has run its course: the picker it opened is gone, with a pick or without, or it never opened. */
    val soundUseEnds: Flow<Unit>

    /**
     * A Use: waits for the media the user is about to pick, holding [sound] out of the cache cleanup. False (and
     * [soundUseEnds] fires) when an import is running.
     */
    fun beginWithSound(sound: PickedSound): Boolean
}

/** What the import sheet's words are about: a pick of videos and photos, or one photo (spec P2-01 → Starting a photo meme). */
enum class ImportWording { MEDIA, PHOTO }
