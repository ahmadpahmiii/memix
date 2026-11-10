package app.memix.core.designsystem.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing

/** Four or more sibling views of one screen; the row scrolls when it overflows. Fewer than four: [SegmentedTabs]. */
@Composable
fun Tabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space5),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .focusRing(interactionSource, MemixShapes.radiusSm)
                    .defaultMinSize(minHeight = MemixSize.touchTarget)
                    .selectable(selected, interactionSource, indication = null, role = Role.Tab) { onSelect(index) }
                    .drawBehind {
                        if (!selected) return@drawBehind
                        val line = MemixStroke.strokeSelection.toPx()
                        drawRect(MemixColors.selection, Offset(0f, size.height - line), Size(size.width, line))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, MemixTheme.type.label, color = if (selected) MemixColors.text else MemixColors.textSecondary, maxLines = 1)
            }
        }
    }
}

/**
 * A two- or three-way switch between parallel views, such as Video / Photo. Tabs share the width equally; when a label is wider
 * than its share (200% font) the tabs size to their labels, each label whole on one line, and the row scrolls sideways with the
 * selected tab scrolled into view (design/system/components/SegmentedTabs/README.md).
 */
@Composable
fun SegmentedTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val lefts = remember(labels.size) { IntArray(labels.size) }
    val widths = remember(labels.size) { IntArray(labels.size) }
    BoxWithConstraints(
        modifier
            .clip(MemixShapes.radiusFull)
            .drawBehind { drawRect(MemixColors.canvas) }
            .padding(MemixSpacing.space1),
    ) {
        val share = maxWidth / labels.size
        val viewportPx = constraints.maxWidth
        LaunchedEffect(selectedIndex, labels) {
            val width = widths[selectedIndex]
            if (width > 0) scroll.animateScrollTo((lefts[selectedIndex] + width / 2 - viewportPx / 2).coerceAtLeast(0))
        }
        Row(Modifier.horizontalScroll(scroll).selectableGroup()) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .widthIn(min = share)
                        .onGloballyPositioned { lefts[index] = it.positionInParent().x.toInt(); widths[index] = it.size.width }
                        .focusRing(interactionSource, MemixShapes.radiusFull)
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(MemixShapes.radiusFull)
                        .drawBehind { drawRect(if (selected) MemixColors.surfaceRaised else Color.Transparent) }
                        .selectable(selected, interactionSource, indication = null, role = Role.Tab) { onSelect(index) }
                        .padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, MemixTheme.type.label, color = if (selected) MemixColors.text else MemixColors.textSecondary, maxLines = 1)
                }
            }
        }
    }
}
