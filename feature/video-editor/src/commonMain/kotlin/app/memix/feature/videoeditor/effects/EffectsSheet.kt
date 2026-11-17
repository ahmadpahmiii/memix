package app.memix.feature.videoeditor.effects

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Sheet
import app.memix.core.designsystem.component.Tabs
import app.memix.core.model.project.EffectCatalog
import app.memix.core.model.project.EffectDef
import app.memix.feature.videoeditor.EffectsSheetUi
import app.memix.feature.videoeditor.VideoEditorIntent
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.a11y_effect_tile
import memix.feature.video_editor.generated.resources.a11y_effect_tile_selected
import memix.feature.video_editor.generated.resources.effects_change_title
import memix.feature.video_editor.generated.resources.tool_effects
import org.jetbrains.compose.resources.stringResource

/**
 * The Effects sheet (spec docs/ux/specs/P4-06-video-effects.md → The Effects sheet): the tabs that exist and, under the open one, a three-column
 * grid of the user's own frame with each effect at its peak ([tiles]). A tile tap lands the effect (or in replace mode swaps the item's) and closes
 * the sheet; the tile is the whole touch target. Put it at the root of the editor screen with the other sheets.
 */
@Composable
internal fun EffectsSheet(sheet: EffectsSheetUi?, tiles: EffectTiles, onIntent: (VideoEditorIntent) -> Unit) {
    // Kept for the sheet's slide-out, once the state is gone.
    val held = remember { arrayOfNulls<EffectsSheetUi>(1) }
    if (sheet != null) held[0] = sheet
    val shown = sheet ?: held[0]
    val frame = sheet?.frame
    val tab = sheet?.tab
    LaunchedEffect(frame?.key, tab) { if (frame != null && tab != null) tiles.show(frame.key, frame.picture, tab) }
    // Keyed on open/closed: the tiles go when the sheet closes.
    DisposableEffect(sheet != null) { onDispose { tiles.release() } }
    val title = stringResource(if (shown?.replaceItemId != null) Res.string.effects_change_title else Res.string.tool_effects)
    Sheet(visible = sheet != null, onDismiss = { onIntent(VideoEditorIntent.CloseEffectsSheet) }, title = title) {
        if (shown != null) EffectsContent(shown, tiles, onIntent)
    }
}

@Composable
private fun EffectsContent(sheet: EffectsSheetUi, tiles: EffectTiles, onIntent: (VideoEditorIntent) -> Unit) {
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val shownTabs = EffectCatalog.shownTabs
    val labels = shownTabs.map { stringResource(tabNameOf(it)) }
    Column(Modifier.fillMaxWidth().heightIn(max = windowHeight * MemixSize.sheetMaxHeightFraction), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
        Tabs(labels, shownTabs.indexOf(sheet.tab).coerceAtLeast(0), { onIntent(VideoEditorIntent.EffectsTabSelected(shownTabs[it])) })
        val effects = EffectCatalog.builtIn(sheet.tab)
        LazyVerticalGrid(
            GridCells.Adaptive(MemixSize.gridTileMin),
            // The sheet already pads its content by `space-4` on each side, which is what makes three tiles fit at 360 dp.
            contentPadding = PaddingValues(bottom = MemixSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        ) {
            items(effects, key = { it.id }, contentType = { TILE }) { def ->
                EffectTile(def, effects.indexOf(def) + 1, effects.size, tiles, ringed = def.id == sheet.currentEffectId) { onIntent(VideoEditorIntent.EffectPicked(def.id)) }
            }
        }
    }
}

// A square picture of the user's frame with the effect (`surface-raised` until it is made), the name under it. The whole tile is the target; the current
// effect of replace mode gets the white ring and says "selected" in its spoken name.
@Composable
private fun EffectTile(def: EffectDef, position: Int, count: Int, tiles: EffectTiles, ringed: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val name = effectNameOf(def.id)?.let { stringResource(it) }.orEmpty()
    val caption = MemixTheme.type.caption
    // Auto hyphens and heading line breaks keep a long Spanish or Portuguese name from splitting mid-word at 200% font.
    val nameStyle = remember(caption) { caption.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Heading) }
    val spoken = stringResource(if (ringed) Res.string.a11y_effect_tile_selected else Res.string.a11y_effect_tile, name, position, count)
    Column(
        Modifier
            .clip(MemixShapes.radiusMd)
            .background(if (pressed) MemixColors.hairline else MemixColors.surface)
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = spoken; role = Role.Button },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MemixShapes.radiusMd)
                .background(MemixColors.surfaceRaised)
                .then(if (ringed) Modifier.border(MemixStroke.strokeSelection, MemixColors.selection, MemixShapes.radiusMd) else Modifier),
        ) {
            val picture = tiles.tileOf(def.id)
            // The sun leak drifts along the top edge, so its tile keeps the top of the frame; the others are centered.
            if (picture != null) Image(picture, null, Modifier.fillMaxWidth().aspectRatio(1f), alignment = if (def.id == "sun_leak") Alignment.TopCenter else Alignment.Center, contentScale = ContentScale.Crop)
        }
        Text(
            name,
            nameStyle,
            Modifier.padding(top = MemixSpacing.space1).clearAndSetSemantics { },
            maxLines = MAX_NAME_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val TILE = "effect-tile"

// Two lines at 100% font; the grid scrolls and names take up to three at 200% (spec → The grid).
private const val MAX_NAME_LINES = 3
