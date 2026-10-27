package app.memix.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor

/** One option of a [SegmentedChoice]: its short [label] and, when it differs, what screen readers say ("30 frames per second"). */
data class Segment(val label: String, val spokenLabel: String = label)

/**
 * A row of two to five equal segments for picking one setting value (design/system/components/SegmentedChoice), such
 * as the export resolution. [groupName] is the heading that names the single-choice group. Every segment always looks
 * the same way and can be tapped: [onSelect] decides, so an option the phone can't do explains with a toast instead of
 * looking dimmed. A tap on the selected segment does nothing. When a label doesn't fit its equal width (200% font), the
 * segments wrap into two rows.
 */
@Composable
fun SegmentedChoice(segments: List<Segment>, selectedIndex: Int, onSelect: (Int) -> Unit, groupName: String, modifier: Modifier = Modifier) {
    val gap = MemixSpacing.space1
    Layout(
        content = {
            segments.forEachIndexed { index, segment ->
                SegmentItem(segment, selected = index == selectedIndex, onClick = { if (index != selectedIndex) onSelect(index) })
            }
        },
        modifier = modifier
            .clip(MemixShapes.radiusMd)
            .drawBehind { drawRect(MemixColors.canvas) }
            .padding(MemixSpacing.space1)
            .selectableGroup()
            .semantics { contentDescription = groupName },
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val widest = measurables.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        // One row while every label fits its equal share; else two rows, the first one longer.
        val perRow = if (widest <= (width - gapPx * (measurables.size - 1)) / measurables.size) measurables.size else (measurables.size + 1) / 2
        val rows = measurables.chunked(perRow)
        val placedRows = rows.map { row ->
            val segmentWidth = ((width - gapPx * (row.size - 1)) / row.size).coerceAtLeast(0)
            val placeables = row.map { it.measure(Constraints(minWidth = segmentWidth, maxWidth = segmentWidth)) }
            val rowHeight = placeables.maxOf { it.height }
            placeables.map { it to rowHeight }
        }
        val height = placedRows.sumOf { it.first().second } + gapPx * (placedRows.size - 1)
        layout(width, height) {
            var y = 0
            for (row in placedRows) {
                var x = 0
                val rowHeight = row.first().second
                for ((placeable, _) in row) {
                    placeable.place(x, y + (rowHeight - placeable.height) / 2)
                    x += placeable.width + gapPx
                }
                y += rowHeight + gapPx
            }
        }
    }
}

@Composable
private fun SegmentItem(segment: Segment, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val normal = if (selected) MemixColors.selection else MemixColors.canvas
    val container = pressedColor(interactionSource, normal, if (selected) normal else MemixColors.surfaceRaised)
    Box(
        Modifier
            .focusRing(interactionSource, MemixShapes.radiusSm)
            .defaultMinSize(minHeight = MemixSize.touchTarget)
            .clip(MemixShapes.radiusSm)
            .drawBehind { drawRect(container.value) }
            .selectable(selected, interactionSource, indication = null, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics { contentDescription = segment.spokenLabel }
            .padding(horizontal = MemixSpacing.space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(segment.label, MemixTheme.type.bodyStrong, color = if (selected) MemixColors.onPrimary else MemixColors.text, textAlign = TextAlign.Center)
    }
}
