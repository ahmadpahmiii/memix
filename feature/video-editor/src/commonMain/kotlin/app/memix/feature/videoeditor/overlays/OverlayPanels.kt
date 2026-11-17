package app.memix.feature.videoeditor.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.OverlayCatalog
import app.memix.core.model.project.Transform
import app.memix.core.ui.panels.PositionRows
import app.memix.feature.videoeditor.OverlayPanel
import app.memix.feature.videoeditor.OverlayPanelUi
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.layers_opacity
import memix.core.ui.generated.resources.panel_done
import memix.core.ui.generated.resources.position_size_value
import memix.core.ui.generated.resources.position_title
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.layer_order_back
import memix.feature.video_editor.generated.resources.layer_order_forward
import memix.feature.video_editor.generated.resources.layer_order_hint
import memix.feature.video_editor.generated.resources.tool_layer_order
import org.jetbrains.compose.resources.stringResource

/**
 * The selected overlay's Opacity, Position and Layer order panels (spec P4-01b), each a ToolPanel over the timeline and tool bar,
 * shown when [panel] says so. [transform] is the overlay's place now, with a held arrow's or a commit's stand-in applied.
 */
@Composable
internal fun OverlayPanels(panel: OverlayPanelUi?, transform: Transform?, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val done = stringResource(UiRes.string.panel_done)
    val close = { onIntent(VideoEditorIntent.CloseOverlayPanel) }
    val shown = panel?.panel
    ToolPanel(shown == OverlayPanel.OPACITY, stringResource(UiRes.string.layers_opacity), done, close, modifier) {
        // The slider's last value stays on screen while the panel slides away.
        val opacity = panel?.opacity ?: 1f
        Slider(
            label = stringResource(UiRes.string.layers_opacity),
            value = opacity,
            onValueChange = { onIntent(VideoEditorIntent.OverlayOpacityChanged(it)) },
            valueText = stringResource(UiRes.string.position_size_value, (opacity * PERCENT).roundToInt()),
            valueRange = OverlayCatalog.OPACITY_RANGE,
            defaultValue = 1f,
            step = OPACITY_STEP,
            onValueChangeFinished = { onIntent(VideoEditorIntent.OverlayOpacityReleased) },
            snapValue = 1f,
            snapDistance = OPACITY_SNAP,
        )
    }
    ToolPanel(shown == OverlayPanel.POSITION && transform != null, stringResource(UiRes.string.position_title), done, close, modifier) {
        if (transform != null) {
            PositionRows(
                transform,
                onStep = { step, repeats -> onIntent(VideoEditorIntent.PositionStepped(step, repeats)) },
                onHeld = { step, repeats -> onIntent(VideoEditorIntent.PositionStepHeld(step, repeats)) },
            )
        }
    }
    ToolPanel(shown == OverlayPanel.ORDER, stringResource(Res.string.tool_layer_order), done, close, modifier) {
        LayerOrderContent(onIntent)
    }
}

// The two actions first, so a large font can't push them under the fold, then the hint (spec → Layer order panel).
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerOrderContent(onIntent: (VideoEditorIntent) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
        Button(stringResource(Res.string.layer_order_forward), { onIntent(VideoEditorIntent.OverlayOrder(forward = true)) })
        Button(stringResource(Res.string.layer_order_back), { onIntent(VideoEditorIntent.OverlayOrder(forward = false)) })
    }
    Spacer(Modifier.height(MemixSpacing.space4))
    Text(stringResource(Res.string.layer_order_hint), MemixTheme.type.label, color = MemixColors.textSecondary)
}

private const val PERCENT = 100f

/** Keyboard and TalkBack steps: 10%, so 100% is reachable. */
private const val OPACITY_STEP = 0.1f

/** The slider snaps to 100% from within ±3%. */
private const val OPACITY_SNAP = 0.03f
