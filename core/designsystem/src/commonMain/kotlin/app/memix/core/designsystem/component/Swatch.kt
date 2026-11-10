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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.jetbrains.compose.resources.DrawableResource

/**
 * A content color choice (design/system/components/Swatch): a `swatch-size` circle of [color] in a `touch-target`
 * square, or, with [color] null, the "none" swatch (a `surface-raised` circle crossed by one line), or with [icon] too,
 * an icon variant such as the canvas's "Blur" (a `surface-raised` circle with [icon] at `icon-small`, in [iconTint]). The
 * Image variant (P2-12) is the icon variant until a picture is set, then the [picture] itself, center-cropped into the
 * circle. [name] is spoken; [clickLabel], when given, is what "double-tap to ..." says. Selected: a white ring 2 dp
 * outside the circle. Low-contrast colors (black on `surface`) get a `text-muted` hairline
 * ring so they don't vanish. Put the row in a `selectableGroup()`; a tap on the selected swatch does nothing.
 */
@Composable
fun Swatch(
    color: Color?,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    iconTint: Color = MemixColors.textSecondary,
    picture: ImageBitmap? = null,
    clickLabel: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressedBackdrop = pressedColor(interactionSource, MemixColors.surfaceRaised.copy(alpha = 0f), MemixColors.surfaceRaised)
    Box(
        modifier
            .size(MemixSize.touchTarget)
            .selectable(selected, interactionSource, indication = null, role = Role.RadioButton, onClick = { if (!selected) onClick() })
            .semantics {
                contentDescription = name
                if (clickLabel != null) onClick(label = clickLabel) { if (!selected) onClick(); true }
            },
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
                    if (picture != null) {
                        drawCroppedInCircle(picture, radius)
                        return@drawBehind
                    }
                    if (color == null && icon != null) return@drawBehind
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
            contentAlignment = Alignment.Center,
        ) {
            if (color == null && icon != null && picture == null) Icon(icon, contentDescription = null, tint = iconTint, size = MemixSize.iconSmall)
        }
    }
}

// The picture scaled to cover the circle's square, centered, then cut to the circle: the swatch's whole meaning (spec P2-12 → The Image swatch).
private fun DrawScope.drawCroppedInCircle(picture: ImageBitmap, radius: Float) {
    val side = radius * 2
    val scale = max(side / picture.width, side / picture.height)
    val shownWidth = picture.width * scale
    val shownHeight = picture.height * scale
    val circle = Path().apply { addOval(Rect(center, radius)) }
    clipPath(circle) {
        drawImage(
            picture,
            srcSize = IntSize(picture.width, picture.height),
            dstOffset = IntOffset((center.x - shownWidth / 2).roundToInt(), (center.y - shownHeight / 2).roundToInt()),
            dstSize = IntSize(shownWidth.roundToInt(), shownHeight.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}

// Under 3:1 against `surface` (WCAG 2.2 SC 1.4.11): in the caption palette only black.
private fun Color.isLowContrastOnSurface(): Boolean =
    (luminance() + CONTRAST_OFFSET) / (MemixColors.surface.luminance() + CONTRAST_OFFSET) < MIN_CONTRAST

private const val CONTRAST_OFFSET = 0.05f
private const val MIN_CONTRAST = 3f
