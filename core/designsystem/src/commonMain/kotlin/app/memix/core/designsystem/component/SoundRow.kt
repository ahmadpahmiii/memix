package app.memix.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import memix.core.designsystem.generated.resources.Res
import memix.core.designsystem.generated.resources.sound_action_details
import memix.core.designsystem.generated.resources.sound_action_favorite
import memix.core.designsystem.generated.resources.sound_action_unfavorite
import memix.core.designsystem.generated.resources.sound_add
import memix.core.designsystem.generated.resources.sound_favorite
import memix.core.designsystem.generated.resources.sound_pause
import memix.core.designsystem.generated.resources.sound_play
import org.jetbrains.compose.resources.stringResource

/**
 * One meme sound. Sound controls are Memix blue everywhere, so this row may carry blue next to a screen's main action.
 * The title takes up to two lines; the meta line is never cut, because it can hold a license's credit. Without
 * [onFavoriteChange] the favorite heart isn't shown (the editor's sounds sheet has no Favorites list, P1-08); the heart
 * is a switch, so screen readers say "on" or "off" (P3-06). Without [onAdd] the blue block isn't shown (the Sounds
 * screen draws it only once the Use flow exists, P3-15).
 *
 * While playing or paused, the row sits one value step above its container: `surface` on `canvas`, or `surface-raised`
 * when it's [onSurface] (inside a sheet), where a `surface` band wouldn't show. A playing row shows [progress] under its
 * name; a paused one keeps it where it stopped, with the disc back to Play (P3-03).
 *
 * With [onShowDetails] the name block is the details button: a tap or a long press calls it (the long press adds a
 * haptic tick), and a screen reader hears "Show details" on it. [detailsFocus] lets the screen send focus back to
 * the block when the details sheet closes. Without it the block isn't clickable, as in the editor's sheet.
 */
@Composable
fun SoundRow(
    name: String,
    meta: String,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlayToggle: () -> Unit,
    onFavoriteChange: ((Boolean) -> Unit)?,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onSurface: Boolean = false,
    isPaused: Boolean = false,
    progress: SoundProgress? = null,
    onShowDetails: (() -> Unit)? = null,
    detailsFocus: FocusRequester? = null,
) {
    val playingBand = if (onSurface) MemixColors.surfaceRaised else MemixColors.surface
    Row(
        modifier
            .fillMaxWidth()
            .clip(MemixShapes.radiusMd)
            .background(if (isPlaying || isPaused) playingBand else Color.Transparent)
            .padding(horizontal = MemixSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SoundPlayDisc(name, isPlaying, onPlayToggle)
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = MemixSpacing.space2)
                .nameBlock(isFavorite, onFavoriteChange, onShowDetails, detailsFocus),
        ) {
            Text(name, MemixTheme.type.bodyStrong, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, MemixTheme.type.label, color = MemixColors.textSecondary)
            if (progress != null) ProgressUnderName(progress)
        }
        if (onFavoriteChange != null) SoundFavoriteHeart(name, isFavorite, onFavoriteChange)
        if (onAdd != null) AddSoundButton(stringResource(Res.string.sound_add, name), onAdd)
    }
}

@Composable
private fun ProgressUnderName(progress: SoundProgress) {
    val peaks = progress.peaks
    val modifier = Modifier.padding(top = MemixSpacing.space2)
    if (peaks.isNullOrEmpty()) SoundRail(progress.played, modifier) else SoundWaveform(peaks, progress.played, modifier)
}

/**
 * The name and meta read as one stop ("{name}, {meta}"). On a row with a heart it also offers the heart as a screen
 * reader action, so favoriting never depends on finding the small control (spec P3-02 → Accessibility → Row actions);
 * with [onShowDetails] the stop itself is the details button.
 */
@Composable
private fun Modifier.nameBlock(
    isFavorite: Boolean,
    onFavoriteChange: ((Boolean) -> Unit)?,
    onShowDetails: (() -> Unit)?,
    detailsFocus: FocusRequester?,
): Modifier {
    if (onFavoriteChange == null && onShowDetails == null) return this
    val favoriteLabel = stringResource(if (isFavorite) Res.string.sound_action_unfavorite else Res.string.sound_action_favorite)
    val actions = if (onFavoriteChange == null) emptyList() else listOf(CustomAccessibilityAction(favoriteLabel) { onFavoriteChange(!isFavorite); true })
    val withActions = semantics(mergeDescendants = true) { customActions = actions }
    if (onShowDetails == null) return withActions
    val detailsLabel = stringResource(Res.string.sound_action_details)
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    return withActions
        .then(if (detailsFocus != null) Modifier.focusRequester(detailsFocus) else Modifier)
        .focusRing(interactionSource, MemixShapes.radiusMd)
        .combinedClickable(
            interactionSource,
            indication = null,
            role = Role.Button,
            onClickLabel = detailsLabel,
            onLongClickLabel = detailsLabel,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onShowDetails()
            },
            onClick = onShowDetails,
        )
}

/** The 40 dp play disc in a 48 dp target: `primary-subtle` with Play, or `primary` with Pause while [isPlaying]. Also the details sheet's. */
@Composable
fun SoundPlayDisc(name: String, isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (isPlaying) Res.string.sound_pause else Res.string.sound_play, name)
    val interactionSource = remember { MutableInteractionSource() }
    val normal = if (isPlaying) MemixColors.primary else MemixColors.primarySubtle
    val disc = pressedColor(interactionSource, normal, if (isPlaying) MemixColors.primaryPressed else MemixColors.surfaceRaised)
    Box(
        modifier
            .size(MemixSize.touchTarget)
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .focusRing(interactionSource, MemixShapes.radiusFull)
                .size(MemixSize.soundDiscSize)
                .clip(MemixShapes.radiusFull)
                .drawBehind { drawRect(disc.value) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) MemixIcons.Pause else MemixIcons.Play,
                contentDescription = null,
                tint = if (isPlaying) MemixColors.onPrimary else MemixColors.primary,
                size = MemixSize.iconMedium,
            )
        }
    }
}

/** The favorite heart in a 48 dp target; a switch, so screen readers say "on" or "off" (P3-06). Also the details sheet's. */
@Composable
fun SoundFavoriteHeart(name: String, isFavorite: Boolean, onFavoriteChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.sound_favorite, name)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(MemixSize.touchTarget)
            .focusRing(interactionSource, MemixShapes.radiusFull)
            .toggleable(isFavorite, interactionSource, indication = null, role = Role.Switch, onValueChange = onFavoriteChange)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isFavorite) MemixIcons.HeartFilled else MemixIcons.Heart,
            contentDescription = null,
            tint = if (isFavorite) MemixColors.text else MemixColors.textSecondary,
        )
    }
}

@Composable
private fun AddSoundButton(description: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val block = pressedColor(interactionSource, MemixColors.primary, MemixColors.primaryPressed)
    Box(
        Modifier
            .size(MemixSize.touchTarget)
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .focusRing(interactionSource, MemixShapes.radiusMd)
                .size(36.dp)
                .clip(MemixShapes.radiusMd)
                .drawBehind { drawRect(block.value) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(MemixIcons.Create, contentDescription = null, tint = MemixColors.onPrimary, size = MemixSize.iconMedium)
        }
    }
}
