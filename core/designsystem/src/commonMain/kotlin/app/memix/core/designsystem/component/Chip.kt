package app.memix.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor

/** Single-select category filter. Put a row of chips in a `selectableGroup()`. Never an action button. */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val normal = if (selected) MemixColors.selection else MemixColors.surfaceRaised
    val container = pressedColor(interactionSource, normal, if (selected) normal else MemixColors.hairline)
    Box(
        modifier
            .defaultMinSize(minHeight = MemixSize.touchTarget)
            .selectable(selected, interactionSource, indication = null, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .focusRing(interactionSource, MemixShapes.radiusFull)
                .defaultMinSize(minHeight = 36.dp)
                .clip(MemixShapes.radiusFull)
                .drawBehind { drawRect(container.value) }
                .padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space2),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, MemixTheme.type.label, color = if (selected) MemixColors.onPrimary else MemixColors.text, maxLines = 1)
        }
    }
}
