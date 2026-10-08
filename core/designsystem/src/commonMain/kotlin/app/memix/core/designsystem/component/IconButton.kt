package app.memix.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import org.jetbrains.compose.resources.DrawableResource

/**
 * An icon-only control with no fill, such as the editor's Close, Play and Undo (spec P1-04 → Interactions): a
 * `touch-target` square, the icon in `text`, and `surface-raised` behind it at `radius-md` while pressed. When
 * [icon] changes (Play to Pause) the two cross-fade over `duration-press`.
 *
 * Disabled ([enabled] false): the icon turns `text-muted`, taps do nothing, and screen readers say "disabled". Use
 * it only where greyed-out is the convention (undo and redo); elsewhere a control explains why it can't act.
 */
@Composable
fun IconButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Fades in from a see-through copy of the same gray, so the press never passes through black.
    val container = pressedColor(interactionSource, MemixColors.surfaceRaised.copy(alpha = 0f), MemixColors.surfaceRaised)
    val tint = if (enabled) MemixColors.text else MemixColors.textMuted
    Box(
        modifier
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .size(MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .drawBehind { drawRect(container.value) }
            .clickable(interactionSource, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(icon, animationSpec = tween(MemixMotion.durationPress), label = "icon") { shown ->
            Icon(shown, contentDescription = null, tint = tint)
        }
    }
}
