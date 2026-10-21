package app.memix.feature.videoeditor.captions

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.captionFontFamily
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.FontTile
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.Swatch
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Transform
import app.memix.feature.videoeditor.PositionStep
import app.memix.feature.videoeditor.TextPanel
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.a11y_degrees
import memix.feature.video_editor.generated.resources.color_black
import memix.feature.video_editor.generated.resources.color_blue
import memix.feature.video_editor.generated.resources.color_green
import memix.feature.video_editor.generated.resources.color_light_blue
import memix.feature.video_editor.generated.resources.color_orange
import memix.feature.video_editor.generated.resources.color_pink
import memix.feature.video_editor.generated.resources.color_purple
import memix.feature.video_editor.generated.resources.color_red
import memix.feature.video_editor.generated.resources.color_white
import memix.feature.video_editor.generated.resources.color_yellow
import memix.feature.video_editor.generated.resources.font_bubbly
import memix.feature.video_editor.generated.resources.font_classic
import memix.feature.video_editor.generated.resources.font_handwritten
import memix.feature.video_editor.generated.resources.font_modern
import memix.feature.video_editor.generated.resources.font_serif
import memix.feature.video_editor.generated.resources.outline_none
import memix.feature.video_editor.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.position_bigger
import memix.feature.video_editor.generated.resources.position_center
import memix.feature.video_editor.generated.resources.position_move
import memix.feature.video_editor.generated.resources.position_move_down
import memix.feature.video_editor.generated.resources.position_move_left
import memix.feature.video_editor.generated.resources.position_move_right
import memix.feature.video_editor.generated.resources.position_move_up
import memix.feature.video_editor.generated.resources.position_rotate_left
import memix.feature.video_editor.generated.resources.position_rotate_right
import memix.feature.video_editor.generated.resources.position_rotation
import memix.feature.video_editor.generated.resources.position_rotation_value
import memix.feature.video_editor.generated.resources.position_size
import memix.feature.video_editor.generated.resources.position_size_value
import memix.feature.video_editor.generated.resources.position_smaller
import memix.feature.video_editor.generated.resources.position_title
import memix.feature.video_editor.generated.resources.style_color
import memix.feature.video_editor.generated.resources.style_font
import memix.feature.video_editor.generated.resources.style_outline
import memix.feature.video_editor.generated.resources.style_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The selected caption's Style or Position panel ([panel]; spec P1-10 → Style panel, Position panel), over the
 * timeline and tool bar like every ToolPanel. [style] and [transform] are the caption's now; every tap applies at once.
 */
@Composable
internal fun TextPanels(panel: TextPanel?, style: CaptionStyle?, transform: Transform?, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val done = stringResource(Res.string.panel_done)
    val close = { onIntent(VideoEditorIntent.CloseTextPanel) }
    ToolPanel(panel == TextPanel.STYLE && style != null, stringResource(Res.string.style_title), done, close, modifier) {
        if (style != null) StyleRows(style, onIntent)
    }
    ToolPanel(panel == TextPanel.POSITION && transform != null, stringResource(Res.string.position_title), done, close, modifier) {
        if (transform != null) PositionRows(transform, onIntent)
    }
}

@Composable
private fun StyleRows(style: CaptionStyle, onIntent: (VideoEditorIntent) -> Unit) {
    // An unknown font id (a newer build's draft) selects no tile until the user picks one (spec → States).
    ChoiceRow(stringResource(Res.string.style_font)) {
        CaptionCatalog.fonts.forEachIndexed { index, font ->
            FontTile(stringResource(FONT_NAMES[index]), captionFontFamily(font.id), selected = style.fontId == font.id, onClick = {
                onIntent(VideoEditorIntent.SetCaptionFont(font.id))
            })
        }
    }
    ChoiceRow(stringResource(Res.string.style_color)) {
        CaptionCatalog.palette.forEachIndexed { index, color ->
            Swatch(Color(color.argb.toInt()), stringResource(COLOR_NAMES[index]), selected = style.color == color, onClick = {
                onIntent(VideoEditorIntent.SetCaptionColor(color.argb))
            })
        }
    }
    ChoiceRow(stringResource(Res.string.style_outline)) {
        Swatch(null, stringResource(Res.string.outline_none), selected = style.outlineColor == null, onClick = {
            onIntent(VideoEditorIntent.SetCaptionOutline(null))
        })
        CaptionCatalog.palette.forEachIndexed { index, color ->
            Swatch(Color(color.argb.toInt()), stringResource(COLOR_NAMES[index]), selected = style.outlineColor == color, onClick = {
                onIntent(VideoEditorIntent.SetCaptionOutline(color.argb))
            })
        }
    }
}

// A heading above a horizontally scrolling single-choice group named by it.
@Composable
private fun ChoiceRow(heading: String, choices: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
        Text(heading, MemixTheme.type.label, Modifier.semantics { heading() }, color = MemixColors.textSecondary)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).selectableGroup().semantics { contentDescription = heading },
            horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) { choices() }
    }
}

@Composable
private fun PositionRows(transform: Transform, onIntent: (VideoEditorIntent) -> Unit) {
    val step = { which: PositionStep -> onIntent(VideoEditorIntent.PositionStepped(which)) }
    val percent = (transform.scale * PERCENT).roundToInt()
    val degrees = transform.rotationDegrees.roundToInt()
    StepperRow(stringResource(Res.string.position_size), spokenState = stringResource(Res.string.position_size_value, percent)) {
        IconButton(MemixIcons.Minus, stringResource(Res.string.position_smaller), { step(PositionStep.SMALLER) })
        Readout(stringResource(Res.string.position_size_value, percent))
        IconButton(MemixIcons.Plus, stringResource(Res.string.position_bigger), { step(PositionStep.BIGGER) })
    }
    StepperRow(stringResource(Res.string.position_rotation), spokenState = pluralStringResource(Res.plurals.a11y_degrees, degrees, degrees)) {
        IconButton(MemixIcons.RotateLeft, stringResource(Res.string.position_rotate_left), { step(PositionStep.ROTATE_LEFT) })
        Readout(stringResource(Res.string.position_rotation_value, degrees))
        IconButton(MemixIcons.RotateRight, stringResource(Res.string.position_rotate_right), { step(PositionStep.ROTATE_RIGHT) })
    }
    StepperRow(stringResource(Res.string.position_move), spokenState = null) {
        // One drawing turned four ways (spec → Icons).
        MoveButton(PositionStep.MOVE_LEFT, Res.string.position_move_left, ARROW_LEFT, onIntent)
        MoveButton(PositionStep.MOVE_UP, Res.string.position_move_up, ARROW_UP, onIntent)
        MoveButton(PositionStep.MOVE_DOWN, Res.string.position_move_down, ARROW_DOWN, onIntent)
        MoveButton(PositionStep.MOVE_RIGHT, Res.string.position_move_right, ARROW_RIGHT, onIntent)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(stringResource(Res.string.position_center), { step(PositionStep.CENTER) })
    }
}

/**
 * A label at the start and its controls at the end; the controls wrap under the label when they don't fit (200%
 * font). [spokenState] is the row's value for screen readers ("Size, 120%").
 */
@Composable
private fun StepperRow(label: String, spokenState: String?, controls: @Composable () -> Unit) {
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

@Composable
private fun Readout(value: String) {
    Box(Modifier.widthIn(min = MemixSize.touchTarget), contentAlignment = Alignment.Center) {
        Text(value, MemixTheme.type.timecode, color = MemixColors.text)
    }
}

/**
 * A Move arrow: a tap moves 1%; held, it repeats after 400 ms, every 50 ms, shown live and committed as one step on
 * release (spec → Position panel). Keyboard: Enter or Space steps once.
 */
@Composable
private fun MoveButton(step: PositionStep, label: StringResource, turnDegrees: Float, onIntent: (VideoEditorIntent) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val container = pressedColor(interactionSource, MemixColors.surfaceRaised.copy(alpha = 0f), MemixColors.surfaceRaised)
    val name = stringResource(label)
    Box(
        Modifier
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .size(MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .drawBehind { drawRect(container.value) }
            .pointerInput(step) {
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
                            onIntent(VideoEditorIntent.PositionStepHeld(step, count))
                        }
                        waitForUpOrCancellation()
                        repeating.cancel()
                    }
                    interactionSource.tryEmit(PressInteraction.Release(press))
                    onIntent(VideoEditorIntent.PositionStepped(step, repeats))
                }
            }
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                val activates = event.key == Key.Enter || event.key == Key.Spacebar
                if (activates && event.type == KeyEventType.KeyUp) onIntent(VideoEditorIntent.PositionStepped(step))
                activates
            }
            .semantics {
                contentDescription = name
                role = Role.Button
                onClick { onIntent(VideoEditorIntent.PositionStepped(step)); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(MemixIcons.Arrow, contentDescription = null, tint = MemixColors.text, modifier = Modifier.rotate(turnDegrees))
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

private val FONT_NAMES = listOf(Res.string.font_classic, Res.string.font_modern, Res.string.font_bubbly, Res.string.font_handwritten, Res.string.font_serif)

private val COLOR_NAMES = listOf(
    Res.string.color_white, Res.string.color_black, Res.string.color_yellow, Res.string.color_orange, Res.string.color_red,
    Res.string.color_pink, Res.string.color_purple, Res.string.color_blue, Res.string.color_light_blue, Res.string.color_green,
)

private const val PERCENT = 100f
private const val HOLD_DELAY_MS = 400L
private const val REPEAT_EVERY_MS = 50L
private const val ARROW_RIGHT = 0f
private const val ARROW_DOWN = 90f
private const val ARROW_LEFT = 180f
private const val ARROW_UP = 270f
