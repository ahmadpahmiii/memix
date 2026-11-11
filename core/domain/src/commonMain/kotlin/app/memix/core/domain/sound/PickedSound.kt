package app.memix.core.domain.sound

import app.memix.core.domain.SoundTab
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.model.project.MediaRef

/**
 * A sound the user chose in the editor's picker, ready to land: its file is on the phone and has been checked and
 * measured ([media]). [tab] is where in the picker it was chosen (`sound_add.tab`). [bundled] is true for the starter
 * pack, whose file every project shares; any other file is the library's download cache, which a project must copy
 * ([PlaceSoundInProjectUseCase]) before it points at it.
 */
data class PickedSound(
    val soundId: String,
    val title: LocalizedText,
    val media: MediaRef,
    val tab: SoundTab,
    val bundled: Boolean,
)
