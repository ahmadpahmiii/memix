package app.memix.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import kotlin.math.sqrt

/**
 * A content color choice (design/system/components/Swatch): a `swatch-size` circle of [color] in a `touch-target`
 * square, or, with [color] null, the "none" swatch (a `surface-raised` circle crossed by one line). [name] is spoken.
 * Selected: a white ring 2 dp outside the circle. Low-contrast colors (black on `surface`) get a `text-muted` hairline
 * ring so they don't vanish. Put the row in a `selectableGroup()`; a tap on the selected swatch does nothing.
 */
@Composable
fun Swatch(color: Color?, name: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressedBackdrop = pressedColor(interactionSource, MemixColors.surfaceRaised.copy(alpha = 0f), MemixColors.surfaceRaised)
    Box(
        modifier
            .size(MemixSize.touchTarget)
            .selectable(selected, interactionSource, indication = null, role = Role.RadioButton, onClick = { if (!selected) onClick() })
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(MemixSize.swatchSize)
                .focusRing(interactionSource, MemixShapes.radiusFull)
                .drawBehind {
                    val radius = size.minDimension / 2
                    // The selected ring sits one selection stroke (2 dp) outside the circle.
                    val ringGap = MemixStroke.strokeSelection.toPx()
                    val ringWidth = MemixStroke.strokeSelection.toPx()
                    // Behind the swatch: the pressed backdrop, the size of the selected ring.
                    drawCircle(pressedBackdrop.value, radius + ringGap + ringWidth)
                    if (selected) drawCircle(MemixColors.selection, radius + ringGap + ringWidth / 2, style = Stroke(ringWidth))
                    drawCircle(color ?: MemixColors.surfaceRaised, radius)
                    val hairline = MemixStroke.strokeHairline.toPx()
                    if (color == null || color.isLowContrastOnSurface()) {
                        drawCircle(MemixColors.textMuted, radius - hairline / 2, style = Stroke(hairline))
                    }
                    if (color == null) {
                        // One diagonal, top-right to bottom-left, inside the circle.
                        val reach = radius / sqrt(2f)
                        drawLine(
                            MemixColors.textSecondary,
                            Offset(center.x + reach, center.y - reach),
                            Offset(center.x - reach, center.y + reach),
                            strokeWidth = MemixStroke.strokeSelection.toPx(),
                        )
                    }
                },
        )
    }
}

// Under 3:1 against `surface` (WCAG 2.2 SC 1.4.11): in the caption palette only black.
private fun Color.isLowContrastOnSurface(): Boolean =
    (luminance() + CONTRAST_OFFSET) / (MemixColors.surface.luminance() + CONTRAST_OFFSET) < MIN_CONTRAST

private const val CONTRAST_OFFSET = 0.05f
private const val MIN_CONTRAST = 3f
