package app.memix.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing

private val TrackWidth = 52.dp
private val TrackHeight = 32.dp

/** The whole row is the touch target. The thumb carries the state, so the track never needs an outline. */
@Composable
fun Toggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = MemixTheme.reduceMotion
    val thumbTarget: Dp = when {
        pressed -> 26.dp
        checked -> 22.dp
        else -> 16.dp
    }
    val thumbSize by animateDpAsState(thumbTarget, if (reduceMotion) snap() else tween(MemixMotion.durationPress), label = "thumbSize")
    val thumbBias by animateFloatAsState(if (checked) 1f else -1f, if (reduceMotion) snap() else tween(MemixMotion.durationPress), label = "thumbBias")
    val trackColor by animateColorAsState(if (checked) MemixColors.primary else MemixColors.hairline, tween(MemixMotion.durationPress), label = "track")

    Row(
        modifier
            .fillMaxWidth()
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .defaultMinSize(minHeight = MemixSize.touchTarget)
            .toggleable(checked, interactionSource, indication = null, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, MemixTheme.type.body, Modifier.weight(1f))
        Box(
            Modifier
                .size(TrackWidth, TrackHeight)
                .clip(MemixShapes.radiusFull)
                .drawBehind { drawRect(trackColor) },
            contentAlignment = BiasAlignment(thumbBias, 0f),
        ) {
            Box(
                Modifier
                    .padding(horizontal = (TrackHeight - thumbSize) / 2)
                    .size(thumbSize)
                    .clip(MemixShapes.radiusFull)
                    .background(if (checked) MemixColors.onPrimary else MemixColors.textSecondary),
            )
        }
    }
}
