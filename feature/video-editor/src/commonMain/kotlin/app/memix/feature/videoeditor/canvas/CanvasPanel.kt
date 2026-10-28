package app.memix.feature.videoeditor.canvas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.OptionTileLayout
import app.memix.core.designsystem.component.Swatch
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.FrameFit
import app.memix.core.ui.panels.CanvasColorSwatches
import app.memix.core.ui.panels.CanvasRatioRow
import app.memix.core.ui.panels.ChoiceRow
import app.memix.core.ui.panels.CustomRatioRows
import app.memix.feature.videoeditor.CanvasPanelUi
import app.memix.feature.videoeditor.CanvasUi
import app.memix.feature.videoeditor.VideoEditorIntent
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.canvas_background
import memix.core.ui.generated.resources.canvas_ratio_a11y
import memix.core.ui.generated.resources.canvas_title
import memix.core.ui.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.canvas_background_blur
import memix.feature.video_editor.generated.resources.canvas_background_hidden
import memix.feature.video_editor.generated.resources.canvas_clips
import memix.feature.video_editor.generated.resources.canvas_fill
import memix.feature.video_editor.generated.resources.canvas_fill_a11y_hint
import memix.feature.video_editor.generated.resources.canvas_fit
import memix.feature.video_editor.generated.resources.canvas_fit_a11y_hint
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
        stringResource(UiRes.string.canvas_title),
        stringResource(UiRes.string.panel_done),
        { onIntent(VideoEditorIntent.CloseCanvasPanel) },
        modifier,
    ) {
        if (panel != null && canvas != null) {
            CanvasRatioRow(canvas.ratio, panel.customTerms != null, onPick = { onIntent(VideoEditorIntent.SetCanvasRatio(it)) })
            CustomRatioRows(
                panel.shownTerms,
                onHeld = { step, repeats -> onIntent(VideoEditorIntent.CanvasStepHeld(step, repeats)) },
                onStepped = { step, repeats -> onIntent(VideoEditorIntent.CanvasStepped(step, repeats)) },
            )
            ClipsRow(canvas.clipsFit, onIntent)
            BackgroundRow(canvas.backgroundArgb, canvas.backgroundShows, onIntent)
        }
    }
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
        // The pair wraps one under the other too, before a long label would break mid-word (P1-11 review P3).
        FlowRow(
            Modifier.selectableGroup().semantics { contentDescription = heading },
            horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
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
        spokenName = stringResource(UiRes.string.canvas_ratio_a11y, stringResource(label), stringResource(hint)),
    ) { tint -> Icon(if (option == FrameFit.FIT) MemixIcons.Fit else MemixIcons.Fill, contentDescription = null, tint = tint) }
}

// Blur first, then the caption palette; the line under it explains when no clip lets the background show.
@Composable
private fun BackgroundRow(argb: Long?, backgroundShows: Boolean, onIntent: (VideoEditorIntent) -> Unit) {
    ChoiceRow(stringResource(UiRes.string.canvas_background)) {
        Swatch(
            null,
            stringResource(Res.string.canvas_background_blur),
            selected = argb == null,
            onClick = { onIntent(VideoEditorIntent.SetCanvasBackground(null)) },
            icon = MemixIcons.Blur,
        )
        CanvasColorSwatches(argb, onPick = { onIntent(VideoEditorIntent.SetCanvasBackground(it)) })
    }
    if (!backgroundShows) Text(stringResource(Res.string.canvas_background_hidden), MemixTheme.type.caption, color = MemixColors.textSecondary)
}
