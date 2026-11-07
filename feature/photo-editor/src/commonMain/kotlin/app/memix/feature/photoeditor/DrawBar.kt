package app.memix.feature.photoeditor

import androidx.compose.foundation.Canvas
import memix.feature.photo_editor.generated.resources.draw_eraser_hint
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.MemixSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.OptionTileLayout
import app.memix.core.designsystem.component.Segment
import app.memix.core.designsystem.component.SegmentedChoice
import app.memix.core.designsystem.component.Swatch
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.DrawCatalog
import app.memix.core.ui.panels.PaletteColorNames
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.core.ui.generated.resources.position_size
import memix.core.ui.generated.resources.style_color
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.draw_brush
import memix.feature.photo_editor.generated.resources.draw_eraser
import memix.feature.photo_editor.generated.resources.draw_size_fine
import memix.feature.photo_editor.generated.resources.draw_size_heavy
import memix.feature.photo_editor.generated.resources.draw_size_medium
import memix.feature.photo_editor.generated.resources.draw_size_thick
import memix.feature.photo_editor.generated.resources.draw_size_thin
import memix.feature.photo_editor.generated.resources.tool_draw
import org.jetbrains.compose.resources.stringResource

/**
 * The Draw bar (spec docs/ux/specs/P2-05-draw-and-censor.md → The Draw bar): in the place of the tool bar while Draw mode is on.
 * Brush or Eraser and Done on top, the ten colors (Brush only), the five sizes. Everything applies at once; the colors and sizes
 * are the brush's for the rest of the visit. Scrolls inside [maxHeight] (the panels' 40% of the window) at large fonts.
 */
@Composable
internal fun DrawBar(brush: DrawBrushUi, maxHeight: Dp, onIntent: (PhotoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val hairline = MemixColors.hairline
    val hairlineWidth = MemixStroke.strokeHairline
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .background(MemixColors.surface)
            .drawBehind { drawLine(hairline, Offset.Zero, Offset(size.width, 0f), hairlineWidth.toPx()) }
            .verticalScroll(rememberScrollState())
            .padding(vertical = MemixSpacing.space2),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MemixSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentedChoice(
                listOf(Segment(stringResource(Res.string.draw_brush)), Segment(stringResource(Res.string.draw_eraser))),
                selectedIndex = if (brush.erasing) 1 else 0,
                onSelect = { onIntent(PhotoEditorIntent.SetEraser(it == 1)) },
                groupName = stringResource(Res.string.tool_draw),
                modifier = Modifier.weight(1f),
            )
            Button(stringResource(UiRes.string.panel_done), { onIntent(PhotoEditorIntent.DrawDone) }, variant = ButtonVariant.Ghost)
        }
        // The eraser keeps the bar's height: one hint line stands where the colors were.
        if (brush.erasing) EraserHint() else ColorRow(brush.colorIndex, onIntent)
        SizeRow(brush.sizeIndex, onIntent)
    }
}

@Composable
private fun EraserHint() {
    Box(Modifier.fillMaxWidth().heightIn(min = MemixSize.touchTarget).padding(horizontal = MemixSpacing.space4), contentAlignment = Alignment.CenterStart) {
        Text(stringResource(Res.string.draw_eraser_hint), MemixTheme.type.body, color = MemixColors.textSecondary)
    }
}

// A 48 dp swatch and `space-1` between: a 52 dp pitch, so at 360 dp the seventh swatch is partly in view and the row reads as scrolling.
// A color past the first six is scrolled into view when the bar opens.
@Composable
private fun ColorRow(selectedIndex: Int, onIntent: (PhotoEditorIntent) -> Unit) {
    val group = stringResource(UiRes.string.style_color)
    val scroll = rememberScrollState()
    val pitchPx = with(LocalDensity.current) { (MemixSize.touchTarget + MemixSpacing.space1).toPx() }
    LaunchedEffect(Unit) { scroll.scrollTo(((selectedIndex - 2) * pitchPx).toInt().coerceAtLeast(0)) }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = MemixSpacing.space4)
            .selectableGroup()
            .semantics { contentDescription = group },
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CaptionCatalog.palette.forEachIndexed { index, color ->
            Swatch(Color(color.argb.toInt()), stringResource(PaletteColorNames[index]), selected = index == selectedIndex, onClick = { onIntent(PhotoEditorIntent.SetBrushColor(index)) })
        }
    }
}

@Composable
private fun SizeRow(selectedIndex: Int, onIntent: (PhotoEditorIntent) -> Unit) {
    val group = stringResource(UiRes.string.position_size)
    val names = listOf(Res.string.draw_size_thin, Res.string.draw_size_fine, Res.string.draw_size_medium, Res.string.draw_size_thick, Res.string.draw_size_heavy)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = MemixSpacing.space4)
            .selectableGroup()
            .semantics { contentDescription = group },
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
    ) {
        DrawCatalog.BRUSH_SIZES.indices.forEach { index ->
            OptionTile(
                stringResource(names[index]),
                selected = index == selectedIndex,
                onClick = { onIntent(PhotoEditorIntent.SetBrushSize(index)) },
                layout = OptionTileLayout.STACKED,
            ) { tint -> SizeDot(index, tint) }
        }
    }
}

// A filled dot in the 24 dp picture box (drawn from data, not an icon file): the designer's diameters, 4 to 22 dp, so the five differ by a clear step.
@Composable
private fun SizeDot(index: Int, tint: Color) {
    Canvas(Modifier.fillMaxSize()) { drawCircle(tint, DOT_DIAMETERS_DP[index].dp.toPx() / 2f) }
}

private val DOT_DIAMETERS_DP = listOf(4, 7, 10, 15, 22)
