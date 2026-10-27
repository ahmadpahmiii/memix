package app.memix.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.ICON_CORNER
import app.memix.core.designsystem.ICON_SHAPE_SHARE
import app.memix.core.designsystem.ICON_SIZE
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor

/** How an [OptionTile] places its picture: over the label (a scrolling row of tiles) or beside it (a pair in a row). */
enum class OptionTileLayout { STACKED, INLINE }

/**
 * A single-choice tile with a small picture and a short label (design/system/components/OptionTile). [picture] draws
 * the 24 dp picture in the color it's given (an icon, or a shape drawn from data such as a ratio's rectangle). [spokenName]
 * replaces the label for screen readers when given; it starts with the label ("Fit, Shows the whole clip"). No state
 * description: it would replace the "selected" screen readers say (P1-11 review M2). Selected: a white fill,
 * label and picture in `on-primary`. Put the row in a `selectableGroup()` named by its heading; a tap on the selected
 * tile does nothing.
 */
@Composable
fun OptionTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    layout: OptionTileLayout,
    modifier: Modifier = Modifier,
    spokenName: String? = null,
    picture: @Composable (tint: Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val normal = if (selected) MemixColors.selection else MemixColors.surfaceRaised
    val container = pressedColor(interactionSource, normal, if (selected) normal else MemixColors.hairline)
    val labelColor = if (selected) MemixColors.onPrimary else MemixColors.text
    val pictureColor = if (selected) MemixColors.onPrimary else MemixColors.textSecondary
    val tile = modifier
        .focusRing(interactionSource, MemixShapes.radiusMd)
        .defaultMinSize(minWidth = MemixSize.touchTarget, minHeight = MemixSize.touchTarget)
        .clip(MemixShapes.radiusMd)
        .drawBehind { drawRect(container.value) }
        .selectable(selected, interactionSource, indication = null, role = Role.RadioButton, onClick = { if (!selected) onClick() })
        .semantics { if (spokenName != null) contentDescription = spokenName }
    val pictureBox: @Composable () -> Unit = { Box(Modifier.size(ICON_SIZE), contentAlignment = Alignment.Center) { picture(pictureColor) } }
    when (layout) {
        OptionTileLayout.STACKED -> Column(
            tile.padding(horizontal = MemixSpacing.space3, vertical = MemixSpacing.space2),
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            pictureBox()
            Text(label, MemixTheme.type.label, color = labelColor)
        }
        OptionTileLayout.INLINE -> Row(
            tile.padding(horizontal = MemixSpacing.space3),
            horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pictureBox()
            Text(label, MemixTheme.type.label, color = labelColor)
        }
    }
}

/**
 * A ratio drawn as itself, for an [OptionTile]'s picture: a solid rectangle of [aspectRatio] (width ÷ height) in [color],
 * its long side the central 20 dp of the 24 dp box, 1 dp corners (the icon grammar). Layout math, not an icon file.
 */
@Composable
fun RatioShape(aspectRatio: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(ICON_SIZE).drawBehind {
            val long = size.minDimension * ICON_SHAPE_SHARE
            val shape = if (aspectRatio >= 1f) Size(long, long / aspectRatio) else Size(long * aspectRatio, long)
            val topLeft = Offset((size.width - shape.width) / 2, (size.height - shape.height) / 2)
            drawRoundRect(color, topLeft, shape, CornerRadius(ICON_CORNER.toPx()))
        },
    )
}
