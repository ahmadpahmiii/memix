package app.memix.feature.videoeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.component.Sheet
import app.memix.core.designsystem.component.SoundRow
import app.memix.core.ui.EmptyState
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.sound_meta
import memix.feature.video_editor.generated.resources.sounds_empty_body
import memix.feature.video_editor.generated.resources.sounds_empty_title
import memix.feature.video_editor.generated.resources.sounds_title
import org.jetbrains.compose.resources.stringResource

/**
 * The Meme sounds sheet (spec P1-06 → Meme sounds sheet): the starter pack in its order, one row per sound with play
 * and add. It opens at half the window so the preview's top stays in view, and grows when dragged up. Only one sound
 * plays at a time; adding one closes the sheet and lands the sound at the playhead.
 */
@Composable
internal fun MemeSoundsSheet(sounds: SoundsSheetUi, onIntent: (VideoEditorIntent) -> Unit) {
    Sheet(
        visible = sounds.isOpen,
        onDismiss = { onIntent(VideoEditorIntent.CloseSoundsSheet) },
        title = stringResource(Res.string.sounds_title),
        opensAtHalfHeight = true,
    ) {
        if (sounds.sounds.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(MemixSpacing.space6), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(Res.string.sounds_empty_title), stringResource(Res.string.sounds_empty_body))
            }
            return@Sheet
        }
        val language = LocalLocale.current.language
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1),
        ) {
            sounds.sounds.forEach { sound ->
                SoundRow(
                    name = sound.title.inLanguage(language),
                    meta = stringResource(Res.string.sound_meta, soundLengthText(sound.durationUs), sound.credit),
                    isPlaying = sound.id == sounds.playingId,
                    isFavorite = false,
                    onPlayToggle = { onIntent(VideoEditorIntent.ToggleSoundPreview(sound.id)) },
                    // No Favorites list in P1, so no heart (spec P1-06 → request 4).
                    onFavoriteChange = null,
                    onAdd = { onIntent(VideoEditorIntent.AddSound(sound.id)) },
                )
            }
        }
    }
}

/** "0:02": minutes and seconds, rounded up to whole seconds, at least 0:01 (the library's format on the Sounds board). */
private fun soundLengthText(durationUs: Long): String {
    val seconds = ((durationUs + MICROS_PER_SECOND - 1) / MICROS_PER_SECOND).coerceAtLeast(1)
    return "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

private const val MICROS_PER_SECOND = 1_000_000L
private const val SECONDS_PER_MINUTE = 60L
