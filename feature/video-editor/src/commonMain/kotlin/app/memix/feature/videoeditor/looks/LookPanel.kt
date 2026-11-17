package app.memix.feature.videoeditor.looks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.SegmentedTabs
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.domain.project.AdjustPart
import app.memix.core.domain.project.EditPhotoTab
import app.memix.core.ui.look.AdjustRow
import app.memix.core.ui.look.AdjustRows
import app.memix.core.ui.look.FilterThumbnails
import app.memix.core.ui.look.FiltersStrip
import app.memix.feature.videoeditor.LookUi
import app.memix.feature.videoeditor.VideoEditorIntent
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.edit_photo_reset
import memix.core.ui.generated.resources.edit_photo_tab_adjust
import memix.core.ui.generated.resources.edit_photo_tab_filters
import memix.core.ui.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.filters_mixed_hint
import memix.feature.video_editor.generated.resources.filters_title_all
import memix.feature.video_editor.generated.resources.tool_filters
import org.jetbrains.compose.resources.stringResource

/**
 * The Filters panel (spec docs/ux/specs/P4-07-video-filters-addendum.md → The panel): tabs Filters and Adjust under the title, the photo
 * editor's strip and sliders (`:core:ui`), Reset in the header. A ToolPanel over the timeline and tool bar, shown while [panel] isn't null;
 * the tabs stay pinned unless they would take more than 40% of the panel (the 360 x 640 phone), where they scroll with the content. The title
 * says the scope. A tile is one undo step; the sliders move the picture live and commit on release. [tiles] are made from [LookUi.frame].
 */
@Composable
internal fun LookPanel(panel: LookUi?, tiles: FilterThumbnails, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    // The last values stay on screen while the panel slides away.
    val held = remember { arrayOfNulls<LookUi>(1) }
    if (panel != null) held[0] = panel
    val shown = panel ?: held[0]
    val frame = panel?.frame
    LaunchedEffect(frame?.key) { if (frame != null) tiles.showPicture(frame.key, frame.picture, frame.geometry) }
    LaunchedEffect(panel == null) { if (panel == null) tiles.release() }
    val tab = shown?.tab ?: EditPhotoTab.FILTERS
    ToolPanel(
        visible = panel != null,
        title = stringResource(if (shown?.isAllClips == true) Res.string.filters_title_all else Res.string.tool_filters),
        doneLabel = stringResource(UiRes.string.panel_done),
        onDone = { onIntent(VideoEditorIntent.CloseLook) },
        modifier = modifier,
        headerActions = { Button(stringResource(UiRes.string.edit_photo_reset), { onIntent(VideoEditorIntent.LookReset) }, variant = ButtonVariant.Quiet) },
        pinned = { LookTabs(tab, onIntent) },
    ) {
        if (shown == null) return@ToolPanel
        if (shown.mixed) Text(stringResource(Res.string.filters_mixed_hint), MemixTheme.type.label, color = MemixColors.textSecondary)
        when (tab) {
            EditPhotoTab.FILTERS -> FiltersStrip(
                filter = shown.filter,
                mixed = shown.mixed,
                tiles = tiles,
                onPick = { onIntent(VideoEditorIntent.LookFilterPicked(it)) },
                onIntensityChange = { onIntent(VideoEditorIntent.LookIntensityChanged(it)) },
                onIntensityEnd = { onIntent(VideoEditorIntent.LookIntensityReleased) },
            )
            EditPhotoTab.ADJUST, EditPhotoTab.DEEP_FRY -> AdjustRows(
                adjust = shown.adjust,
                onChange = { row, value -> onIntent(VideoEditorIntent.LookAdjustChanged(row.toPart(), value)) },
                onEnd = { row -> onIntent(VideoEditorIntent.LookAdjustReleased(row.toPart())) },
            )
        }
    }
}

// Two tabs: Deep-fry is a photo tool and has none in video.
@Composable
private fun LookTabs(selected: EditPhotoTab, onIntent: (VideoEditorIntent) -> Unit) {
    val filters = stringResource(UiRes.string.edit_photo_tab_filters)
    val adjust = stringResource(UiRes.string.edit_photo_tab_adjust)
    val labels = remember(filters, adjust) { listOf(filters, adjust) }
    SegmentedTabs(labels, selected.ordinal, { onIntent(VideoEditorIntent.LookTabSelected(VIDEO_TABS[it])) }, Modifier.fillMaxWidth())
}

private val VIDEO_TABS = listOf(EditPhotoTab.FILTERS, EditPhotoTab.ADJUST)

// `:core:ui` can't see the domain's AdjustPart, so the six rows map to it here, once.
internal fun AdjustRow.toPart(): AdjustPart = when (this) {
    AdjustRow.BRIGHTNESS -> AdjustPart.BRIGHTNESS
    AdjustRow.CONTRAST -> AdjustPart.CONTRAST
    AdjustRow.SATURATION -> AdjustPart.SATURATION
    AdjustRow.WARMTH -> AdjustPart.WARMTH
    AdjustRow.SHARPEN -> AdjustPart.SHARPEN
    AdjustRow.VIGNETTE -> AdjustPart.VIGNETTE
}
