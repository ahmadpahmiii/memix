package app.memix.core.domain.sound

import app.memix.core.domain.SoundTab
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.domain.mysound.MY_SOUND_EVENT_ID
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef

/**
 * A sound the user chose in the editor's picker, ready to land: its file is on the phone and has been checked and
 * measured ([media]). [tab] is where in the picker it was chosen (`sound_add.tab`). [bundled] is true for the starter
 * pack, whose file every project shares; any other file is the library's download cache, which a project must copy
 * ([PlaceSoundInProjectUseCase]) before it points at it. A My sound (P3-07) is neither: its file is the user's own, in
 * `mysounds/`, and it is copied into the project the same way.
 */
data class PickedSound(
    val soundId: String,
    val title: LocalizedText,
    val media: MediaRef,
    val tab: SoundTab,
    val bundled: Boolean,
) {
    /** True for a sound from My sounds: it is not a library sound, so it never joins Recently used. */
    val isMine: Boolean get() = media.origin is MediaOrigin.MySound

    /** The `sound_id` of `sound_add` and `sound_preview`: the catalog id, or the constant [MY_SOUND_EVENT_ID] for a My sound. */
    val eventSoundId: String get() = if (isMine) MY_SOUND_EVENT_ID else soundId
}
