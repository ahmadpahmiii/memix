package app.memix.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import memix.core.designsystem.generated.resources.sound_add
import memix.core.designsystem.generated.resources.sound_favorite
import memix.core.designsystem.generated.resources.sound_pause
import memix.core.designsystem.generated.resources.sound_play
import org.jetbrains.compose.resources.stringResource

/** One meme sound. Sound controls are Memix blue everywhere, so this row may carry blue next to a screen's main action. */
@Composable
fun SoundRow(
    name: String,
    meta: String,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlayToggle: () -> Unit,
    onFavoriteChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MemixShapes.radiusMd)
            .background(if (isPlaying) MemixColors.surface else Color.Transparent)
            .padding(horizontal = MemixSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayDisc(isPlaying, stringResource(if (isPlaying) Res.string.sound_pause else Res.string.sound_play, name), onPlayToggle)
        Column(Modifier.weight(1f).padding(vertical = MemixSpacing.space2)) {
            Text(name, MemixTheme.type.bodyStrong, maxLines = 1)
            Text(meta, MemixTheme.type.label, color = MemixColors.textSecondary, maxLines = 1)
        }
        FavoriteToggle(isFavorite, stringResource(Res.string.sound_favorite, name), onFavoriteChange)
        AddSoundButton(stringResource(Res.string.sound_add, name), onAdd)
    }
}

@Composable
private fun PlayDisc(isPlaying: Boolean, description: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val normal = if (isPlaying) MemixColors.primary else MemixColors.primarySubtle
    val disc = pressedColor(interactionSource, normal, if (isPlaying) MemixColors.primaryPressed else MemixColors.surfaceRaised)
    Box(
        Modifier
            .size(MemixSize.touchTarget)
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .focusRing(interactionSource, MemixShapes.radiusFull)
                .size(40.dp)
                .clip(MemixShapes.radiusFull)
                .drawBehind { drawRect(disc.value) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) MemixIcons.Pause else MemixIcons.Play,
                contentDescription = null,
                tint = if (isPlaying) MemixColors.onPrimary else MemixColors.primary,
                size = 20.dp,
            )
        }
    }
}

@Composable
private fun FavoriteToggle(isFavorite: Boolean, description: String, onFavoriteChange: (Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(MemixSize.touchTarget)
            .focusRing(interactionSource, MemixShapes.radiusFull)
            .toggleable(isFavorite, interactionSource, indication = null, role = Role.Checkbox, onValueChange = onFavoriteChange)
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
            Icon(MemixIcons.Create, contentDescription = null, tint = MemixColors.onPrimary, size = 20.dp)
        }
    }
}
