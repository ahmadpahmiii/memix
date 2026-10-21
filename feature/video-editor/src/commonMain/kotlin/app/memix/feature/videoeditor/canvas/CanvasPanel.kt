package app.memix.feature.videoeditor.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.OptionTileLayout
import app.memix.core.designsystem.component.RatioShape
import app.memix.core.designsystem.component.Swatch
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.CanvasCatalog
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.FrameFit
import app.memix.core.model.project.RatioTerms
import app.memix.feature.videoeditor.CanvasPanelUi
import app.memix.feature.videoeditor.CanvasUi
import app.memix.feature.videoeditor.ChoiceRow
import app.memix.feature.videoeditor.RatioStep
import app.memix.feature.videoeditor.Readout
import app.memix.feature.videoeditor.RepeatingButton
import app.memix.feature.videoeditor.StepperRow
import app.memix.feature.videoeditor.VideoEditorIntent
import app.memix.feature.videoeditor.captions.COLOR_NAMES
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.canvas_background
import memix.feature.video_editor.generated.resources.canvas_background_blur
import memix.feature.video_editor.generated.resources.canvas_background_hidden
import memix.feature.video_editor.generated.resources.canvas_clips
import memix.feature.video_editor.generated.resources.canvas_custom_height
import memix.feature.video_editor.generated.resources.canvas_custom_height_down
import memix.feature.video_editor.generated.resources.canvas_custom_height_up
import memix.feature.video_editor.generated.resources.canvas_custom_width
import memix.feature.video_editor.generated.resources.canvas_custom_width_down
import memix.feature.video_editor.generated.resources.canvas_custom_width_up
import memix.feature.video_editor.generated.resources.canvas_fill
import memix.feature.video_editor.generated.resources.canvas_fill_a11y_hint
import memix.feature.video_editor.generated.resources.canvas_fit
import memix.feature.video_editor.generated.resources.canvas_fit_a11y_hint
import memix.feature.video_editor.generated.resources.canvas_ratio
import memix.feature.video_editor.generated.resources.canvas_ratio_a11y
import memix.feature.video_editor.generated.resources.canvas_ratio_custom
import memix.feature.video_editor.generated.resources.canvas_shape_square
import memix.feature.video_editor.generated.resources.canvas_shape_tall
import memix.feature.video_editor.generated.resources.canvas_shape_wide
import memix.feature.video_editor.generated.resources.canvas_title
import memix.feature.video_editor.generated.resources.panel_done
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Canvas panel (spec docs/ux/specs/P1-11-canvas.md → Canvas panel) over the timeline and tool bar, like every
 * ToolPanel: the ratio, a custom ratio's steppers, Fit or Fill for every clip, and the background. [canvas] is the
 * project's now and [panel] this visit's own state; every tap applies at once.
 */
@Composable
internal fun CanvasPanel(panel: CanvasPanelUi?, canvas: CanvasUi?, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    ToolPanel(
        panel != null && canvas != null,
        stringResource(Res.string.canvas_title),
        stringResource(Res.string.panel_done),
        { onIntent(VideoEditorIntent.CloseCanvasPanel) },
        modifier,
    ) {
        if (panel != null && canvas != null) {
            RatioRow(canvas.ratio, panel.customTerms != null, onIntent)
            CustomRatioRows(panel.shownTerms, onIntent)
            ClipsRow(canvas.clipsFit, onIntent)
            BackgroundRow(canvas.backgroundArgb, canvas.backgroundShows, onIntent)
        }
    }
}

// Six tiles: the presets drawn as their own shape, then Custom. While Custom is picked, no preset is selected.
@Composable
private fun RatioRow(ratio: CanvasRatio, customPicked: Boolean, onIntent: (VideoEditorIntent) -> Unit) {
    ChoiceRow(stringResource(Res.string.canvas_ratio)) {
        CanvasCatalog.presets.forEach { (preset, size) ->
            val label = RatioTerms.of(size).toString()
            val aspect = size.widthPx.toFloat() / size.heightPx
            OptionTile(
                label,
                selected = !customPicked && ratio == preset,
                onClick = { onIntent(VideoEditorIntent.SetCanvasRatio(preset)) },
                layout = OptionTileLayout.STACKED,
                spokenName = stringResource(Res.string.canvas_ratio_a11y, label, stringResource(shapeWordOf(aspect))),
            ) { tint -> RatioShape(aspect, tint) }
        }
        OptionTile(
            stringResource(Res.string.canvas_ratio_custom),
            selected = customPicked || ratio == CanvasRatio.CUSTOM,
            onClick = { onIntent(VideoEditorIntent.SetCanvasRatio(CanvasRatio.CUSTOM)) },
            layout = OptionTileLayout.STACKED,
        ) { tint -> Icon(MemixIcons.RatioCustom, contentDescription = null, tint = tint) }
    }
}

private fun shapeWordOf(aspect: Float): StringResource = when {
    aspect < 1f -> Res.string.canvas_shape_tall
    aspect > 1f -> Res.string.canvas_shape_wide
    else -> Res.string.canvas_shape_square
}

// Width and Height steppers, only while Custom is picked; they fade in and out in 120 ms (spec → Motion).
@Composable
private fun CustomRatioRows(terms: RatioTerms?, onIntent: (VideoEditorIntent) -> Unit) {
    AnimatedVisibility(terms != null, enter = fadeIn(tween(MemixMotion.durationPress)), exit = fadeOut(tween(MemixMotion.durationPress))) {
        // Kept for the fade-out once the steppers are gone.
        val shown = terms ?: return@AnimatedVisibility
        Column(verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4)) {
            TermStepper(Res.string.canvas_custom_width, shown.width, RatioStep.WIDTH_DOWN, RatioStep.WIDTH_UP, Res.string.canvas_custom_width_down, Res.string.canvas_custom_width_up, onIntent)
            TermStepper(Res.string.canvas_custom_height, shown.height, RatioStep.HEIGHT_DOWN, RatioStep.HEIGHT_UP, Res.string.canvas_custom_height_down, Res.string.canvas_custom_height_up, onIntent)
        }
    }
}

@Composable
private fun TermStepper(
    label: StringResource,
    value: Int,
    down: RatioStep,
    up: RatioStep,
    downLabel: StringResource,
    upLabel: StringResource,
    onIntent: (VideoEditorIntent) -> Unit,
) {
    StepperRow(stringResource(label), spokenState = value.toString()) {
        StepButton(MemixIcons.Minus, stringResource(downLabel), down, onIntent)
        Readout(value.toString())
        StepButton(MemixIcons.Plus, stringResource(upLabel), up, onIntent)
    }
}

@Composable
private fun StepButton(icon: DrawableResource, label: String, step: RatioStep, onIntent: (VideoEditorIntent) -> Unit) {
    RepeatingButton(
        icon,
        label,
        onHeld = { count -> onIntent(VideoEditorIntent.CanvasStepHeld(step, count)) },
        onStep = { count -> onIntent(VideoEditorIntent.CanvasStepped(step, count)) },
    )
}

// "Clips" with Fit and Fill at the end; they wrap under the label when they don't fit (200% font). Mixed: neither.
@Composable
private fun ClipsRow(fit: FrameFit?, onIntent: (VideoEditorIntent) -> Unit) {
    val heading = stringResource(Res.string.canvas_clips)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(heading, MemixTheme.type.body, color = MemixColors.textSecondary)
        Row(
            Modifier.selectableGroup().semantics { contentDescription = heading },
            horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        ) {
            FitTile(FrameFit.FIT, fit, Res.string.canvas_fit, Res.string.canvas_fit_a11y_hint, onIntent)
            FitTile(FrameFit.FILL, fit, Res.string.canvas_fill, Res.string.canvas_fill_a11y_hint, onIntent)
        }
    }
}

@Composable
private fun FitTile(option: FrameFit, current: FrameFit?, label: StringResource, hint: StringResource, onIntent: (VideoEditorIntent) -> Unit) {
    OptionTile(
        stringResource(label),
        selected = current == option,
        onClick = { onIntent(VideoEditorIntent.SetClipsFit(option)) },
        layout = OptionTileLayout.INLINE,
        hint = stringResource(hint),
    ) { tint -> Icon(if (option == FrameFit.FIT) MemixIcons.Fit else MemixIcons.Fill, contentDescription = null, tint = tint) }
}

// Blur first, then the caption palette; the line under it explains when no clip lets the background show.
@Composable
private fun BackgroundRow(argb: Long?, backgroundShows: Boolean, onIntent: (VideoEditorIntent) -> Unit) {
    ChoiceRow(stringResource(Res.string.canvas_background)) {
        Swatch(
            null,
            stringResource(Res.string.canvas_background_blur),
            selected = argb == null,
            onClick = { onIntent(VideoEditorIntent.SetCanvasBackground(null)) },
            icon = MemixIcons.Blur,
        )
        CaptionCatalog.palette.forEachIndexed { index, color ->
            Swatch(Color(color.argb.toInt()), stringResource(COLOR_NAMES[index]), selected = argb == color.argb, onClick = {
                onIntent(VideoEditorIntent.SetCanvasBackground(color.argb))
            })
        }
    }
    if (!backgroundShows) Text(stringResource(Res.string.canvas_background_hidden), MemixTheme.type.caption, color = MemixColors.textSecondary)
}
