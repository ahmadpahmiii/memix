package app.memix.feature.videoeditor

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.DrawableResource

/*
 * Rows the editor's tool panels share (spec P1-10 → Style and Position panels, P1-11 → Canvas panel).
 */

/**
 * A heading above a horizontally scrolling single-choice group named by it. The row scrolls edge to edge: it reaches
 * past the panel's `space-4` side padding and keeps that padding inside the scroll, so at rest the first item lines up
 * with the heading and scrolled items slide out at the screen edge (ToolPanel README → Content; P1-10 review P1).
 */
@Composable
internal fun ChoiceRow(heading: String, choices: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
        Text(heading, MemixTheme.type.label, Modifier.semantics { heading() }, color = MemixColors.textSecondary)
        Row(
            Modifier
                .bleedPastPanelPadding()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MemixSpacing.space4)
                .selectableGroup()
                .semantics { contentDescription = heading },
            horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) { choices() }
    }
}

// Measures the content `space-4` wider on each side than the panel's content area and places it that far left.
private fun Modifier.bleedPastPanelPadding(): Modifier = layout { measurable, constraints ->
    val bleed = MemixSpacing.space4.roundToPx()
    val wide = constraints.maxWidth + 2 * bleed
    val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
}

/**
 * A label at the start and its controls at the end; the controls wrap under the label when they don't fit (200%
 * font). [spokenState] is the row's value for screen readers ("Size, 120%").
 */
@Composable
internal fun StepperRow(label: String, spokenState: String?, controls: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            MemixTheme.type.body,
            Modifier.semantics { if (spokenState != null) stateDescription = spokenState },
            color = MemixColors.textSecondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) { controls() }
    }
}

/** A stepper's value between its buttons, in `timecode`, at least `touch-target` wide. */
@Composable
internal fun Readout(value: String) {
    Box(Modifier.widthIn(min = MemixSize.touchTarget), contentAlignment = Alignment.Center) {
        Text(value, MemixTheme.type.timecode, color = MemixColors.text)
    }
}

/**
 * An icon button that repeats while held: a tap is one step ([onStep] with 1); held, it repeats after 400 ms, every
 * 50 ms, reporting the steps so far to [onHeld], and the release reports them all to [onStep] (one commit). Keyboard:
 * Enter or Space steps once. [turnDegrees] turns the icon (one arrow drawing for four directions).
 */
@Composable
internal fun RepeatingButton(
    icon: DrawableResource,
    label: String,
    onHeld: (count: Int) -> Unit,
    onStep: (count: Int) -> Unit,
    turnDegrees: Float = 0f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val container = pressedColor(interactionSource, MemixColors.surfaceRaised.copy(alpha = 0f), MemixColors.surfaceRaised)
    val currentOnHeld by rememberUpdatedState(onHeld)
    val currentOnStep by rememberUpdatedState(onStep)
    Box(
        Modifier
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .size(MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .drawBehind { drawRect(container.value) }
            .pointerInput(interactionSource) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    val tapped = withTimeoutOrNull(HOLD_DELAY_MS) { waitForUpOrCancellation() }
                    var repeats = 1
                    if (tapped == null) {
                        // Held: repeat until the finger lifts.
                        val repeating = scope.launchRepeats { count ->
                            repeats = count
                            currentOnHeld(count)
                        }
                        waitForUpOrCancellation()
                        repeating.cancel()
                    }
                    interactionSource.tryEmit(PressInteraction.Release(press))
                    currentOnStep(repeats)
                }
            }
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                val activates = event.key == Key.Enter || event.key == Key.Spacebar
                if (activates && event.type == KeyEventType.KeyUp) currentOnStep(1)
                activates
            }
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick { currentOnStep(1); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MemixColors.text, modifier = Modifier.rotate(turnDegrees))
    }
}

// From the hold's first step on, one more step every 50 ms; [onRepeat] gets the steps so far.
private fun CoroutineScope.launchRepeats(onRepeat: (count: Int) -> Unit) = launch {
    var count = 1
    while (true) {
        onRepeat(count)
        delay(REPEAT_EVERY_MS)
        count++
    }
}

private const val HOLD_DELAY_MS = 400L
private const val REPEAT_EVERY_MS = 50L
