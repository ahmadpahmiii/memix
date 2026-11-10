package app.memix.feature.videoeditor.captions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.CaptionBox
import app.memix.core.model.project.CaptionShadow
import app.memix.core.model.project.Transform
import app.memix.core.ui.panels.PositionRows
import app.memix.core.ui.text.StyleActions
import app.memix.core.ui.text.StylePanel
import app.memix.feature.videoeditor.TextPanel
import app.memix.feature.videoeditor.VideoEditorIntent
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.core.ui.generated.resources.position_title
import org.jetbrains.compose.resources.stringResource

/**
 * The selected caption's Style or Position panel ([panel]; spec P1-10 → Style panel, Position panel), over the
 * timeline and tool bar like every ToolPanel. [style] and [transform] are the caption's now; every tap applies at once.
 * The Style panel is the shared one (`:core:ui`, also the photo editor's).
 */
@Composable
internal fun TextPanels(panel: TextPanel?, style: CaptionStyle?, transform: Transform?, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val done = stringResource(UiRes.string.panel_done)
    val close = { onIntent(VideoEditorIntent.CloseTextPanel) }
    val styleActions = remember(onIntent) {
        object : StyleActions {
            override fun onFont(fontId: String) = onIntent(VideoEditorIntent.SetCaptionFont(fontId))
            override fun onColor(argb: Long) = onIntent(VideoEditorIntent.SetCaptionColor(argb))
            override fun onOutline(argb: Long?) = onIntent(VideoEditorIntent.SetCaptionOutline(argb))
            override fun onShadow(shadow: CaptionShadow?) = onIntent(VideoEditorIntent.SetCaptionShadow(shadow))
            override fun onBox(box: CaptionBox?) = onIntent(VideoEditorIntent.SetCaptionBox(box))
            override fun onClose() = onIntent(VideoEditorIntent.CloseTextPanel)
        }
    }
    StylePanel(panel == TextPanel.STYLE, style, styleActions, modifier)
    ToolPanel(panel == TextPanel.POSITION && transform != null, stringResource(UiRes.string.position_title), done, close, modifier) {
        if (transform != null) {
            PositionRows(
                transform,
                onStep = { step, repeats -> onIntent(VideoEditorIntent.PositionStepped(step, repeats)) },
                onHeld = { step, repeats -> onIntent(VideoEditorIntent.PositionStepHeld(step, repeats)) },
            )
        }
    }
}
