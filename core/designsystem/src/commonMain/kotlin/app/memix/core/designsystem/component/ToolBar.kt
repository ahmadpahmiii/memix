package app.memix.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.DrawableResource

/** How a tool looks (`design/system/components/ToolBar/README.md`). */
enum class ToolTone {
    /** `text-secondary`. */
    Default,

    /** Delete: `danger`, always with its word. */
    Danger,

    /** Meme-sound UI (the Meme sounds tool): `primary`. */
    MemeSound,

    /** Its panel or sheet is open: `primary` on a `primary-subtle` highlight. */
    Active,
}

/**
 * One tool: an icon over its label. The label is also its spoken name, unless [contentDescription] says it
 * differently (Move here is spoken "Move to playhead"). A tool that can't act right now keeps its look; its
 * [onClick] explains why.
 */
@Immutable
class ToolBarItem(
    val label: String,
    val icon: DrawableResource,
    val onClick: () -> Unit,
    val tone: ToolTone = ToolTone.Default,
    val contentDescription: String? = null,
)

/** The clip mode's leading button, which closes the clip tools; icon only, so it has a spoken name. */
@Immutable
class ToolBarClose(val contentDescription: String, val onClick: () -> Unit)

/**
 * The editors' bottom tool bar (`design/system/components/ToolBar/README.md`): a `surface` bar at least
 * `toolbar-height` tall under a `hairline`, with [tools] left-aligned in a row that scrolls sideways when they
 * overflow. With [close] it's in clip mode: a 48 dp back button and a divider lead the tools.
 *
 * Each tool is 64 dp wide with its label in `caption`, up to two lines; at large font sizes a tool widens only when
 * a word can't break, and the bar grows taller instead of cutting labels.
 */
@Composable
fun ToolBar(tools: ImmutableList<ToolBarItem>, modifier: Modifier = Modifier, close: ToolBarClose? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MemixSize.toolbarHeight)
            .background(MemixColors.surface)
            .drawBehind { drawRect(MemixColors.hairline, Offset.Zero, Size(size.width, MemixStroke.strokeHairline.toPx())) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (close != null) {
            IconButton(MemixIcons.ArrowBack, close.contentDescription, close.onClick, Modifier.padding(start = MemixSpacing.space1))
            Box(Modifier.width(MemixStroke.strokeHairline).height(MemixSpacing.space6).background(MemixColors.hairline))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = MemixSpacing.space1)) {
            tools.forEach { tool -> ToolButton(tool) }
        }
    }
}

@Composable
private fun ToolButton(tool: ToolBarItem) {
    val interactionSource = remember { MutableInteractionSource() }
    val highlight = if (tool.tone == ToolTone.Active) MemixColors.primarySubtle else Color.Transparent
    // Pressed fills fade in from a see-through copy of the same gray, so the press never passes through black.
    val pressed = pressedColor(interactionSource, MemixColors.surfaceRaised.copy(alpha = 0f), MemixColors.surfaceRaised)
    val tint = toneColor(tool.tone)
    val spokenName = tool.contentDescription ?: tool.label
    Column(
        Modifier
            .focusRing(interactionSource, MemixShapes.radiusMd)
            // 64 dp, wider only when a word of the label can't break at large font sizes.
            .widthIn(min = ToolItemWidth)
            .width(IntrinsicSize.Min)
            .heightIn(min = MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .drawBehind {
                drawRect(highlight)
                drawRect(pressed.value)
            }
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = tool.onClick)
            .clearAndSetSemantics {
                contentDescription = spokenName
                role = Role.Button
                onClick { tool.onClick(); true }
            }
            .padding(horizontal = MemixSpacing.space1, vertical = MemixSpacing.space2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1),
    ) {
        Icon(tool.icon, contentDescription = null, tint = tint)
        Text(tool.label, MemixTheme.type.caption, Modifier.fillMaxWidth(), color = tint, textAlign = TextAlign.Center, maxLines = MAX_LABEL_LINES)
    }
}

private fun toneColor(tone: ToolTone): Color = when (tone) {
    ToolTone.Default -> MemixColors.textSecondary
    ToolTone.Danger -> MemixColors.danger
    ToolTone.MemeSound, ToolTone.Active -> MemixColors.primary
}

/** A tool item is 64 dp wide (ToolBar README): a touch target plus `space-2` on each side. */
private val ToolItemWidth: Dp = MemixSize.touchTarget + MemixSpacing.space2 * 2

private const val MAX_LABEL_LINES = 2
