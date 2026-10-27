package app.memix.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate

/**
 * The 2 dp white focus ring, 2 dp outside the control, drawn past the bounds so layout never shifts.
 * Touch taps don't focus clickables, so it shows only for keyboard and switch access.
 */
@Composable
fun Modifier.focusRing(interactionSource: InteractionSource, shape: Shape): Modifier {
    val focused = interactionSource.collectIsFocusedAsState()
    return drawWithContent {
        drawContent()
        if (!focused.value) return@drawWithContent
        val inset = (MemixStroke.strokeFocus * 1.5f).toPx()
        val ringSize = Size(size.width + inset * 2, size.height + inset * 2)
        translate(-inset, -inset) {
            drawOutline(
                shape.createOutline(ringSize, layoutDirection, this),
                MemixColors.focusRing,
                style = Stroke(MemixStroke.strokeFocus.toPx()),
            )
        }
    }
}

/** Pressed fills switch color in 120 ms; nothing moves (DESIGN_SYSTEM → States). */
@Composable
fun pressedColor(interactionSource: InteractionSource, normal: Color, pressed: Color): State<Color> {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateColorAsState(if (isPressed) pressed else normal, tween(MemixMotion.durationPress), label = "pressed")
}
