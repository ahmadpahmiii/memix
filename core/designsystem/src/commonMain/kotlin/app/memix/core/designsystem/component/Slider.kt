package app.memix.core.designsystem.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import kotlin.math.abs

private val ThumbDiameter = 22.dp

/**
 * Labeled slider with a live readout. Drag or tap the track to set; double-tap the thumb to reset to [defaultValue].
 * [step] is the keyboard and screen-reader increment.
 */
@Composable
fun Slider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueText: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    defaultValue: Float = valueRange.start,
    step: Float = (valueRange.endInclusive - valueRange.start) / 20f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val span = valueRange.endInclusive - valueRange.start
    fun fractionOf(v: Float) = ((v - valueRange.start) / span).coerceIn(0f, 1f)
    fun setFromX(x: Float, width: Float) = currentOnValueChange(valueRange.start + (x / width).coerceIn(0f, 1f) * span)

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, MemixTheme.type.label, Modifier.weight(1f))
            Text(valueText, MemixTheme.type.timecode, color = MemixColors.textSecondary)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .height(MemixSize.touchTarget)
                .focusRing(interactionSource, MemixShapes.radiusMd)
                .focusable(interactionSource = interactionSource)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val delta = when (event.key) {
                        Key.DirectionRight, Key.DirectionUp -> step
                        Key.DirectionLeft, Key.DirectionDown -> -step
                        else -> return@onKeyEvent false
                    }
                    currentOnValueChange((currentValue + delta).coerceIn(valueRange))
                    true
                }
                .semantics {
                    contentDescription = label
                    stateDescription = valueText
                    progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                    setProgress { target ->
                        currentOnValueChange(target.coerceIn(valueRange))
                        true
                    }
                }
                .pointerInput(valueRange) {
                    detectTapGestures(
                        onTap = { setFromX(it.x, size.width.toFloat()) },
                        onDoubleTap = { offset ->
                            val thumbX = fractionOf(currentValue) * size.width
                            if (abs(offset.x - thumbX) <= ThumbDiameter.toPx()) currentOnValueChange(defaultValue)
                        },
                    )
                }
                .pointerInput(valueRange) {
                    detectHorizontalDragGestures(
                        onDragStart = { setFromX(it.x, size.width.toFloat()) },
                        onHorizontalDrag = { change, _ -> setFromX(change.position.x, size.width.toFloat()) },
                    )
                }
                .drawBehind {
                    val railHeight = MemixSize.railHeight.toPx()
                    val thumbRadius = ThumbDiameter.toPx() / 2
                    val centerY = size.height / 2
                    val thumbX = fractionOf(currentValue) * size.width
                    val radius = CornerRadius(railHeight / 2)
                    drawRoundRect(MemixColors.hairline, Offset(0f, centerY - railHeight / 2), Size(size.width, railHeight), radius)
                    drawRoundRect(MemixColors.primary, Offset(0f, centerY - railHeight / 2), Size(thumbX, railHeight), radius)
                    drawCircle(MemixColors.selection, thumbRadius, Offset(thumbX.coerceIn(thumbRadius, size.width - thumbRadius), centerY))
                },
        ) {}
    }
}
