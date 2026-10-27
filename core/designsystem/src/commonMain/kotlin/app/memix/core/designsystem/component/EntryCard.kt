package app.memix.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.DisplayText
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import org.jetbrains.compose.resources.DrawableResource

/** Home's editor cards. Only the Video meme card is [emphasized] (blue): one blue card per screen. */
@Composable
fun EntryCard(
    title: String,
    description: String,
    icon: DrawableResource,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val container = pressedColor(
        interactionSource,
        if (emphasized) MemixColors.primary else MemixColors.surfaceRaised,
        if (emphasized) MemixColors.primaryPressed else MemixColors.hairline,
    )
    val content = if (emphasized) MemixColors.onPrimary else MemixColors.text
    Column(
        modifier
            .focusRing(interactionSource, MemixShapes.radiusLg)
            .defaultMinSize(minHeight = 132.dp)
            .clip(MemixShapes.radiusLg)
            .drawBehind { drawRect(container.value) }
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(MemixSpacing.space4),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, contentDescription = null, tint = if (emphasized) MemixColors.onPrimary else MemixColors.primary, size = 28.dp)
        Column(Modifier.padding(top = MemixSpacing.space4)) {
            DisplayText(title, color = content)
            Text(description, MemixTheme.type.label, color = content)
        }
    }
}
