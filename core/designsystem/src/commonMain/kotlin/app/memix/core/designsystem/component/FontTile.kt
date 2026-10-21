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
import androidx.compose.ui.text.font.FontFamily
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor

/**
 * A caption font choice (design/system/components/FontTile): [name] written in that font ([fontFamily], see
 * captionFontFamily), at `caption-sample`. Selected: a white fill, the name in `on-primary`. Put the row in a
 * `selectableGroup()` named by its heading; a tap on the selected tile does nothing.
 */
@Composable
fun FontTile(name: String, fontFamily: FontFamily, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val normal = if (selected) MemixColors.selection else MemixColors.surfaceRaised
    val container = pressedColor(interactionSource, normal, if (selected) normal else MemixColors.hairline)
    Box(
        modifier
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .defaultMinSize(minHeight = MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .drawBehind { drawRect(container.value) }
            .selectable(selected, interactionSource, indication = null, role = Role.RadioButton, onClick = { if (!selected) onClick() })
            .padding(horizontal = MemixSpacing.space4),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            MemixTheme.type.captionSample.copy(fontFamily = fontFamily),
            color = if (selected) MemixColors.onPrimary else MemixColors.text,
        )
    }
}
