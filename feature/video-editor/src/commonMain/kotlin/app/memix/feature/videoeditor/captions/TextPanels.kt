package app.memix.feature.videoeditor.captions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.memix.core.designsystem.captionFontFamily
import app.memix.core.designsystem.component.FontTile
import app.memix.core.designsystem.component.Swatch
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Transform
import app.memix.core.ui.panels.ChoiceRow
import app.memix.core.ui.panels.PaletteColorNames
import app.memix.core.ui.panels.PositionRows
import app.memix.feature.videoeditor.TextPanel
import app.memix.feature.videoeditor.VideoEditorIntent
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.core.ui.generated.resources.position_title
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.font_bubbly
import memix.feature.video_editor.generated.resources.font_classic
import memix.feature.video_editor.generated.resources.font_handwritten
import memix.feature.video_editor.generated.resources.font_modern
import memix.feature.video_editor.generated.resources.font_serif
import memix.feature.video_editor.generated.resources.outline_none
import memix.feature.video_editor.generated.resources.style_color
import memix.feature.video_editor.generated.resources.style_font
import memix.feature.video_editor.generated.resources.style_outline
import memix.feature.video_editor.generated.resources.style_title
import org.jetbrains.compose.resources.stringResource

/**
 * The selected caption's Style or Position panel ([panel]; spec P1-10 → Style panel, Position panel), over the
 * timeline and tool bar like every ToolPanel. [style] and [transform] are the caption's now; every tap applies at once.
 */
@Composable
internal fun TextPanels(panel: TextPanel?, style: CaptionStyle?, transform: Transform?, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val done = stringResource(UiRes.string.panel_done)
    val close = { onIntent(VideoEditorIntent.CloseTextPanel) }
    ToolPanel(panel == TextPanel.STYLE && style != null, stringResource(Res.string.style_title), done, close, modifier) {
        if (style != null) StyleRows(style, onIntent)
    }
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
            Swatch(Color(color.argb.toInt()), stringResource(PaletteColorNames[index]), selected = style.color == color, onClick = {
                onIntent(VideoEditorIntent.SetCaptionColor(color.argb))
            })
        }
    }
    ChoiceRow(stringResource(Res.string.style_outline)) {
        Swatch(null, stringResource(Res.string.outline_none), selected = style.outlineColor == null, onClick = {
            onIntent(VideoEditorIntent.SetCaptionOutline(null))
        })
        CaptionCatalog.palette.forEachIndexed { index, color ->
            Swatch(Color(color.argb.toInt()), stringResource(PaletteColorNames[index]), selected = style.outlineColor == color, onClick = {
                onIntent(VideoEditorIntent.SetCaptionOutline(color.argb))
            })
        }
    }
}

private val FONT_NAMES = listOf(Res.string.font_classic, Res.string.font_modern, Res.string.font_bubbly, Res.string.font_handwritten, Res.string.font_serif)
