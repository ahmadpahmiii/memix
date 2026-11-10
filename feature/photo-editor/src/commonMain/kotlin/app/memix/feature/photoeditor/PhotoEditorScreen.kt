package app.memix.feature.photoeditor

import androidx.compose.animation.Crossfade
import app.memix.core.ui.crop.CropScreen
import app.memix.core.domain.project.EditPhotoTab
import app.memix.core.model.project.ImageEdit
import app.memix.core.designsystem.MemixTheme
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.traversalIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.component.FadingToast
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.SettingPill
import app.memix.core.designsystem.component.Toast
import app.memix.core.designsystem.component.ToolBar
import app.memix.core.designsystem.component.ToolBarClose
import app.memix.core.designsystem.component.ToolBarItem
import app.memix.core.designsystem.component.ToolTone
import app.memix.core.designsystem.component.coveredByModal
import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.ui.editor.EXPLANATION_TOAST_MILLIS
import app.memix.core.ui.editor.SaveBanner
import app.memix.core.ui.editor.SavedAgainAnnouncement
import app.memix.core.ui.editor.SpokenOnly
import app.memix.core.ui.editor.UnsavedChangesSheet
import app.memix.core.ui.editor.WHAT_HAPPENED_TOAST_MILLIS
import app.memix.core.ui.editor.recommendedTimeoutMillis
import app.memix.core.ui.layers.LayerStageState
import app.memix.core.ui.scene.ImageToolRenderer
import app.memix.core.ui.scene.SceneImageSource
import app.memix.core.ui.text.TextEntryBar
import app.memix.core.ui.scene.SceneCaptions
import app.memix.core.ui.scene.SceneThumbnails
import app.memix.feature.mediaimport.ImportWording
import app.memix.feature.mediaimport.ProjectImportSheet
import app.memix.feature.mediaimport.rememberImportSheetShown
import kotlin.math.max
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import memix.core.ui.generated.resources.Res as UiRes
import memix.feature.photo_editor.generated.resources.a11y_layer_text_added
import memix.core.ui.generated.resources.edit_add_text
import memix.core.ui.generated.resources.edit_edit_text
import memix.core.ui.generated.resources.edit_text_color
import memix.core.ui.generated.resources.edit_text_font
import memix.core.ui.generated.resources.edit_text_outline
import memix.core.ui.generated.resources.edit_text_shadow
import memix.core.ui.generated.resources.edit_text_box
import memix.core.ui.generated.resources.edit_adjust
import memix.core.ui.generated.resources.edit_crop
import memix.feature.photo_editor.generated.resources.edit_remove_bg
import memix.feature.photo_editor.generated.resources.explain_photo_export_busy
import memix.feature.photo_editor.generated.resources.explain_photo_export_empty
import memix.feature.photo_editor.generated.resources.explain_photo_export_no_space
import memix.feature.photo_editor.generated.resources.make_video_fail
import memix.feature.photo_editor.generated.resources.make_video_fail_space
import memix.feature.photo_editor.generated.resources.photo_export_copied
import memix.core.ui.generated.resources.editor_export
import memix.core.ui.generated.resources.export_stopped_toast
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import memix.feature.photo_editor.generated.resources.edit_remove_cutout
import memix.feature.photo_editor.generated.resources.tool_remove_bg
import memix.feature.photo_editor.generated.resources.tool_stickers
import memix.feature.photo_editor.generated.resources.edit_add_sticker
import memix.feature.photo_editor.generated.resources.a11y_sticker_added
import memix.feature.photo_editor.generated.resources.layer_meta_cutout
import memix.core.ui.generated.resources.edit_deep_fry
import memix.core.ui.generated.resources.edit_filter
import memix.core.ui.generated.resources.edit_photo_reset
import memix.feature.photo_editor.generated.resources.tool_edit_photo
import memix.core.ui.generated.resources.tool_crop
import memix.core.ui.generated.resources.tool_edit_text
import memix.core.ui.generated.resources.tool_style
import memix.core.ui.generated.resources.tool_text
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.input.TextFieldState
import memix.core.ui.generated.resources.a11y_canvas_image_removed
import memix.core.ui.generated.resources.a11y_canvas_image_set
import memix.core.ui.generated.resources.canvas_image_missing_toast
import memix.core.ui.generated.resources.a11y_degrees
import memix.core.ui.generated.resources.a11y_edit_on_item
import memix.core.ui.generated.resources.a11y_text_position
import memix.core.ui.generated.resources.a11y_text_transformed
import memix.core.ui.generated.resources.canvas_pill_a11y
import memix.core.ui.generated.resources.edit_canvas_background
import memix.core.ui.generated.resources.edit_canvas_ratio
import memix.core.ui.generated.resources.edit_delete
import memix.core.ui.generated.resources.edit_duplicate
import memix.core.ui.generated.resources.edit_reorder
import memix.core.ui.generated.resources.editor_close
import memix.core.ui.generated.resources.editor_redo
import memix.core.ui.generated.resources.editor_redo_toast
import memix.core.ui.generated.resources.editor_undo
import memix.core.ui.generated.resources.editor_undo_toast
import memix.core.ui.generated.resources.explain_canvas_max
import memix.core.ui.generated.resources.explain_canvas_min
import memix.core.ui.generated.resources.explain_canvas_tallest
import memix.core.ui.generated.resources.explain_canvas_widest
import memix.core.ui.generated.resources.explain_text_biggest
import memix.core.ui.generated.resources.explain_text_smallest
import memix.core.ui.generated.resources.position_size_value
import memix.core.ui.generated.resources.tool_delete
import memix.core.ui.generated.resources.tool_duplicate
import memix.core.ui.generated.resources.tool_position
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.a11y_layer_added
import memix.feature.photo_editor.generated.resources.a11y_layer_order
import memix.feature.photo_editor.generated.resources.a11y_layer_opacity
import memix.feature.photo_editor.generated.resources.a11y_layer_row
import memix.feature.photo_editor.generated.resources.a11y_draw_done
import memix.feature.photo_editor.generated.resources.a11y_draw_mode
import memix.feature.photo_editor.generated.resources.edit_add_photo
import memix.feature.photo_editor.generated.resources.edit_draw
import memix.feature.photo_editor.generated.resources.edit_erase
import memix.feature.photo_editor.generated.resources.explain_draw_full
import memix.feature.photo_editor.generated.resources.explain_draw_nothing
import memix.feature.photo_editor.generated.resources.a11y_layer_censor_added
import memix.feature.photo_editor.generated.resources.edit_censor_add
import memix.feature.photo_editor.generated.resources.edit_censor_mode
import memix.feature.photo_editor.generated.resources.edit_censor_shape
import memix.feature.photo_editor.generated.resources.edit_censor_strength
import memix.feature.photo_editor.generated.resources.explain_censor_max
import memix.feature.photo_editor.generated.resources.explain_censor_min
import memix.feature.photo_editor.generated.resources.tool_censor
import memix.feature.photo_editor.generated.resources.tool_draw
import memix.feature.photo_editor.generated.resources.edit_layer_hide
import memix.feature.photo_editor.generated.resources.edit_layer_opacity
import memix.feature.photo_editor.generated.resources.edit_layer_show
import memix.feature.photo_editor.generated.resources.edit_lock
import memix.feature.photo_editor.generated.resources.edit_transform_layer
import memix.feature.photo_editor.generated.resources.edit_unlock
import memix.feature.photo_editor.generated.resources.explain_layer_back
import memix.feature.photo_editor.generated.resources.explain_layer_edge
import memix.feature.photo_editor.generated.resources.explain_layer_front
import memix.feature.photo_editor.generated.resources.explain_layer_locked
import memix.feature.photo_editor.generated.resources.explain_layer_missing
import memix.feature.photo_editor.generated.resources.explain_layer_one
import memix.feature.photo_editor.generated.resources.explain_layers_empty
import memix.feature.photo_editor.generated.resources.layer_name_photo
import memix.feature.photo_editor.generated.resources.layer_tools_close
import memix.feature.photo_editor.generated.resources.photo_layers_moved
import memix.feature.photo_editor.generated.resources.photo_missing_media
import memix.feature.photo_editor.generated.resources.tool_add_photo
import memix.feature.photo_editor.generated.resources.tool_formats
import memix.feature.photo_editor.generated.resources.tool_hide_layer
import memix.feature.photo_editor.generated.resources.a11y_format_set
import memix.feature.photo_editor.generated.resources.a11y_layer_bubble_added
import memix.feature.photo_editor.generated.resources.edit_add_bubble
import memix.feature.photo_editor.generated.resources.edit_bubble_fill
import memix.feature.photo_editor.generated.resources.edit_bubble_shape
import memix.feature.photo_editor.generated.resources.edit_bubble_tail
import memix.feature.photo_editor.generated.resources.tool_add_bubble
import memix.feature.photo_editor.generated.resources.panels_close_cell
import memix.feature.photo_editor.generated.resources.tool_panel_remove
import memix.feature.photo_editor.generated.resources.tool_panels_layout
import memix.core.ui.generated.resources.tool_replace
import memix.feature.photo_editor.generated.resources.edit_panel_photo
import memix.feature.photo_editor.generated.resources.edit_panels_divider
import memix.feature.photo_editor.generated.resources.edit_panels_layout
import memix.feature.photo_editor.generated.resources.edit_format
import memix.feature.photo_editor.generated.resources.panels_photo_dropped
import memix.feature.photo_editor.generated.resources.tool_layer_opacity
import memix.feature.photo_editor.generated.resources.tool_layers
import memix.feature.photo_editor.generated.resources.tool_lock
import memix.feature.photo_editor.generated.resources.tool_show_layer
import memix.feature.photo_editor.generated.resources.tool_unlock
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The photo editor frame (spec docs/ux/specs/P2-01-photo-editor-shell.md → Layout): the top bar, the stage with the canvas
 * and its layers, and the tool bar, which a tool panel replaces and the entry bar replaces while a text is typed.
 * [imageSource] decodes the pictures and [captions] draws the text layers: the composition root passes the engines', which a
 * feature can't depend on. [onFreeUpSpace] opens the phone's storage manager asking for the
 * given number of bytes.
 */
@Composable
fun PhotoEditorScreen(
    state: PhotoEditorUiState,
    onIntent: (PhotoEditorIntent) -> Unit,
    imageSource: SceneImageSource,
    imageTools: ImageToolRenderer,
    captions: SceneCaptions,
    onFreeUpSpace: (requestedBytes: Long) -> Unit,
    modifier: Modifier = Modifier,
    /** The Edit photo panel's tab when it opens: Filters, always, outside the render harness (spec P2-04: the open tab isn't kept). */
    editPhotoTab: EditPhotoTab = EditPhotoTab.FILTERS,
) {
    // System back closes the editor through the app's own navigation, which saves on the way out. The editor takes back
    // itself while something inside it comes first (a panel, the selection) or while saving fails, to ask first.
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !state.unsavedSheetOpen && (state.makingVideo || state.cropLayerId != null || state.cutOut != null || state.draw != null || state.textEntry != null || state.panel != null || state.selectedLayerId != null || state.selectedCell != null || state.selectedDivider != null || state.isSaveFailing),
        onBackCompleted = { onIntent(PhotoEditorIntent.Back) },
    )
    LifecycleEventEffect(Lifecycle.Event.ON_START) { onIntent(PhotoEditorIntent.AppStarted) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onIntent(PhotoEditorIntent.AppStopped) }
    val freeUpSpaceForSave = remember(onFreeUpSpace) { { onFreeUpSpace(ImportMediaUseCase.HEADROOM_BYTES) } }

    val importSheetShown = rememberImportSheetShown(state.photoImport)
    // The typed words live here, so a keystroke redraws only the scene's text, never the screen (spec P2-03 → Performance).
    val entry = state.textEntry
    val entryField = remember(entry?.serial) { TextFieldState(entry?.initialText.orEmpty(), TextRange(entry?.initialText.orEmpty().length)) }
    val scope = rememberCoroutineScope()
    val bitmaps = remember(imageSource, imageTools) { SceneBitmaps(imageSource, imageTools, scope) }
    DisposableEffect(bitmaps) { onDispose { bitmaps.release() } }
    val thumbnails = remember(imageSource) { SceneThumbnails(imageSource, scope) }
    val filterThumbnails = remember(imageSource, imageTools) { FilterThumbnails(imageSource, imageTools, scope) }
    DisposableEffect(filterThumbnails) { onDispose { filterThumbnails.release() } }
    DisposableEffect(thumbnails) { onDispose { thumbnails.release() } }
    val stage = remember { LayerStageState() }
    val held = rememberUpdatedState(state.held)
    val heldTail = rememberUpdatedState(state.heldTail)
    val liveFormat = rememberUpdatedState(state.format)
    val heldFormat = rememberUpdatedState(state.heldFormat)
    val live = remember(stage) { PhotoLive(stage, held, heldTail, liveFormat, heldFormat) }
    // The stage lets go of a layer it held at its released place once the project has it.
    LaunchedEffect(state.gestureEndCount) { live.release() }

    var panelHeightPx by remember { mutableIntStateOf(0) }
    var toolRegionPx by remember { mutableIntStateOf(0) }
    val panelOpen = state.panel != null
    val coverPx = if (panelOpen) max(0, panelHeightPx - toolRegionPx) else 0

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MemixColors.canvas)
            // Escape does what system back does (spec → Interactions); an open sheet handles its own.
            .onPreviewKeyEvent { event ->
                val goesBack = event.key == Key.Escape && !state.unsavedSheetOpen
                // The Crop screen is a mode: Escape discards it (spec P2-04 → Interactions).
                if (goesBack && event.type == KeyEventType.KeyUp) onIntent(if (state.cropLayerId != null) PhotoEditorIntent.CropClosed else if (state.cutOut != null) PhotoEditorIntent.CutOutClosed else PhotoEditorIntent.Back)
                goesBack
            },
    ) {
        val panelMaxHeight = maxHeight * PANEL_MAX_SHARE_OF_WINDOW
        Column(
            Modifier
                .fillMaxSize()
                .coveredByModal(state.unsavedSheetOpen || importSheetShown || state.exportSheet != null)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            TopBar(state, onIntent)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PhotoStage(state, state.scene, bitmaps, captions, stage, live, coverPx, entryField, onIntent, Modifier.fillMaxSize())
                StageMessages(state, onIntent, freeUpSpaceForSave, Modifier.align(Alignment.TopCenter).semantics { traversalIndex = -1f })
            }
            MakeVideoRow(state, onIntent)
            Box(Modifier.coveredByModal(panelOpen).onSizeChanged { toolRegionPx = it.height }) {
                ToolBarRegion(state, entryField, panelMaxHeight, onIntent)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { if (it.height > 0) panelHeightPx = it.height }) {
            PhotoPanels(state, live, thumbnails, filterThumbnails, panelMaxHeight, onIntent, editPhotoTab = editPhotoTab)
            LayersPanel(state, live, thumbnails, bitmaps, panelMaxHeight, onIntent)
        }
        state.photoImport?.let { importUi ->
            ProjectImportSheet(
                importUi,
                ImportWording.PHOTO,
                onIntent = { onIntent(PhotoEditorIntent.Import(it)) },
                onPickAgain = { onIntent(PhotoEditorIntent.PickAgain) },
                onFreeUpSpace = onFreeUpSpace,
            )
        }
        CropOverlay(state, bitmaps, onIntent)
        CutOutOverlay(state, bitmaps, onIntent)
        StickersSheet(state, bitmaps, onIntent)
        PhotoExportSheet(
            state.exportSheet,
            onIntent = { onIntent(PhotoEditorIntent.Export(it)) },
            onFreeUpSpace = onFreeUpSpace.takeIf { state.canFreeUpSpace },
        )
        UnsavedChangesSheet(
            state.unsavedSheetOpen,
            onStay = { onIntent(PhotoEditorIntent.StayInEditor) },
            onLeave = { onIntent(PhotoEditorIntent.LeaveAnyway) },
            onFreeUpSpace = freeUpSpaceForSave.takeIf { state.canFreeUpSpace },
        )
        if (state.makingVideo) MakeVideoInputBlock()
    }
}

/**
 * Close at the start and Export at the end of the first row (spec P2-08 → Export button). The ratio pill, Undo and Redo sit between
 * them when they fit, with the pill centered on the screen but clamped left of Undo; when they don't (200% font, long labels) all
 * three move together to a second 48 dp row under the bar, the pill at the start and Undo and Redo at the end, so the bar never
 * reshuffles piece by piece. A custom [Layout], not overlapping aligns: Close and Export are measured first and never shrink.
 */
@Composable
private fun TopBar(state: PhotoEditorUiState, onIntent: (PhotoEditorIntent) -> Unit) {
    val gap = MemixSpacing.space2
    val secondRowHeight = MemixSize.touchTarget
    val scene = state.scene
    val typing = state.textEntry != null
    Layout(
        content = {
            IconButton(MemixIcons.Close, stringResource(UiRes.string.editor_close), { onIntent(PhotoEditorIntent.Close) })
            Row(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
                // Undo, Redo and the pill are inert while typing: the typed words aren't in the project yet (spec P2-03 → Typing).
                IconButton(MemixIcons.Undo, stringResource(UiRes.string.editor_undo), { onIntent(PhotoEditorIntent.Undo) }, enabled = state.canUndo && !typing)
                IconButton(MemixIcons.Redo, stringResource(UiRes.string.editor_redo), { onIntent(PhotoEditorIntent.Redo) }, enabled = state.canRedo && !typing)
            }
            Button(stringResource(UiRes.string.editor_export), { onIntent(PhotoEditorIntent.ExportTool) }, variant = ButtonVariant.Primary)
            if (scene != null) {
                val label = state.canvasPanel?.shownTerms?.lowest()?.toString() ?: scene.ratioLabel
                SettingPill(
                    label,
                    stringResource(UiRes.string.canvas_pill_a11y, label),
                    expanded = state.panel == PhotoPanel.CANVAS,
                    onClick = { onIntent(PhotoEditorIntent.RatioPill) },
                    enabled = !typing && state.draw == null,
                )
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = MemixSpacing.space2, vertical = MemixSpacing.space1),
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val close = measurables[0].measure(loose)
        val endGroup = measurables[1].measure(loose)
        val export = measurables[2].measure(loose)
        val pill = measurables.getOrNull(3)?.measure(loose)
        val width = constraints.maxWidth
        val firstRowHeight = maxOf(close.height, export.height)
        // The room between Close and Export, with `space-2` to each neighbour, against what the middle three need.
        val between = width - close.width - export.width - gapPx * 2
        val needed = endGroup.width + if (pill != null) pill.width + gapPx else 0
        val secondRow = needed > between
        val secondRowPx = secondRowHeight.roundToPx()
        val secondHeight = if (secondRow) maxOf(secondRowPx, endGroup.height, pill?.height ?: 0) else 0
        layout(width, firstRowHeight + secondHeight) {
            close.place(0, (firstRowHeight - close.height) / 2)
            export.place(width - export.width, (firstRowHeight - export.height) / 2)
            if (secondRow) {
                pill?.place(0, firstRowHeight + (secondHeight - pill.height) / 2)
                endGroup.place(width - endGroup.width, firstRowHeight + (secondHeight - endGroup.height) / 2)
            } else {
                val endGroupX = width - export.width - gapPx - endGroup.width
                endGroup.place(endGroupX, (firstRowHeight - endGroup.height) / 2)
                if (pill != null) {
                    val centered = (width - pill.width) / 2
                    val rightmost = endGroupX - gapPx - pill.width
                    val leftmost = close.width + gapPx
                    pill.place(centered.coerceAtMost(rightmost).coerceAtLeast(leftmost), (firstRowHeight - pill.height) / 2)
                }
            }
        }
    }
}

/** The main bar with nothing selected, the selected-layer bar with a layer (spec → Tool bars); they crossfade in 120 ms. */
@Composable
private fun ToolBarRegion(state: PhotoEditorUiState, entryField: TextFieldState, drawBarMaxHeight: Dp, onIntent: (PhotoEditorIntent) -> Unit) {
    val entry = state.textEntry
    if (state.draw != null) {
        // The Draw bar takes the place of the tool bar for the whole of Draw mode (spec P2-05 → Entry and exit).
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).background(MemixColors.surface)) {
            DrawBar(state.brush, drawBarMaxHeight, onIntent)
        }
        return
    }
    if (entry != null) {
        // The entry bar rides the keyboard in place of the tool bar (spec P2-03 → Typing).
        Box(Modifier.fillMaxWidth().background(MemixColors.surface).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
            TextEntryBar(entry, entryField, onChanged = { onIntent(PhotoEditorIntent.TextEntryChanged(it)) }, onDone = { onIntent(PhotoEditorIntent.TextEntryDone) })
        }
        return
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(MemixColors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        // The bar that fades out keeps the layer it was showing, after the selection is gone.
        val lastLayer = remember { arrayOfNulls<SceneLayerUi>(1) }
        state.selectedLayer?.let { lastLayer[0] = it }
        Crossfade(state.selectedLayerId != null || state.selectedCell != null, animationSpec = tween(MemixMotion.durationPress), label = "tool bar") { hasSelection ->
            val layer = if (hasSelection) state.selectedLayer ?: lastLayer[0] else null
            if (state.selectedCell != null) {
                // The selected cell's bar: Replace · Position · Remove (spec P2-02 → Selected-cell bar).
                ToolBar(
                    cellTools(onIntent),
                    close = ToolBarClose(stringResource(Res.string.panels_close_cell)) { onIntent(PhotoEditorIntent.ClearSelection) },
                )
            } else if (layer == null) {
                // The format's own tool sits right after Formats (spec P2-02 → Main bar).
                val formatTool = formatSpecificTool(state.format, onIntent)
                ToolBar(persistentListOf(*listOfNotNull(formatsTool(onIntent), formatTool, textTool(onIntent), stickersTool(onIntent), addPhotoTool(onIntent), drawTool(onIntent), editPhotoTool(onIntent, state.layers.any { it.kind == LayerKind.IMAGE }), cropTool(onIntent, state.layers.any { it.kind == LayerKind.IMAGE }), removeBgTool(onIntent, state.layers.any { it.kind == LayerKind.IMAGE }), censorTool(onIntent), layersTool(onIntent)).toTypedArray()))
            } else {
                val closeLabel = stringResource(Res.string.layer_tools_close)
                ToolBar(
                    selectedLayerTools(layer, layer.id in state.missingLayerIds, onIntent),
                    close = ToolBarClose(closeLabel) { onIntent(PhotoEditorIntent.ClearSelection) },
                    scrollResetKey = layer.locked,
                )
            }
        }
    }
}

// Replace (the picker for this cell), Position (the cell's panel) and Remove (the photo leaves the cell), in that order.
@Composable
private fun cellTools(onIntent: (PhotoEditorIntent) -> Unit): ImmutableList<ToolBarItem> = persistentListOf(
    ToolBarItem(stringResource(UiRes.string.tool_replace), MemixIcons.Replace, { onIntent(PhotoEditorIntent.ReplaceCell) }),
    ToolBarItem(stringResource(UiRes.string.tool_position), MemixIcons.Position, { onIntent(PhotoEditorIntent.OpenPanel(PhotoPanel.CELL_POSITION)) }),
    ToolBarItem(stringResource(Res.string.tool_panel_remove), MemixIcons.Delete, { onIntent(PhotoEditorIntent.RemoveCell) }, ToolTone.Danger),
)

// Opens the Formats panel; first on the main bar so it is found without scrolling (spec P2-02 → Main bar).
@Composable
private fun formatsTool(onIntent: (PhotoEditorIntent) -> Unit) =
    ToolBarItem(stringResource(Res.string.tool_formats), MemixIcons.Formats, { onIntent(PhotoEditorIntent.OpenPanel(PhotoPanel.FORMATS)) })

// Bubbles: Add bubble (icon `bubble`), lands a bubble and opens typing at once. Panels' Layout tool joins with the Panels UI.
@Composable
private fun formatSpecificTool(format: app.memix.core.model.project.PhotoFormat?, onIntent: (PhotoEditorIntent) -> Unit): ToolBarItem? = when (format) {
    is app.memix.core.model.project.PanelsFormat -> ToolBarItem(stringResource(Res.string.tool_panels_layout), MemixIcons.Panels, { onIntent(PhotoEditorIntent.OpenPanel(PhotoPanel.LAYOUT)) })
    app.memix.core.model.project.BubblesFormat -> ToolBarItem(stringResource(Res.string.tool_add_bubble), MemixIcons.Bubble, { onIntent(PhotoEditorIntent.AddBubble) })
    else -> null
}

// Typing a new text, no menu (spec P2-03 → The Text tool); first on the main bar, before Add photo and Layers.
@Composable
private fun textTool(onIntent: (PhotoEditorIntent) -> Unit) =
    ToolBarItem(stringResource(UiRes.string.tool_text), MemixIcons.Text, { onIntent(PhotoEditorIntent.AddText) })

// Stickers: always on the main bar, right after Text (spec P2-14 → Entry and exit); the empty sheet is the way to the first sticker.
@Composable
private fun stickersTool(onIntent: (PhotoEditorIntent) -> Unit) =
    ToolBarItem(stringResource(Res.string.tool_stickers), MemixIcons.Sticker, { onIntent(PhotoEditorIntent.OpenStickers) })

// Edit photo on the main bar, like Crop: only while an image layer exists; a tap selects the main photo and opens the panel.
@Composable
private fun editPhotoTool(onIntent: (PhotoEditorIntent) -> Unit, hasImage: Boolean): ToolBarItem? =
    if (hasImage) ToolBarItem(stringResource(Res.string.tool_edit_photo), MemixIcons.Adjust, { onIntent(PhotoEditorIntent.OpenEditPhoto(null)) }) else null

// Crop on the main bar exists only while the canvas has an image layer; a tap selects the main photo and opens the Crop screen
// (spec P2-04 → Entry and exit). The final order has Edit photo and Crop after Draw; both join the bar as their tickets land.
@Composable
private fun cropTool(onIntent: (PhotoEditorIntent) -> Unit, hasImage: Boolean): ToolBarItem? =
    if (hasImage) ToolBarItem(stringResource(UiRes.string.tool_crop), MemixIcons.Crop, { onIntent(PhotoEditorIntent.OpenCrop(null)) }) else null

// Remove BG on the main bar, like Crop: only while an image layer exists; a tap selects the main photo and opens the cut-out screen (spec P2-07 → D2).
@Composable
private fun removeBgTool(onIntent: (PhotoEditorIntent) -> Unit, hasImage: Boolean): ToolBarItem? =
    if (hasImage) ToolBarItem(stringResource(Res.string.tool_remove_bg), MemixIcons.Cutout, { onIntent(PhotoEditorIntent.OpenCutOut(null)) }) else null

// Draw mode (spec P2-05 → Entry and exit); a selected drawing layer gets the new strokes, else the first stroke makes one.
@Composable
private fun drawTool(onIntent: (PhotoEditorIntent) -> Unit) =
    ToolBarItem(stringResource(Res.string.tool_draw), MemixIcons.Draw, { onIntent(PhotoEditorIntent.OpenDraw) })

// A censor lands in the middle and its panel opens (spec P2-05 → Entry and exit).
@Composable
private fun censorTool(onIntent: (PhotoEditorIntent) -> Unit) =
    ToolBarItem(stringResource(Res.string.tool_censor), MemixIcons.Censor, { onIntent(PhotoEditorIntent.AddCensor) })

// Opens the picker for a new top layer (spec P2-13 → Entry and exit); in its final place just before Layers.
@Composable
private fun addPhotoTool(onIntent: (PhotoEditorIntent) -> Unit) =
    ToolBarItem(stringResource(Res.string.tool_add_photo), MemixIcons.Create, { onIntent(PhotoEditorIntent.AddPhoto) })

// Opens the Layers panel; on an empty canvas it explains instead (spec → States).
@Composable
private fun layersTool(onIntent: (PhotoEditorIntent) -> Unit) =
    ToolBarItem(stringResource(Res.string.tool_layers), MemixIcons.Layers, { onIntent(PhotoEditorIntent.OpenPanel(PhotoPanel.LAYERS)) })

/**
 * Position · Opacity · Delete · Duplicate · Lock · Hide · Layers for the selected layer (a text: Edit text · Style · Position ·
 * Delete · Opacity · Duplicate · Lock · Hide · Layers). Delete comes first when its file
 * is missing (it is what fixes it); Unlock comes before everything when the layer is locked, since the next three only
 * explain. Lock and Hide show the layer's current state in the icon and name the action in the label.
 */
@Composable
private fun selectedLayerTools(layer: SceneLayerUi, missing: Boolean, onIntent: (PhotoEditorIntent) -> Unit): ImmutableList<ToolBarItem> {
    val position = ToolBarItem(stringResource(UiRes.string.tool_position), MemixIcons.Position, {
        if (layer.locked) onIntent(PhotoEditorIntent.LockedLayerTouched(layer.id)) else onIntent(PhotoEditorIntent.OpenPanel(PhotoPanel.POSITION))
    })
    val opacity = ToolBarItem(stringResource(Res.string.tool_layer_opacity), MemixIcons.Opacity, {
        if (layer.locked) onIntent(PhotoEditorIntent.LockedLayerTouched(layer.id)) else onIntent(PhotoEditorIntent.OpenPanel(PhotoPanel.OPACITY))
    })
    val delete = ToolBarItem(stringResource(UiRes.string.tool_delete), MemixIcons.Delete, { onIntent(PhotoEditorIntent.DeleteLayer(layer.id)) }, ToolTone.Danger)
    val duplicate = ToolBarItem(stringResource(UiRes.string.tool_duplicate), MemixIcons.Duplicate, { onIntent(PhotoEditorIntent.DuplicateLayer(layer.id)) })
    val lock = ToolBarItem(
        stringResource(if (layer.locked) Res.string.tool_unlock else Res.string.tool_lock),
        if (layer.locked) MemixIcons.Lock else MemixIcons.Unlock,
        { onIntent(PhotoEditorIntent.SetLocked(layer.id, !layer.locked)) },
    )
    val hide = ToolBarItem(
        stringResource(if (layer.hidden) Res.string.tool_show_layer else Res.string.tool_hide_layer),
        if (layer.hidden) MemixIcons.EyeOff else MemixIcons.Eye,
        { onIntent(PhotoEditorIntent.SetHidden(layer.id, !layer.hidden)) },
    )
    val editPhoto = ToolBarItem(stringResource(Res.string.tool_edit_photo), MemixIcons.Adjust, {
        if (layer.locked) onIntent(PhotoEditorIntent.LockedLayerTouched(layer.id)) else onIntent(PhotoEditorIntent.OpenEditPhoto(layer.id))
    })
    val crop = ToolBarItem(stringResource(UiRes.string.tool_crop), MemixIcons.Crop, {
        if (layer.locked) onIntent(PhotoEditorIntent.LockedLayerTouched(layer.id)) else onIntent(PhotoEditorIntent.OpenCrop(layer.id))
    })
    val removeBg = ToolBarItem(stringResource(Res.string.tool_remove_bg), MemixIcons.Cutout, {
        if (layer.locked) onIntent(PhotoEditorIntent.LockedLayerTouched(layer.id)) else onIntent(PhotoEditorIntent.OpenCutOut(layer.id))
    })
    val layers = layersTool(onIntent)
    val draw = drawTool(onIntent)
    // Censor · Position · Delete · Duplicate · Lock · Hide · Layers: no Opacity (a see-through censor shows what it hides).
    val censor = ToolBarItem(stringResource(Res.string.tool_censor), MemixIcons.Censor, {
        if (layer.locked) onIntent(PhotoEditorIntent.LockedLayerTouched(layer.id)) else onIntent(PhotoEditorIntent.OpenPanel(PhotoPanel.CENSOR))
    })
    val isText = layer.kind == LayerKind.TEXT || layer.kind == LayerKind.BUBBLE
    // A text's own tools come first (spec P2-03 → Selected-text bar); while locked they only explain.
    val editText = ToolBarItem(stringResource(UiRes.string.tool_edit_text), MemixIcons.EditText, {
        if (layer.locked) onIntent(PhotoEditorIntent.LockedLayerTouched(layer.id)) else onIntent(PhotoEditorIntent.EditText)
    })
    val style = ToolBarItem(stringResource(UiRes.string.tool_style), MemixIcons.Style, {
        if (layer.locked) onIntent(PhotoEditorIntent.LockedLayerTouched(layer.id)) else onIntent(PhotoEditorIntent.OpenPanel(PhotoPanel.STYLE))
    })
    val tools = buildList {
        if (layer.locked) add(lock)
        if (isText) {
            // Edit text · Style · Position · Delete · Opacity · Duplicate · Lock · Hide · Layers: Delete stays 4th (four tools fit in view at 360 dp).
            add(editText)
            add(style)
            add(position)
            add(delete)
            add(opacity)
        } else if (layer.kind == LayerKind.CENSOR) {
            add(censor)
            add(position)
            add(delete)
        } else {
            if (missing) add(delete)
            add(position)
            add(opacity)
            if (!missing) add(delete)
            // Position · Opacity · Delete · Draw: Delete stays in view at 360 dp (spec P2-05 → The selected-drawing bar).
            if (layer.kind == LayerKind.DRAWING) add(draw)
            if (layer.kind == LayerKind.IMAGE) {
                add(editPhoto)
                add(crop)
                add(removeBg)
            }
        }
        add(duplicate)
        if (!layer.locked) add(lock)
        add(hide)
        add(layers)
    }
    return remember(layer.id, tools.map { it.label }, layer.locked, layer.hidden, missing) { persistentListOf(*tools.toTypedArray()) }
}

/**
 * The save banner, else the latest toast, `space-2` below the top of the stage. They never stack: a toast under the
 * banner is spoken to screen readers only (spec P1-04 → Saving).
 */
@Composable
private fun StageMessages(state: PhotoEditorUiState, onIntent: (PhotoEditorIntent) -> Unit, onFreeUpSpace: () -> Unit, modifier: Modifier = Modifier) {
    val toast = state.toast
    if (toast != null) ToastTimer(toast, onTimedOut = { onIntent(PhotoEditorIntent.ToastTimedOut(it)) })
    val banner = state.saveBanner
    val shownToast = toast?.takeIf { banner == null && !it.isSpokenOnly }
    Box(modifier.padding(top = MemixSpacing.space2, start = MemixSpacing.space4, end = MemixSpacing.space4)) {
        // Scrolls rather than cutting a message off when a large font makes it taller than a small stage.
        Box(Modifier.verticalScroll(rememberScrollState())) {
            FadingToast(banner) { problem ->
                SaveBanner(
                    problem,
                    onFreeUpSpace = onFreeUpSpace.takeIf { state.canFreeUpSpace },
                    onDismiss = { onIntent(PhotoEditorIntent.DismissSaveBanner) },
                )
            }
            FadingToast(shownToast) { shown -> Toast(toastText(shown.message)) }
        }
        // A new node per toast, so the same text twice ("Undo: Opacity") is announced twice.
        if (toast != null && toast.isSpokenOnly) key(toast.id) { SpokenOnly(toastText(toast.message)) }
        SavedAgainAnnouncement(state.savedAgainCount)
    }
}

/** Hides [toast] after its time: 2 s for what happened, 4 s for an explanation, or longer when "Time to take action" asks. */
@Composable
private fun ToastTimer(toast: PhotoToast, onTimedOut: (id: Long) -> Unit) {
    val accessibilityManager = LocalAccessibilityManager.current
    LaunchedEffect(toast.id) {
        val baseMillis = when (toast.message) {
            is PhotoToastMessage.Explain, PhotoToastMessage.MissingMedia, PhotoToastMessage.LayersMoved, PhotoToastMessage.PanelPhotosDropped, is PhotoToastMessage.BackgroundMissing -> EXPLANATION_TOAST_MILLIS
            is PhotoToastMessage.Undone, is PhotoToastMessage.Redone, is PhotoToastMessage.Edited, is PhotoToastMessage.LayerOrder,
            is PhotoToastMessage.PositionTransformed, is PhotoToastMessage.PositionMoved, is PhotoToastMessage.OpacityChanged,
            is PhotoToastMessage.RatioStepped, PhotoToastMessage.BackgroundImageSet, PhotoToastMessage.BackgroundImageRemoved,
            PhotoToastMessage.LayerAdded, PhotoToastMessage.StickerAdded, PhotoToastMessage.TextAdded, PhotoToastMessage.DrawMode, PhotoToastMessage.DrawSaved, PhotoToastMessage.CensorAdded, PhotoToastMessage.BubbleAdded, is PhotoToastMessage.FormatSet,
            PhotoToastMessage.PictureCopied, PhotoToastMessage.ExportStopped,
            -> WHAT_HAPPENED_TOAST_MILLIS
        }
        delay(recommendedTimeoutMillis(accessibilityManager, baseMillis))
        onTimedOut(toast.id)
    }
}

@Composable
private fun toastText(message: PhotoToastMessage): String = when (message) {
    is PhotoToastMessage.Undone -> stringResource(UiRes.string.editor_undo_toast, editName(message.edit))
    is PhotoToastMessage.Redone -> stringResource(UiRes.string.editor_redo_toast, editName(message.edit))
    is PhotoToastMessage.Explain -> explanationText(message.explanation)
    PhotoToastMessage.MissingMedia -> stringResource(Res.string.photo_missing_media)
    PhotoToastMessage.LayersMoved -> stringResource(Res.string.photo_layers_moved)
    is PhotoToastMessage.Edited -> stringResource(
        UiRes.string.a11y_edit_on_item,
        editName(message.edit),
        stringResource(Res.string.a11y_layer_row, stringResource(Res.string.layer_name_photo), message.position, message.count),
    )
    is PhotoToastMessage.LayerOrder -> stringResource(Res.string.a11y_layer_order, message.position, message.count)
    is PhotoToastMessage.PositionTransformed -> stringResource(
        UiRes.string.a11y_text_transformed,
        stringResource(UiRes.string.position_size_value, message.scalePercent),
        pluralStringResource(UiRes.plurals.a11y_degrees, message.degrees, message.degrees),
    )
    is PhotoToastMessage.PositionMoved -> stringResource(UiRes.string.a11y_text_position, message.acrossPercent, message.downPercent)
    is PhotoToastMessage.OpacityChanged -> stringResource(Res.string.a11y_layer_opacity, message.percent)
    // Ratios are never translated (spec P1-11 → Copy).
    is PhotoToastMessage.RatioStepped -> message.label
    is PhotoToastMessage.BackgroundMissing -> stringResource(UiRes.string.canvas_image_missing_toast, message.ratioLabel)
    PhotoToastMessage.BackgroundImageSet -> stringResource(UiRes.string.a11y_canvas_image_set)
    PhotoToastMessage.BackgroundImageRemoved -> stringResource(UiRes.string.a11y_canvas_image_removed)
    PhotoToastMessage.LayerAdded -> stringResource(Res.string.a11y_layer_added)
    PhotoToastMessage.StickerAdded -> stringResource(Res.string.a11y_sticker_added)
    PhotoToastMessage.TextAdded -> stringResource(Res.string.a11y_layer_text_added)
    is PhotoToastMessage.FormatSet -> stringResource(Res.string.a11y_format_set, formatName(message.choice))
    PhotoToastMessage.BubbleAdded -> stringResource(Res.string.a11y_layer_bubble_added)
    PhotoToastMessage.DrawMode -> stringResource(Res.string.a11y_draw_mode)
    PhotoToastMessage.CensorAdded -> stringResource(Res.string.a11y_layer_censor_added)
    PhotoToastMessage.DrawSaved -> stringResource(Res.string.a11y_draw_done)
    PhotoToastMessage.PanelPhotosDropped -> stringResource(Res.string.panels_photo_dropped)
    PhotoToastMessage.PictureCopied -> stringResource(Res.string.photo_export_copied)
    PhotoToastMessage.ExportStopped -> stringResource(UiRes.string.export_stopped_toast)
}

@Composable
private fun editName(edit: PhotoEdit): String = stringResource(
    when (edit) {
        PhotoEdit.TRANSFORM, PhotoEdit.POSITION, PhotoEdit.TRANSFORM_TEXT, PhotoEdit.POSITION_TEXT -> Res.string.edit_transform_layer
        PhotoEdit.OPACITY -> Res.string.edit_layer_opacity
        PhotoEdit.LOCK -> Res.string.edit_lock
        PhotoEdit.UNLOCK -> Res.string.edit_unlock
        PhotoEdit.HIDE -> Res.string.edit_layer_hide
        PhotoEdit.SHOW -> Res.string.edit_layer_show
        PhotoEdit.REORDER -> UiRes.string.edit_reorder
        PhotoEdit.DELETE -> UiRes.string.edit_delete
        PhotoEdit.DUPLICATE -> UiRes.string.edit_duplicate
        PhotoEdit.CANVAS_RATIO -> UiRes.string.edit_canvas_ratio
        PhotoEdit.CANVAS_BACKGROUND -> UiRes.string.edit_canvas_background
        PhotoEdit.ADD_PHOTO -> Res.string.edit_add_photo
        PhotoEdit.ADD_TEXT -> UiRes.string.edit_add_text
        PhotoEdit.EDIT_TEXT -> UiRes.string.edit_edit_text
        PhotoEdit.TEXT_FONT -> UiRes.string.edit_text_font
        PhotoEdit.TEXT_COLOR -> UiRes.string.edit_text_color
        PhotoEdit.TEXT_OUTLINE -> UiRes.string.edit_text_outline
        PhotoEdit.TEXT_SHADOW -> UiRes.string.edit_text_shadow
        PhotoEdit.TEXT_BOX -> UiRes.string.edit_text_box
        PhotoEdit.CROP -> UiRes.string.edit_crop
        PhotoEdit.REMOVE_BG -> Res.string.edit_remove_bg
        PhotoEdit.ADD_STICKER -> Res.string.edit_add_sticker
        PhotoEdit.CLEAR_CUTOUT -> Res.string.edit_remove_cutout
        PhotoEdit.FILTER -> UiRes.string.edit_filter
        PhotoEdit.ADJUST_BRIGHTNESS, PhotoEdit.ADJUST_CONTRAST, PhotoEdit.ADJUST_SATURATION, PhotoEdit.ADJUST_WARMTH,
        PhotoEdit.ADJUST_SHARPEN, PhotoEdit.ADJUST_VIGNETTE,
        -> UiRes.string.edit_adjust
        PhotoEdit.DEEP_FRY -> UiRes.string.edit_deep_fry
        PhotoEdit.EDIT_RESET -> UiRes.string.edit_photo_reset
        PhotoEdit.FORMAT_PLAIN, PhotoEdit.FORMAT_CLASSIC, PhotoEdit.FORMAT_CAPTION_BAR, PhotoEdit.FORMAT_PANELS,
        PhotoEdit.FORMAT_DEMOTIVATIONAL, PhotoEdit.FORMAT_BUBBLES,
        -> Res.string.edit_format
        PhotoEdit.ADD_BUBBLE -> Res.string.edit_add_bubble
        PhotoEdit.BUBBLE_SHAPE -> Res.string.edit_bubble_shape
        PhotoEdit.BUBBLE_FILL -> Res.string.edit_bubble_fill
        PhotoEdit.BUBBLE_TAIL -> Res.string.edit_bubble_tail
        PhotoEdit.PANELS_LAYOUT -> Res.string.edit_panels_layout
        PhotoEdit.PANELS_DIVIDER -> Res.string.edit_panels_divider
        PhotoEdit.ADD_CELL_PHOTO, PhotoEdit.REMOVE_CELL_PHOTO, PhotoEdit.PANEL_PHOTO_TRANSFORM, PhotoEdit.PANEL_PHOTO_POSITION -> Res.string.edit_panel_photo
        PhotoEdit.DRAW_STROKE -> Res.string.edit_draw
        PhotoEdit.ERASE_STROKE -> Res.string.edit_erase
        PhotoEdit.ADD_CENSOR -> Res.string.edit_censor_add
        PhotoEdit.CENSOR_MODE -> Res.string.edit_censor_mode
        PhotoEdit.CENSOR_SHAPE -> Res.string.edit_censor_shape
        PhotoEdit.CENSOR_STRENGTH -> Res.string.edit_censor_strength
    },
)

@Composable
private fun explanationText(explanation: PhotoExplanation): String = stringResource(
    when (explanation) {
        PhotoExplanation.LAYER_LOCKED -> Res.string.explain_layer_locked
        PhotoExplanation.LAYER_ONE -> Res.string.explain_layer_one
        PhotoExplanation.LAYER_FRONT -> Res.string.explain_layer_front
        PhotoExplanation.LAYER_BACK -> Res.string.explain_layer_back
        PhotoExplanation.LAYER_EDGE -> Res.string.explain_layer_edge
        PhotoExplanation.LAYER_MISSING -> Res.string.explain_layer_missing
        PhotoExplanation.LAYERS_EMPTY -> Res.string.explain_layers_empty
        PhotoExplanation.SIZE_SMALLEST -> UiRes.string.explain_text_smallest
        PhotoExplanation.SIZE_BIGGEST -> UiRes.string.explain_text_biggest
        PhotoExplanation.CANVAS_WIDEST -> UiRes.string.explain_canvas_widest
        PhotoExplanation.CANVAS_TALLEST -> UiRes.string.explain_canvas_tallest
        PhotoExplanation.CANVAS_MIN -> UiRes.string.explain_canvas_min
        PhotoExplanation.CANVAS_MAX -> UiRes.string.explain_canvas_max
        PhotoExplanation.DRAW_NOTHING -> Res.string.explain_draw_nothing
        PhotoExplanation.DRAW_FULL -> Res.string.explain_draw_full
        PhotoExplanation.CENSOR_MIN -> Res.string.explain_censor_min
        PhotoExplanation.CENSOR_MAX -> Res.string.explain_censor_max
        PhotoExplanation.EXPORT_EMPTY -> Res.string.explain_photo_export_empty
        PhotoExplanation.EXPORT_BUSY -> Res.string.explain_photo_export_busy
        PhotoExplanation.EXPORT_NO_SPACE -> Res.string.explain_photo_export_no_space
        PhotoExplanation.MAKE_VIDEO_FAILED -> Res.string.make_video_fail
        PhotoExplanation.MAKE_VIDEO_NO_SPACE -> Res.string.make_video_fail_space
    },
)

/** A panel is as tall as its content up to this share of the window (spec → Layers panel). */
private const val PANEL_MAX_SHARE_OF_WINDOW = 0.4f

// The Crop screen over the editor while a layer is being cropped (a mode: 200 ms slide up, or a 120 ms fade with reduce motion).
@Composable
private fun CropOverlay(state: PhotoEditorUiState, bitmaps: SceneBitmaps, onIntent: (PhotoEditorIntent) -> Unit) {
    val reduceMotion = MemixTheme.reduceMotion
    AnimatedVisibility(
        state.cropLayerId != null,
        enter = if (reduceMotion) fadeIn(tween(MemixMotion.durationPress)) else slideInVertically(tween(MemixMotion.durationSheet)) { it },
        exit = if (reduceMotion) fadeOut(tween(MemixMotion.durationPress)) else slideOutVertically(tween(MemixMotion.durationSheet)) { it },
    ) {
        // Kept for the slide-out, once the layer id is gone.
        val layer = state.layers.firstOrNull { it.id == state.cropLayerId } ?: return@AnimatedVisibility
        val picture = layer.path?.let(bitmaps::imageOf)
        val size = layer.pictureSize ?: picture?.sourceSize ?: return@AnimatedVisibility
        val canvas = state.scene?.canvas
        CropScreen(
            picture = picture?.image,
            pictureSize = size,
            initial = layer.edit ?: ImageEdit.NONE,
            canvasAspect = canvas?.let { it.widthPx.toFloat() / it.heightPx } ?: 1f,
            onDone = { onIntent(PhotoEditorIntent.CropDone(it)) },
            onClose = { onIntent(PhotoEditorIntent.CropClosed) },
        )
    }
}
