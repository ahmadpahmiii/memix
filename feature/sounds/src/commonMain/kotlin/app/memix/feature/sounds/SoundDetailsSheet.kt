package app.memix.feature.sounds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import app.memix.core.designsystem.component.FadingToast
import app.memix.core.designsystem.component.Sheet
import app.memix.core.designsystem.component.SoundFavoriteHeart
import app.memix.core.designsystem.component.SoundPlayDisc
import app.memix.core.designsystem.component.SoundRowLook
import app.memix.core.designsystem.component.SoundRail
import app.memix.core.designsystem.component.SoundWaveform
import app.memix.core.designsystem.component.Toast
import app.memix.core.ui.soundPositionText
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.sound_dl_downloading
import memix.core.ui.generated.resources.sound_dl_failed
import memix.core.ui.generated.resources.sound_dl_failed_storage
import memix.feature.sounds.generated.resources.Res
import memix.feature.sounds.generated.resources.sound_detail_offline_note
import memix.feature.sounds.generated.resources.sound_detail_not_saved_now
import memix.feature.sounds.generated.resources.sound_detail_saved
import memix.feature.sounds.generated.resources.sound_detail_unavailable_note
import memix.feature.sounds.generated.resources.sound_detail_category
import memix.feature.sounds.generated.resources.sound_detail_credit
import memix.feature.sounds.generated.resources.sound_detail_device
import memix.feature.sounds.generated.resources.sound_detail_included
import memix.feature.sounds.generated.resources.sound_detail_license
import memix.feature.sounds.generated.resources.sound_detail_not_saved
import memix.feature.sounds.generated.resources.sound_detail_source
import memix.feature.sounds.generated.resources.sound_detail_source_a11y
import memix.feature.sounds.generated.resources.sound_license_cc0
import memix.feature.sounds.generated.resources.sound_license_cc_by
import memix.feature.sounds.generated.resources.sound_license_licensed
import memix.feature.sounds.generated.resources.sound_license_own
import memix.feature.sounds.generated.resources.sound_license_royalty_free
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The sound details sheet (spec P3-03): the name and heart, a player with the large waveform, and where the sound
 * comes from (credit, license, source) so a CC BY credit is always one tap away. The app places it over the bottom nav,
 * as it does the other sheets. It is always composed, so opening it animates; its content is kept while it slides out.
 *
 * The Report pane (P3-10) swaps in for the body of this sheet, and "Use in a video meme" (P3-15) joins the bottom of
 * it; [DetailsBody] is the part they replace or extend.
 */
@Composable
fun SoundDetailsSheet(state: State<SoundsUiState>, clock: PlaybackClock, onIntent: (SoundsIntent) -> Unit, modifier: Modifier = Modifier) {
    // Each input is derived, so the sheet recomposes only when one of them changes, not on every keystroke in the search.
    val current by remember(state) { derivedStateOf { state.value.details } }
    val open = current != null
    val lastShown = remember { LastShown() }
    SideEffect { current?.let { lastShown.details = it } }
    val details = current ?: lastShown.details
    val soundId = details?.id
    val isFavorite by remember(state, soundId) { derivedStateOf { soundId != null && soundId in state.value.favoriteIds } }
    val playback by remember(state, soundId) { derivedStateOf { soundId?.let { state.value.playbackOf(it) } ?: RowPlayback.IDLE } }
    val peaks by remember(state, soundId) { derivedStateOf { soundId?.let { state.value.waveforms[it] } } }
    val look by remember(state, soundId) { derivedStateOf { soundId?.let { state.value.lookOf(it) } ?: SoundRowLook.NORMAL } }
    val downloadProgress by remember(state, soundId) { derivedStateOf { soundId?.let { state.value.downloadProgressOf(it) } } }
    val onPhone by remember(state, soundId) { derivedStateOf { soundId?.let { state.value.onPhoneOf(it) } ?: SoundOnPhone.NOT_SAVED } }
    val activeId by remember(state) { derivedStateOf { state.value.activeId } }
    val isPlaying by remember(state) { derivedStateOf { state.value.playingId != null } }
    val toast by remember(state) { derivedStateOf { state.value.toast.takeIf { open } } }
    val language = LocalLocale.current.language
    val name = details?.title?.inLanguage(language).orEmpty()
    // The sheet ticks only while it is up; the list behind it runs its own.
    val position = rememberPlaybackPosition(clock, activeId.takeIf { open }, isPlaying = open && isPlaying)
    val haptics = LocalHapticFeedback.current
    Box(modifier.fillMaxSize()) {
        Sheet(
            visible = open,
            onDismiss = { onIntent(SoundsIntent.CloseDetails) },
            title = name,
            titleMaxLines = TITLE_MAX_LINES,
            titleTrailing = details?.let { shown ->
                {
                    SoundFavoriteHeart(name, isFavorite, { on ->
                        if (on) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onIntent(SoundsIntent.ToggleFavorite(shown.id))
                    })
                }
            },
        ) {
            if (details != null) DetailsBody(details, name, playback, peaks, look, downloadProgress, onPhone, position, language, onIntent)
        }
        FadingToast(toast, Modifier.align(Alignment.BottomCenter).padding(MemixSpacing.space4)) { shown ->
            Toast(soundsToastText(shown))
        }
    }
}

// Not state: it only keeps the last details for the slide-out, and nothing reads it to draw.
private class LastShown {
    var details: SoundDetailsUi? = null
}

internal const val TITLE_MAX_LINES = 2

@Composable
internal fun ColumnScope.DetailsBody(
    details: SoundDetailsUi,
    name: String,
    playback: RowPlayback,
    peaks: List<Float>?,
    look: SoundRowLook,
    downloadProgress: Float?,
    onPhone: SoundOnPhone,
    position: State<Long>,
    language: String,
    onIntent: (SoundsIntent) -> Unit,
) {
    val isActive = playback != RowPlayback.IDLE
    val played = remember(isActive, position, details.durationUs) {
        { if (isActive) playedFraction(position.value, details.durationUs) else 0f }
    }
    val timeBelow = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    // A download, a failure or a missing connection replaces the time with a line of words under the player (spec P3-04).
    val status = playerStatusText(look)
    val statusColor = if (look == SoundRowLook.FAILED || look == SoundRowLook.FAILED_STORAGE) MemixColors.danger else MemixColors.textSecondary
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(MemixSpacing.space3), Alignment.CenterVertically) {
        SoundPlayDisc(name, playback == RowPlayback.PLAYING, { onIntent(SoundsIntent.ToggleDetailsPlay) }, look = look, disabledReason = status)
        Box(Modifier.weight(1f)) {
            when {
                // The rail under the player row is the download's progress.
                look == SoundRowLook.DOWNLOADING -> SoundRail({ downloadProgress ?: 0f }, Modifier.align(Alignment.Center))
                peaks.isNullOrEmpty() -> SoundRail(played, Modifier.align(Alignment.Center))
                else -> SoundWaveform(peaks, played, large = true)
            }
        }
        if (!timeBelow && status == null) ElapsedTime(position, isActive, details.length)
    }
    when {
        status != null -> Text(
            status,
            MemixTheme.type.label,
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            color = statusColor,
        )
        timeBelow -> ElapsedTime(position, isActive, details.length, Modifier.align(Alignment.End))
    }
    Column(
        Modifier.weight(1f, fill = false).padding(top = MemixSpacing.space2).verticalScroll(rememberScrollState()).padding(bottom = MemixSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
    ) {
        details.categoryName?.let { Fact(Res.string.sound_detail_category) { Text(it.inLanguage(language), MemixTheme.type.body) } }
        details.credit?.let { Fact(Res.string.sound_detail_credit) { Text(it, MemixTheme.type.body) } }
        Fact(Res.string.sound_detail_license) { Text(licenseText(details.licenseType), MemixTheme.type.body) }
        if (details.sourceUrl != null && details.sourceHost != null) {
            SourceFact(details.sourceUrl, details.sourceHost) { onIntent(SoundsIntent.SourceOpenFailed) }
        }
        Fact(Res.string.sound_detail_device) { OnPhone(onPhone, look) }
    }
}

/** "0:01 / 0:02". Only the whole second is read in composition, so this recomposes once a second, not per tick. */
@Composable
private fun ElapsedTime(position: State<Long>, isActive: Boolean, length: String, modifier: Modifier = Modifier) {
    val elapsed by remember(isActive, position) { derivedStateOf { soundPositionText(if (isActive) position.value else 0L) } }
    Text("$elapsed / $length", MemixTheme.type.timecode, modifier, color = MemixColors.textSecondary)
}

/** A label over its value; the two read as one stop ("Credit, Sad trombone by Name, Freesound"). */
@Composable
private fun Fact(label: StringResource, value: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(stringResource(label), MemixTheme.type.label, color = MemixColors.textSecondary)
        value()
    }
}

/** Label and host are one 48 dp block, so the whole thing is the link; it shows a focus ring and a pressed value step. */
@Composable
private fun SourceFact(url: String, host: String, onOpenFailed: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val description = stringResource(Res.string.sound_detail_source_a11y, host)
    val interactionSource = remember { MutableInteractionSource() }
    val background = pressedColor(interactionSource, Color.Transparent, MemixColors.surfaceRaised)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MemixSize.touchTarget)
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .clip(MemixShapes.radiusMd)
            .drawBehind { drawRect(background.value) }
            .clickable(interactionSource, indication = null, role = Role.Button) {
                runCatching { uriHandler.openUri(url) }.onFailure { onOpenFailed() }
            }
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(Res.string.sound_detail_source), MemixTheme.type.label, color = MemixColors.textSecondary)
        Text(host, MemixTheme.type.body, color = MemixColors.primary)
    }
}

/** The words that replace the time while the sound can't just play; null when it plays as normal. */
@Composable
private fun playerStatusText(look: SoundRowLook): String? = when (look) {
    SoundRowLook.NORMAL -> null
    SoundRowLook.DOWNLOADING -> stringResource(UiRes.string.sound_dl_downloading)
    SoundRowLook.FAILED -> stringResource(UiRes.string.sound_dl_failed)
    SoundRowLook.FAILED_STORAGE -> stringResource(UiRes.string.sound_dl_failed_storage)
    SoundRowLook.OFFLINE_NOT_SAVED -> stringResource(Res.string.sound_detail_offline_note)
    SoundRowLook.UNAVAILABLE -> stringResource(Res.string.sound_detail_unavailable_note)
}

@Composable
private fun OnPhone(onPhone: SoundOnPhone, look: SoundRowLook) {
    val saved = onPhone != SoundOnPhone.NOT_SAVED
    val words = when {
        onPhone == SoundOnPhone.INCLUDED -> stringResource(Res.string.sound_detail_included)
        onPhone == SoundOnPhone.SAVED -> stringResource(Res.string.sound_detail_saved)
        // "Saves when you play or add it" would be untrue here: say what is in the way.
        look == SoundRowLook.FAILED_STORAGE -> stringResource(UiRes.string.sound_dl_failed_storage)
        look == SoundRowLook.OFFLINE_NOT_SAVED || look == SoundRowLook.UNAVAILABLE -> stringResource(Res.string.sound_detail_not_saved_now)
        else -> stringResource(Res.string.sound_detail_not_saved)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2), verticalAlignment = Alignment.CenterVertically) {
        if (saved) Icon(MemixIcons.Check, contentDescription = null, tint = MemixColors.success, size = MemixSize.iconSmall)
        Text(words, MemixTheme.type.body, color = if (saved) MemixColors.text else MemixColors.textSecondary)
    }
}

/** The license in one plain sentence; a type the app has no sentence for shows as the catalog spells it. */
@Composable
private fun licenseText(licenseType: String): String {
    val sentence = when (licenseType) {
        "CC0" -> Res.string.sound_license_cc0
        "CC-BY" -> Res.string.sound_license_cc_by
        "royalty-free" -> Res.string.sound_license_royalty_free
        "own" -> Res.string.sound_license_own
        "licensed" -> Res.string.sound_license_licensed
        else -> return licenseType
    }
    return stringResource(sentence)
}
