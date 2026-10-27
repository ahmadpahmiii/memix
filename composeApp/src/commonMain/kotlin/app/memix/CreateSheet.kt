package app.memix

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.DisplayText
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Sheet
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import memix.composeapp.generated.resources.Res
import memix.composeapp.generated.resources.create_photo_body
import memix.composeapp.generated.resources.create_photo_title
import memix.composeapp.generated.resources.create_template
import memix.composeapp.generated.resources.create_title
import memix.composeapp.generated.resources.create_video_body
import memix.composeapp.generated.resources.create_video_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/** Draws no scrim of its own: the app root's shared scrim stays up when a pick hands over to the import sheet. */
@Composable
internal fun CreateSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onVideoMeme: () -> Unit,
    onPhotoMeme: () -> Unit,
    onTemplates: () -> Unit,
) {
    Sheet(visible, onDismiss, stringResource(Res.string.create_title), heroTitle = true, drawScrim = false) {
        EditorRow(stringResource(Res.string.create_video_title), stringResource(Res.string.create_video_body), MemixIcons.Video, emphasized = true, onVideoMeme)
        EditorRow(stringResource(Res.string.create_photo_title), stringResource(Res.string.create_photo_body), MemixIcons.Photo, emphasized = false, onPhotoMeme)
        TemplateRow(stringResource(Res.string.create_template), onTemplates)
    }
}

/** Video meme is the sheet's one blue action; Photo meme stays neutral with a blue icon, as on Home. */
@Composable
private fun EditorRow(title: String, body: String, icon: DrawableResource, emphasized: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val container = pressedRowColor(interactionSource, emphasized)
    val content = if (emphasized) MemixColors.onPrimary else MemixColors.text
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing(interactionSource, MemixShapes.radiusLg)
            .defaultMinSize(minHeight = 88.dp)
            .clip(MemixShapes.radiusLg)
            .drawBehind { drawRect(container.value) }
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(MemixSpacing.space4),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (emphasized) MemixColors.onPrimary else MemixColors.primary, size = 32.dp)
        Column(Modifier.weight(1f)) {
            DisplayText(title, color = content)
            Text(body, MemixTheme.type.label, color = content)
        }
        Icon(MemixIcons.ChevronRight, contentDescription = null, tint = content)
    }
}

@Composable
private fun TemplateRow(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val container = pressedRowColor(interactionSource, emphasized = false)
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .defaultMinSize(minHeight = MemixSize.touchTarget + MemixSpacing.space2)
            .clip(MemixShapes.radiusMd)
            .drawBehind { drawRect(container.value) }
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MemixIcons.Templates, contentDescription = null, tint = MemixColors.text, size = 22.dp)
        Text(label, MemixTheme.type.bodyStrong, Modifier.weight(1f))
        Icon(MemixIcons.ChevronRight, contentDescription = null, tint = MemixColors.text, size = 20.dp)
    }
}

@Composable
private fun pressedRowColor(interactionSource: MutableInteractionSource, emphasized: Boolean) = pressedColor(
    interactionSource,
    if (emphasized) MemixColors.primary else MemixColors.surfaceRaised,
    if (emphasized) MemixColors.primaryPressed else MemixColors.hairline,
)
