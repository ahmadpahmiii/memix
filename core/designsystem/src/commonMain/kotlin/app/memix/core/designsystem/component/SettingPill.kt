package app.memix.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor

/**
 * A current setting in an editor's top bar that opens where it's changed (design/system/components/SettingPill): [value]
 * in `timecode` on a `surface-raised` pill, in a target at least `touch-target` each way. [name] is spoken and contains
 * the value ("Canvas, 9:16"). While what it opens is open ([expanded]), it takes the active-tool look: `primary` on
 * `primary-subtle`. A tap opens or closes; screen readers hear it as expanded or collapsed.
 */
@Composable
fun SettingPill(value: String, name: String, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val normal = if (expanded) MemixColors.primarySubtle else MemixColors.surfaceRaised
    val container = pressedColor(interactionSource, normal, if (expanded) MemixColors.surfaceRaised else MemixColors.hairline)
    Box(
        modifier
            .defaultMinSize(minWidth = MemixSize.touchTarget, minHeight = MemixSize.touchTarget)
            .clickable(interactionSource, indication = null, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = name
                role = Role.Button
                onClick { onClick(); true }
                if (expanded) collapse { onClick(); true } else expand { onClick(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .focusRing(interactionSource, MemixShapes.radiusFull)
                .clip(MemixShapes.radiusFull)
                .drawBehind { drawRect(container.value) }
                .padding(horizontal = MemixSpacing.space3, vertical = MemixSpacing.space2),
            contentAlignment = Alignment.Center,
        ) {
            Text(value, MemixTheme.type.timecode, color = if (expanded) MemixColors.primary else MemixColors.text)
        }
    }
}
