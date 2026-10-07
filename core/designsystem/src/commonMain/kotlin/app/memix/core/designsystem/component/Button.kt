package app.memix.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import org.jetbrains.compose.resources.DrawableResource

/** Primary is the one main action on a screen; never two on the same screen. */
enum class ButtonVariant { Primary, Secondary, Danger, Ghost, Quiet }

@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Secondary,
    leadingIcon: DrawableResource? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = variant.colors()
    val container = pressedColor(interactionSource, colors.container, colors.containerPressed)
    val content by pressedColor(interactionSource, colors.content, colors.contentPressed)
    val horizontalPadding = if (variant == ButtonVariant.Quiet) MemixSpacing.space2 else MemixSpacing.space5
    Row(
        modifier
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .defaultMinSize(minHeight = MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .drawBehind { drawRect(container.value) }
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = MemixSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, tint = content, size = 20.dp)
        Text(text, MemixTheme.type.bodyStrong, color = content, textAlign = TextAlign.Center)
    }
}

private class ButtonColors(val container: Color, val containerPressed: Color, val content: Color, val contentPressed: Color)

private fun ButtonVariant.colors() = when (this) {
    ButtonVariant.Primary -> ButtonColors(MemixColors.primary, MemixColors.primaryPressed, MemixColors.onPrimary, MemixColors.onPrimary)
    ButtonVariant.Secondary -> ButtonColors(MemixColors.surfaceRaised, MemixColors.hairline, MemixColors.text, MemixColors.text)
    ButtonVariant.Danger -> ButtonColors(MemixColors.danger, MemixColors.dangerPressed, MemixColors.onPrimary, MemixColors.onPrimary)
    ButtonVariant.Ghost -> ButtonColors(Color.Transparent, MemixColors.surfaceRaised, MemixColors.text, MemixColors.text)
    ButtonVariant.Quiet -> ButtonColors(Color.Transparent, Color.Transparent, MemixColors.primary, MemixColors.primaryPressed)
}
