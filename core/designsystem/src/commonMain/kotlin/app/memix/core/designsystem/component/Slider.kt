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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlin.math.ceil
import kotlin.math.floor

private val ThumbDiameter = 22.dp

/**
 * Labeled slider with a live readout. Drag or tap the track to set; double-tap the thumb to reset to [defaultValue].
 * [step] is the keyboard and screen-reader increment, and those steps land on its grid: from a dragged 147% with 10%
 * steps, up is 150% and down 140%, so a round level like 100% can always be reached without a pointer. [onValueChangeFinished] runs when a change is complete: a drag
 * lets go, a tap, a reset, a key or a screen-reader step; editors commit one undo step there.
 *
 * With [snapValue], a value within [snapDistance] of it becomes exactly [snapValue], with one `SegmentTick` haptic each
 * time it snaps (the Volume panel's 100%, spec P1-06), so a level that matters is easy to find again.
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
    onValueChangeFinished: () -> Unit = {},
    snapValue: Float? = null,
    snapDistance: Float = 0f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val haptics = LocalHapticFeedback.current
    val span = valueRange.endInclusive - valueRange.start
    fun fractionOf(v: Float) = ((v - valueRange.start) / span).coerceIn(0f, 1f)
    fun change(raw: Float) {
        val snapped = snapValue?.takeIf { abs(raw - it) <= snapDistance } ?: raw
        // One tick as it snaps, not on every move while it stays snapped.
        if (snapValue != null && snapped == snapValue && currentValue != snapValue) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        currentOnValueChange(snapped)
    }
    fun setFromX(x: Float, width: Float) = change(valueRange.start + (x / width).coerceIn(0f, 1f) * span)

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
                    val up = when (event.key) {
                        Key.DirectionRight, Key.DirectionUp -> true
                        Key.DirectionLeft, Key.DirectionDown -> false
                        else -> return@onKeyEvent false
                    }
                    currentOnValueChange(nextOnStepGrid(currentValue, step, up, valueRange))
                    currentOnValueChangeFinished()
                    true
                }
                .semantics {
                    contentDescription = label
                    stateDescription = valueText
                    progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                    // A screen reader asks for a step up or down; it lands on the grid like a key.
                    setProgress { target ->
                        currentOnValueChange(nextOnStepGrid(currentValue, step, up = target > currentValue, valueRange))
                        currentOnValueChangeFinished()
                        true
                    }
                }
                .pointerInput(valueRange) {
                    detectTapGestures(
                        onTap = {
                            setFromX(it.x, size.width.toFloat())
                            currentOnValueChangeFinished()
                        },
                        onDoubleTap = { offset ->
                            val thumbX = fractionOf(currentValue) * size.width
                            if (abs(offset.x - thumbX) <= ThumbDiameter.toPx()) {
                                currentOnValueChange(defaultValue)
                                currentOnValueChangeFinished()
                            }
                        },
                    )
                }
                .pointerInput(valueRange) {
                    detectHorizontalDragGestures(
                        onDragStart = { setFromX(it.x, size.width.toFloat()) },
                        onDragEnd = { currentOnValueChangeFinished() },
                        onDragCancel = { currentOnValueChangeFinished() },
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

/**
 * The next value on the [step] grid (counted from the range's start) above or below [value], within [range]. A value
 * already on the grid moves a whole step; float error within [GRID_TOLERANCE] of a step counts as on it.
 */
internal fun nextOnStepGrid(value: Float, step: Float, up: Boolean, range: ClosedFloatingPointRange<Float>): Float {
    if (step <= 0f) return value.coerceIn(range)
    val stepsFromStart = (value - range.start) / step
    val next = if (up) floor(stepsFromStart + GRID_TOLERANCE) + 1 else ceil(stepsFromStart - GRID_TOLERANCE) - 1
    return (range.start + next * step).coerceIn(range)
}

/** In steps: how close to a grid line a value counts as on it (1.0 after float maths can be 0.99999994). */
private const val GRID_TOLERANCE = 1e-3f
