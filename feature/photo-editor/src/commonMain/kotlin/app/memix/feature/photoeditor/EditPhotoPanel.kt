package app.memix.feature.photoeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.SegmentedTabs
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.domain.project.AdjustPart
import app.memix.core.domain.project.EditPhotoTab
import app.memix.core.domain.project.valueOf
import app.memix.core.domain.project.with
import app.memix.core.model.project.FilterRef
import app.memix.core.model.project.ImageEdit
import app.memix.core.model.project.ImageToolCatalog
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.edit_photo_reset
import memix.core.ui.generated.resources.panel_done
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.adjust_brightness
import memix.feature.photo_editor.generated.resources.adjust_contrast
import memix.feature.photo_editor.generated.resources.adjust_saturation
import memix.feature.photo_editor.generated.resources.adjust_sharpen
import memix.feature.photo_editor.generated.resources.adjust_vignette
import memix.feature.photo_editor.generated.resources.adjust_warmth
import memix.feature.photo_editor.generated.resources.deep_fry_amount
import memix.feature.photo_editor.generated.resources.deep_fry_hint
import memix.feature.photo_editor.generated.resources.edit_photo_hidden
import memix.feature.photo_editor.generated.resources.edit_photo_tab_adjust
import memix.feature.photo_editor.generated.resources.edit_photo_tab_deep_fry
import memix.feature.photo_editor.generated.resources.edit_photo_tab_filters
import memix.feature.photo_editor.generated.resources.edit_photo_title
import memix.feature.photo_editor.generated.resources.filter_aqua
import memix.feature.photo_editor.generated.resources.filter_bleach
import memix.feature.photo_editor.generated.resources.filter_chrome
import memix.feature.photo_editor.generated.resources.filter_cinema
import memix.feature.photo_editor.generated.resources.filter_cool
import memix.feature.photo_editor.generated.resources.filter_drama
import memix.feature.photo_editor.generated.resources.filter_dusk
import memix.feature.photo_editor.generated.resources.filter_fade
import memix.feature.photo_editor.generated.resources.filter_film
import memix.feature.photo_editor.generated.resources.filter_matte
import memix.feature.photo_editor.generated.resources.filter_mono
import memix.feature.photo_editor.generated.resources.filter_neon
import memix.feature.photo_editor.generated.resources.filter_noir
import memix.feature.photo_editor.generated.resources.filter_pastel
import memix.feature.photo_editor.generated.resources.filter_punch
import memix.feature.photo_editor.generated.resources.filter_sepia
import memix.feature.photo_editor.generated.resources.filter_sunset
import memix.feature.photo_editor.generated.resources.filter_vintage
import memix.feature.photo_editor.generated.resources.filter_vivid
import memix.feature.photo_editor.generated.resources.filter_warm
import memix.feature.photo_editor.generated.resources.filters_intensity
import memix.feature.photo_editor.generated.resources.filters_none
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import memix.core.ui.generated.resources.Res as UiRes

/**
 * The Edit photo panel (spec docs/ux/specs/P2-04-image-tools.md → The Edit photo panel): Filters, Adjust and Deep-fry on the selected image
 * layer. Reset acts on the open tab. A slider moves the picture live through [live] (read in the draw phase) and commits one step on
 * release; a filter tile commits at once. The tab isn't kept: it opens on Filters.
 */
@Composable
internal fun EditPhotoPanel(state: PhotoEditorUiState, live: PhotoLive, thumbnails: FilterThumbnails, maxHeight: Dp, onIntent: (PhotoEditorIntent) -> Unit, modifier: Modifier = Modifier, initialTab: EditPhotoTab = EditPhotoTab.FILTERS) {
    val layer = state.selectedLayer?.takeIf { it.kind == LayerKind.IMAGE }
    val visible = state.panel == PhotoPanel.EDIT_PHOTO && layer != null
    var tab by remember { mutableStateOf(initialTab) }
    LaunchedEffect(visible) {
        if (!visible) {
            tab = initialTab
            thumbnails.release()
        }
    }
    ToolPanel(
        visible,
        stringResource(Res.string.edit_photo_title),
        stringResource(UiRes.string.panel_done),
        { onIntent(PhotoEditorIntent.ClosePanel) },
        modifier,
        contentMaxHeight = maxHeight,
        headerActions = { Button(stringResource(UiRes.string.edit_photo_reset), { onIntent(PhotoEditorIntent.ResetEditTab(tab)) }, variant = ButtonVariant.Quiet) },
    ) {
        if (layer != null) {
            val edit = live.editOverride()?.takeIf { it.first == layer.id }?.second ?: layer.edit ?: ImageEdit.NONE
            if (layer.hidden) Text(stringResource(Res.string.edit_photo_hidden), MemixTheme.type.label, color = MemixColors.textSecondary)
            val labels = listOf(Res.string.edit_photo_tab_filters, Res.string.edit_photo_tab_adjust, Res.string.edit_photo_tab_deep_fry).map { stringResource(it) }
            SegmentedTabs(labels, tab.ordinal, { tab = EditPhotoTab.entries[it] }, Modifier.fillMaxWidth())
            when (tab) {
                EditPhotoTab.FILTERS -> FiltersTab(layer, edit, thumbnails, live, onIntent)
                EditPhotoTab.ADJUST -> AdjustTab(layer, edit, live, onIntent)
                EditPhotoTab.DEEP_FRY -> DeepFryTab(layer, edit, live, onIntent)
            }
        }
    }
}

@Composable
private fun FiltersTab(layer: SceneLayerUi, edit: ImageEdit, thumbnails: FilterThumbnails, live: PhotoLive, onIntent: (PhotoEditorIntent) -> Unit) {
    // The tiles are made from the picture with its geometry; a crop or turn made it different, so they start again.
    val geometry = edit.copy(adjust = ImageEdit.NONE.adjust, filter = null, deepFry = 0f)
    LaunchedEffect(layer.path, geometry) {
        val path = layer.path
        val size = layer.pictureSize
        if (path != null && size != null) thumbnails.show(path, size, geometry)
    }
    val ids = remember { listOf<String?>(null) + ImageToolCatalog.filters.map { it.id } }
    val names = filterNames()
    val none = stringResource(Res.string.filters_none)
    val listState = rememberLazyListState()
    val selectedIndex = ids.indexOf(edit.filter?.id).coerceAtLeast(0)
    // The selected tile scrolls into view when the panel opens (and after an undo brings another one back).
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex) }
    LazyRow(Modifier.fillMaxWidth().selectableGroup(), state = listState, horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
        items(ids, key = { it ?: "none" }) { id ->
            val name = if (id == null) none else stringResource(names.getValue(id))
            val index = ids.indexOf(id) + 1
            FilterTile(name, thumbnails.tileOf(id), selected = edit.filter?.id == id, position = "$index / ${ids.size}") { onIntent(PhotoEditorIntent.SetFilter(id)) }
        }
    }
    val filter = edit.filter
    if (filter != null) {
        Slider(
            label = stringResource(Res.string.filters_intensity),
            value = filter.intensity,
            onValueChange = { live.setEdit(layer.id, edit.copy(filter = FilterRef(filter.id, it))) },
            valueText = percentText(filter.intensity),
            defaultValue = 1f,
            step = SLIDER_STEP,
            onValueChangeFinished = { live.releaseEdit()?.filter?.let { onIntent(PhotoEditorIntent.SetFilterIntensity(it.intensity)) } },
            snapValue = 1f,
            snapDistance = SNAP_DISTANCE,
        )
    }
}

@Composable
private fun AdjustTab(layer: SceneLayerUi, edit: ImageEdit, live: PhotoLive, onIntent: (PhotoEditorIntent) -> Unit) {
    val labels = mapOf(
        AdjustPart.BRIGHTNESS to Res.string.adjust_brightness, AdjustPart.CONTRAST to Res.string.adjust_contrast,
        AdjustPart.SATURATION to Res.string.adjust_saturation, AdjustPart.WARMTH to Res.string.adjust_warmth,
        AdjustPart.SHARPEN to Res.string.adjust_sharpen, AdjustPart.VIGNETTE to Res.string.adjust_vignette,
    )
    for (part in AdjustPart.entries) {
        val value = edit.adjust.valueOf(part)
        Slider(
            label = stringResource(labels.getValue(part)),
            value = value,
            onValueChange = { live.setEdit(layer.id, edit.copy(adjust = edit.adjust.with(part, it))) },
            valueText = signedText(value, bipolar = part.range.start < 0f),
            valueRange = part.range,
            defaultValue = 0f,
            step = SLIDER_STEP,
            onValueChangeFinished = { live.releaseEdit()?.adjust?.valueOf(part)?.let { onIntent(PhotoEditorIntent.SetAdjust(part, it)) } },
            snapValue = 0f,
            snapDistance = SNAP_DISTANCE,
            origin = if (part.range.start < 0f) 0f else null,
        )
    }
}

@Composable
private fun DeepFryTab(layer: SceneLayerUi, edit: ImageEdit, live: PhotoLive, onIntent: (PhotoEditorIntent) -> Unit) {
    Slider(
        label = stringResource(Res.string.deep_fry_amount),
        value = edit.deepFry,
        onValueChange = { live.setEdit(layer.id, edit.copy(deepFry = it)) },
        valueText = percentText(edit.deepFry),
        defaultValue = 0f,
        step = SLIDER_STEP,
        onValueChangeFinished = { live.releaseEdit()?.let { onIntent(PhotoEditorIntent.SetDeepFry(it.deepFry)) } },
    )
    Text(stringResource(Res.string.deep_fry_hint), MemixTheme.type.label, color = MemixColors.textSecondary)
}

// 72 dp square with the user's photo and the filter on, the name under it; a white ring when selected (spec → Filters tab).
@Composable
private fun FilterTile(name: String, tile: androidx.compose.ui.graphics.ImageBitmap?, selected: Boolean, position: String, onClick: () -> Unit) {
    Column(
        Modifier
            .width(MemixSize.filterTileSize)
            .selectable(selected, role = Role.RadioButton, onClick = { if (!selected) onClick() })
            .semantics { contentDescription = "$name, $position" },
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(MemixSize.filterTileSize)
                .clip(MemixShapes.radiusMd)
                .background(MemixColors.surfaceRaised)
                .then(if (selected) Modifier.border(MemixStroke.strokeSelection, MemixColors.selection, MemixShapes.radiusMd) else Modifier),
        ) {
            if (tile != null) Image(BitmapPainter(tile), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(name, MemixTheme.type.label, color = MemixColors.text)
    }
}

private fun percentText(value: Float) = "${(value * PERCENT).roundToInt()}%"

// "+30", "-20" and "0": a bipolar slider's value with its sign; a one-sided one without.
private fun signedText(value: Float, bipolar: Boolean): String {
    val percent = (value * PERCENT).roundToInt()
    return when {
        percent > 0 && bipolar -> "+$percent"
        percent < 0 -> "−${-percent}"
        else -> percent.toString()
    }
}

@Composable
private fun filterNames(): Map<String, StringResource> = remember {
    mapOf(
        "vivid" to Res.string.filter_vivid, "warm" to Res.string.filter_warm, "cool" to Res.string.filter_cool, "mono" to Res.string.filter_mono,
        "noir" to Res.string.filter_noir, "fade" to Res.string.filter_fade, "vintage" to Res.string.filter_vintage, "film" to Res.string.filter_film,
        "sepia" to Res.string.filter_sepia, "cinema" to Res.string.filter_cinema, "pastel" to Res.string.filter_pastel, "punch" to Res.string.filter_punch,
        "drama" to Res.string.filter_drama, "sunset" to Res.string.filter_sunset, "neon" to Res.string.filter_neon, "aqua" to Res.string.filter_aqua,
        "matte" to Res.string.filter_matte, "chrome" to Res.string.filter_chrome, "dusk" to Res.string.filter_dusk, "bleach" to Res.string.filter_bleach,
    )
}

private const val SLIDER_STEP = 0.1f
private const val SNAP_DISTANCE = 0.03f
private const val PERCENT = 100f
