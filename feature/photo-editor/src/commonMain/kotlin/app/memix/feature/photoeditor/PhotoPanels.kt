package app.memix.feature.photoeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.PhotoCatalog
import app.memix.core.ui.panels.CanvasColorSwatches
import app.memix.core.ui.panels.CanvasRatioRow
import app.memix.core.ui.panels.ChoiceRow
import app.memix.core.ui.panels.CustomRatioRows
import app.memix.core.ui.panels.PositionRows
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.canvas_background
import memix.core.ui.generated.resources.canvas_title
import memix.core.ui.generated.resources.panel_done
import memix.core.ui.generated.resources.position_size_value
import memix.core.ui.generated.resources.position_title
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.layers_locked_opacity
import memix.feature.photo_editor.generated.resources.layers_opacity
import memix.feature.photo_editor.generated.resources.photo_background_hidden
import memix.feature.photo_editor.generated.resources.tool_unlock
import org.jetbrains.compose.resources.stringResource

/**
 * The Opacity, Position and Canvas panels (spec P2-01 → Opacity panel, Position panel, Canvas panel), each a ToolPanel as
 * tall as its content up to [maxHeight] (40% of the window), anchored by the caller at the bottom. Every choice applies at
 * once as one undo step; the slider's in-between values go to [live] and are committed on release.
 */
@Composable
internal fun PhotoPanels(state: PhotoEditorUiState, live: PhotoLive, maxHeight: Dp, onIntent: (PhotoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val done = stringResource(UiRes.string.panel_done)
    val close = { onIntent(PhotoEditorIntent.ClosePanel) }
    val layer = state.selectedLayer
    ToolPanel(state.panel == PhotoPanel.OPACITY && layer != null, stringResource(Res.string.layers_opacity), done, close, modifier, contentMaxHeight = maxHeight) {
        if (layer != null) OpacityRow(layer, live, onIntent)
    }
    ToolPanel(state.panel == PhotoPanel.POSITION && layer != null, stringResource(UiRes.string.position_title), done, close, modifier, contentMaxHeight = maxHeight) {
        if (layer != null) {
            val shown = state.held?.takeIf { it.layerId == layer.id }?.transform ?: layer.transform
            PositionRows(
                shown,
                onStep = { step, repeats -> onIntent(PhotoEditorIntent.PositionStepped(layer.id, step, repeats)) },
                onHeld = { step, repeats -> onIntent(PhotoEditorIntent.PositionStepHeld(layer.id, step, repeats)) },
            )
        }
    }
    val canvas = state.canvas
    val canvasPanel = state.canvasPanel
    ToolPanel(state.panel == PhotoPanel.CANVAS && canvas != null && canvasPanel != null, stringResource(UiRes.string.canvas_title), done, close, modifier, contentMaxHeight = maxHeight) {
        if (canvas != null && canvasPanel != null) {
            CanvasRatioRow(canvas.ratio, canvasPanel.customTerms != null, onPick = { onIntent(PhotoEditorIntent.SetCanvasRatio(it)) })
            CustomRatioRows(
                canvasPanel.shownTerms,
                onHeld = { step, repeats -> onIntent(PhotoEditorIntent.CanvasStepHeld(step, repeats)) },
                onStepped = { step, repeats -> onIntent(PhotoEditorIntent.CanvasStepped(step, repeats)) },
            )
            BackgroundRow((canvas.background as? CanvasBackground.Solid)?.color?.argb, state.backgroundShows, onIntent)
        }
    }
}

/**
 * The opacity slider, live while dragging (drawn through [live], read in the draw phase) and committed on release. On a
 * locked layer it is a message with an Unlock button instead (spec → Layers panel: locked layer selected).
 */
@Composable
internal fun OpacityRow(layer: SceneLayerUi, live: PhotoLive, onIntent: (PhotoEditorIntent) -> Unit) {
    if (layer.locked) {
        LockedOpacityMessage(onUnlock = { onIntent(PhotoEditorIntent.SetLocked(layer.id, locked = false)) })
        return
    }
    val value = live.opacityOf() ?: layer.opacity
    Slider(
        label = stringResource(Res.string.layers_opacity),
        value = value,
        onValueChange = { live.setOpacity(layer.id, it) },
        valueText = stringResource(UiRes.string.position_size_value, (value * PERCENT).toInt().coerceIn(0, PERCENT)),
        valueRange = PhotoCatalog.OPACITY_RANGE,
        defaultValue = 1f,
        step = PhotoCatalog.OPACITY_STEP,
        onValueChangeFinished = { onIntent(PhotoEditorIntent.OpacityCommitted(layer.id, live.opacityOf() ?: layer.opacity)) },
        snapValue = 1f,
        snapDistance = PhotoCatalog.OPACITY_SNAP_TOLERANCE,
    )
}

@Composable
private fun LockedOpacityMessage(onUnlock: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3), verticalAlignment = Alignment.CenterVertically) {
        Icon(MemixIcons.Lock, contentDescription = null, tint = MemixColors.text)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
            Text(stringResource(Res.string.layers_locked_opacity), MemixTheme.type.body, color = MemixColors.textSecondary)
            Button(stringResource(Res.string.tool_unlock), onUnlock)
        }
    }
}

// The caption palette; the line under it explains why a color shows nothing when a photo covers the whole frame.
@Composable
private fun BackgroundRow(selectedArgb: Long?, backgroundShows: Boolean, onIntent: (PhotoEditorIntent) -> Unit) {
    ChoiceRow(stringResource(UiRes.string.canvas_background)) {
        CanvasColorSwatches(selectedArgb, onPick = { onIntent(PhotoEditorIntent.SetCanvasBackground(it)) })
    }
    if (!backgroundShows) {
        Text(stringResource(Res.string.photo_background_hidden), MemixTheme.type.caption, color = MemixColors.textSecondary)
    }
}

private const val PERCENT = 100
