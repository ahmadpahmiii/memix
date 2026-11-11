package app.memix.feature.videoeditor

import androidx.compose.runtime.Composable
import app.memix.core.domain.sound.PickedSound
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.sounds_title
import org.jetbrains.compose.resources.stringResource

/**
 * The sound picker the app plugs into the editor (spec P3-05): [visible] says whether the Meme sounds sheet is open, [title]
 * is its title, [onDismiss] closes it with nothing added, [onPick] hands over the sound the user added, with its file
 * ready, and [onUnplayable] says a sound would not play (the editor shows the toast above the scrim). The picker lives in `:feature:sounds`, which the composition root joins to this screen, so the two features never
 * depend on each other.
 */
typealias SoundPickerSlot = @Composable (visible: Boolean, title: String, onDismiss: () -> Unit, onPick: (PickedSound) -> Unit, onUnplayable: () -> Unit) -> Unit

/** The Meme sounds sheet (spec P1-06 → Meme sounds sheet, replaced by the picker in P3-05): the editor opens and closes it and lands what it picks. */
@Composable
internal fun MemeSoundsSheet(sounds: SoundsSheetUi, picker: SoundPickerSlot, onIntent: (VideoEditorIntent) -> Unit) {
    picker(
        sounds.isOpen,
        stringResource(Res.string.sounds_title),
        { onIntent(VideoEditorIntent.CloseSoundsSheet) },
        { picked -> onIntent(VideoEditorIntent.AddSound(picked)) },
        { onIntent(VideoEditorIntent.SoundUnplayable) },
    )
}
