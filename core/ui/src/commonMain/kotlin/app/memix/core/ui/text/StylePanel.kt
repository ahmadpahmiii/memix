package app.memix.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.memix.core.designsystem.captionFontFamily
import app.memix.core.designsystem.component.FontTile
import app.memix.core.designsystem.component.Swatch
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.CaptionStyle
import app.memix.core.ui.panels.ChoiceRow
import app.memix.core.ui.panels.PaletteColorNames
import memix.core.ui.generated.resources.Res
import memix.core.ui.generated.resources.font_bubbly
import memix.core.ui.generated.resources.font_classic
import memix.core.ui.generated.resources.font_handwritten
import memix.core.ui.generated.resources.font_modern
import memix.core.ui.generated.resources.font_serif
import memix.core.ui.generated.resources.outline_none
import memix.core.ui.generated.resources.panel_done
import memix.core.ui.generated.resources.style_color
import memix.core.ui.generated.resources.style_font
import memix.core.ui.generated.resources.style_outline
import memix.core.ui.generated.resources.style_title
import org.jetbrains.compose.resources.stringResource

/** What the Style panel tells its editor: each tap applies at once and is one undo step. */
interface StyleActions {
    fun onFont(fontId: String)
    fun onColor(argb: Long)

    /** The outline color, or null for "No outline". */
    fun onOutline(argb: Long?)
    fun onClose()
}

/**
 * The selected caption's Style panel (spec P1-10 → Style panel, P2-03): Font (five tiles), Color and Outline (ten swatches
 * each, Outline also "No outline"). [style] is the caption's now; the panel shows while [visible] and [style] is known.
 * [contentMaxHeight] caps the panel where the editor wants it shorter (the photo editor's 40%); the rest scrolls. A speech bubble's
 * panel (P2-02) hides the Outline row ([showOutline]) and adds its own rows before and after ([rowsBefore], [rowsAfter]: Shape, Fill).
 */
@Composable
fun StylePanel(
    visible: Boolean,
    style: CaptionStyle?,
    actions: StyleActions,
    modifier: Modifier = Modifier,
    contentMaxHeight: Dp? = null,
    showOutline: Boolean = true,
    rowsBefore: @Composable () -> Unit = {},
    rowsAfter: @Composable () -> Unit = {},
) {
    ToolPanel(
        visible && style != null,
        stringResource(Res.string.style_title),
        stringResource(Res.string.panel_done),
        actions::onClose,
        modifier,
        contentMaxHeight = contentMaxHeight,
    ) {
        if (style != null) {
            rowsBefore()
            StyleRows(style, actions, showOutline)
            rowsAfter()
        }
    }
}

@Composable
private fun StyleRows(style: CaptionStyle, actions: StyleActions, showOutline: Boolean) {
    // An unknown font id (a newer build's draft) selects no tile until the user picks one (spec → States).
    ChoiceRow(stringResource(Res.string.style_font)) {
        CaptionCatalog.fonts.forEachIndexed { index, font ->
            FontTile(stringResource(FONT_NAMES[index]), captionFontFamily(font.id), selected = style.fontId == font.id, onClick = { actions.onFont(font.id) })
        }
    }
    ChoiceRow(stringResource(Res.string.style_color)) {
        CaptionCatalog.palette.forEachIndexed { index, color ->
            Swatch(Color(color.argb.toInt()), stringResource(PaletteColorNames[index]), selected = style.color == color, onClick = { actions.onColor(color.argb) })
        }
    }
    if (!showOutline) return
    ChoiceRow(stringResource(Res.string.style_outline)) {
        Swatch(null, stringResource(Res.string.outline_none), selected = style.outlineColor == null, onClick = { actions.onOutline(null) })
        CaptionCatalog.palette.forEachIndexed { index, color ->
            Swatch(Color(color.argb.toInt()), stringResource(PaletteColorNames[index]), selected = style.outlineColor == color, onClick = { actions.onOutline(color.argb) })
        }
    }
}

private val FONT_NAMES = listOf(Res.string.font_classic, Res.string.font_modern, Res.string.font_bubbly, Res.string.font_handwritten, Res.string.font_serif)
