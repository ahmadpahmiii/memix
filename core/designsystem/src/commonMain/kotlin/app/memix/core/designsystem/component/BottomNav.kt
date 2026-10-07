package app.memix.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import org.jetbrains.compose.resources.DrawableResource

/** [key] identifies the destination; [label] is already translated. */
class NavDestination(val key: Any, val label: String, val icon: DrawableResource)

/**
 * Home, Templates, Create, Sounds, Drafts. Create is the blue block in the middle and opens the Create sheet
 * instead of navigating, so it's the bar's only accent. Draws under the system navigation bar.
 */
@Composable
fun BottomNav(
    leading: List<NavDestination>,
    trailing: List<NavDestination>,
    selectedKey: Any?,
    onSelect: (NavDestination) -> Unit,
    createLabel: String,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(MemixColors.surface)
                drawLine(MemixColors.hairline, Offset(0f, 0f), Offset(size.width, 0f), MemixStroke.strokeHairline.toPx())
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(MemixSize.navHeight)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading.forEach { NavItem(it, it.key == selectedKey, onSelect) }
        CreateBlock(createLabel, onCreate)
        trailing.forEach { NavItem(it, it.key == selectedKey, onSelect) }
    }
}

@Composable
private fun NavItem(destination: NavDestination, selected: Boolean, onSelect: (NavDestination) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val color = if (selected) MemixColors.text else MemixColors.textSecondary
    Column(
        Modifier
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .width(64.dp)
            .defaultMinSize(minHeight = MemixSize.touchTarget)
            .selectable(selected, interactionSource, indication = null, role = Role.Tab) { onSelect(destination) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(destination.icon, contentDescription = null, tint = color)
        Text(destination.label, MemixTheme.type.caption, color = color, maxLines = 1)
    }
}

@Composable
private fun CreateBlock(label: String, onCreate: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val block = pressedColor(interactionSource, MemixColors.primary, MemixColors.primaryPressed)
    Box(
        Modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = MemixSize.touchTarget)
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onCreate)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .focusRing(interactionSource, MemixShapes.radiusMd)
                .size(56.dp, 40.dp)
                .clip(MemixShapes.radiusMd)
                .drawBehind { drawRect(block.value) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(MemixIcons.Create, contentDescription = null, tint = MemixColors.onPrimary)
        }
    }
}
