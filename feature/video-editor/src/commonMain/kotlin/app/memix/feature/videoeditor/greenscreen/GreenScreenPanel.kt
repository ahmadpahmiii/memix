package app.memix.feature.videoeditor.greenscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.Swatch
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.ChromaKey
import app.memix.core.model.project.ChromaKeyCatalog
import app.memix.feature.videoeditor.GreenScreenUi
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.core.ui.generated.resources.position_size_value
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.green_screen_color
import memix.feature.video_editor.generated.resources.green_screen_off
import memix.feature.video_editor.generated.resources.green_screen_off_a11y
import memix.feature.video_editor.generated.resources.green_screen_softness
import memix.feature.video_editor.generated.resources.green_screen_strength
import memix.feature.video_editor.generated.resources.key_blue
import memix.feature.video_editor.generated.resources.key_green
import memix.feature.video_editor.generated.resources.key_pick
import memix.feature.video_editor.generated.resources.key_pick_click
import memix.feature.video_editor.generated.resources.key_picked
import memix.feature.video_editor.generated.resources.tool_green_screen
import org.jetbrains.compose.resources.stringResource

/**
 * The Green screen panel of the selected clip (spec P4-04 → The Green screen panel): Color to remove (the eyedropper, Green, Blue, and
 * the Picked color when it is neither), Strength and Edge softness, with Turn off beside Done in the header. A ToolPanel over the
 * timeline and tool bar, shown when [panel] isn't null. Its sliders move the matte live; each release is one undo step.
 */
@Composable
internal fun GreenScreenPanel(panel: GreenScreenUi?, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    // The last values stay on screen while the panel slides away.
    val key = panel?.key ?: ChromaKeyCatalog.DEFAULT
    val turnOff = stringResource(Res.string.green_screen_off)
    val turnOffSpoken = stringResource(Res.string.green_screen_off_a11y)
    ToolPanel(
        visible = panel != null,
        title = stringResource(Res.string.tool_green_screen),
        doneLabel = stringResource(UiRes.string.panel_done),
        onDone = { onIntent(VideoEditorIntent.CloseGreenScreen) },
        modifier = modifier,
        headerActions = {
            Button(turnOff, { onIntent(VideoEditorIntent.GreenScreenOff) }, Modifier.semantics { contentDescription = turnOffSpoken }, variant = ButtonVariant.Quiet)
        },
    ) {
        KeyColorRow(key, panel?.picking == true, onIntent)
        PercentSlider(
            label = stringResource(Res.string.green_screen_strength),
            value = key.strength,
            default = ChromaKeyCatalog.DEFAULT_STRENGTH,
            onChange = { onIntent(VideoEditorIntent.GreenScreenStrengthChanged(it)) },
            onReleased = { onIntent(VideoEditorIntent.GreenScreenReleased) },
        )
        PercentSlider(
            label = stringResource(Res.string.green_screen_softness),
            value = key.softness,
            default = ChromaKeyCatalog.DEFAULT_SOFTNESS,
            onChange = { onIntent(VideoEditorIntent.GreenScreenSoftnessChanged(it)) },
            onReleased = { onIntent(VideoEditorIntent.GreenScreenReleased) },
        )
    }
}

// The heading sits beside the swatches at 100% font and wraps above them when it doesn't fit (spec → Swatch row details).
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeyColorRow(key: ChromaKey, picking: Boolean, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val heading = stringResource(Res.string.green_screen_color)
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(heading, MemixTheme.type.body, color = MemixColors.textSecondary)
        FlowRow(
            // The swatches are 48 dp squares already; no gap between them keeps four in a row beside the heading at 360 dp.
            Modifier.selectableGroup().semantics { contentDescription = heading },
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val isGreen = key.colorArgb == ChromaKeyCatalog.GREEN_ARGB
            val isBlue = key.colorArgb == ChromaKeyCatalog.BLUE_ARGB
            Swatch(
                color = null,
                name = stringResource(Res.string.key_pick),
                selected = picking,
                onClick = { onIntent(VideoEditorIntent.GreenScreenPickToggled) },
                icon = MemixIcons.Eyedropper,
                clickLabel = stringResource(Res.string.key_pick_click),
                clickWhenSelected = true,
            )
            Swatch(Color(ChromaKeyCatalog.GREEN_ARGB), stringResource(Res.string.key_green), selected = isGreen && !picking, onClick = { onIntent(VideoEditorIntent.GreenScreenColor(ChromaKeyCatalog.GREEN_ARGB)) })
            Swatch(Color(ChromaKeyCatalog.BLUE_ARGB), stringResource(Res.string.key_blue), selected = isBlue && !picking, onClick = { onIntent(VideoEditorIntent.GreenScreenColor(ChromaKeyCatalog.BLUE_ARGB)) })
            if (!isGreen && !isBlue) {
                Swatch(Color(key.colorArgb), stringResource(Res.string.key_picked), selected = !picking, onClick = {})
            }
        }
    }
}

@Composable
private fun PercentSlider(label: String, value: Float, default: Float, onChange: (Float) -> Unit, onReleased: () -> Unit) {
    Slider(
        label = label,
        value = value,
        onValueChange = onChange,
        valueText = stringResource(UiRes.string.position_size_value, (value * PERCENT).roundToInt()),
        valueRange = 0f..1f,
        defaultValue = default,
        step = ChromaKeyCatalog.SLIDER_STEP,
        onValueChangeFinished = onReleased,
    )
}

private const val PERCENT = 100
