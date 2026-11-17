package app.memix.feature.photoeditor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.SegmentedTabs
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.domain.project.AdjustPart
import app.memix.core.domain.project.EditPhotoTab
import app.memix.core.ui.look.AdjustRow
import app.memix.core.ui.look.AdjustRows
import app.memix.core.ui.look.FilterThumbnails
import app.memix.core.ui.look.FiltersStrip
import app.memix.core.ui.look.percentText
import app.memix.core.domain.project.valueOf
import app.memix.core.domain.project.with
import app.memix.core.model.project.FilterRef
import app.memix.core.model.project.ImageEdit
import memix.core.ui.generated.resources.edit_photo_reset
import memix.core.ui.generated.resources.edit_photo_tab_adjust
import memix.core.ui.generated.resources.edit_photo_tab_filters
import memix.core.ui.generated.resources.panel_done
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.deep_fry_amount
import memix.feature.photo_editor.generated.resources.deep_fry_hint
import memix.feature.photo_editor.generated.resources.edit_photo_hidden
import memix.feature.photo_editor.generated.resources.edit_photo_tab_deep_fry
import memix.feature.photo_editor.generated.resources.edit_photo_title
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
            val labels = listOf(UiRes.string.edit_photo_tab_filters, UiRes.string.edit_photo_tab_adjust, Res.string.edit_photo_tab_deep_fry).map { stringResource(it) }
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
    val filter = edit.filter
    FiltersStrip(
        filter = filter,
        mixed = false,
        tiles = thumbnails,
        onPick = { onIntent(PhotoEditorIntent.SetFilter(it)) },
        onIntensityChange = { if (filter != null) live.setEdit(layer.id, edit.copy(filter = FilterRef(filter.id, it))) },
        onIntensityEnd = { live.releaseEdit()?.filter?.let { onIntent(PhotoEditorIntent.SetFilterIntensity(it.intensity)) } },
    )
}

@Composable
private fun AdjustTab(layer: SceneLayerUi, edit: ImageEdit, live: PhotoLive, onIntent: (PhotoEditorIntent) -> Unit) {
    AdjustRows(
        adjust = edit.adjust,
        onChange = { row, value -> live.setEdit(layer.id, edit.copy(adjust = edit.adjust.with(row.toPart(), value))) },
        onEnd = { row -> live.releaseEdit()?.adjust?.valueOf(row.toPart())?.let { onIntent(PhotoEditorIntent.SetAdjust(row.toPart(), it)) } },
    )
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

private const val SLIDER_STEP = 0.1f

// `:core:ui` can't see the domain's AdjustPart, so the six rows map to it here, once.
private fun AdjustRow.toPart(): AdjustPart = when (this) {
    AdjustRow.BRIGHTNESS -> AdjustPart.BRIGHTNESS
    AdjustRow.CONTRAST -> AdjustPart.CONTRAST
    AdjustRow.SATURATION -> AdjustPart.SATURATION
    AdjustRow.WARMTH -> AdjustPart.WARMTH
    AdjustRow.SHARPEN -> AdjustPart.SHARPEN
    AdjustRow.VIGNETTE -> AdjustPart.VIGNETTE
}
