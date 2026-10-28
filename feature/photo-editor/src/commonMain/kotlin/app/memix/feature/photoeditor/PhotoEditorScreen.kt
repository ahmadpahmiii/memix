package app.memix.feature.photoeditor

import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.semantics.traversalIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
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
import app.memix.core.ui.scene.SceneImageSource
import app.memix.core.ui.scene.SceneThumbnails
import app.memix.feature.mediaimport.ImportWording
import app.memix.feature.mediaimport.ProjectImportSheet
import app.memix.feature.mediaimport.rememberImportSheetShown
import kotlin.math.max
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import memix.core.ui.generated.resources.Res as UiRes
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
import memix.feature.photo_editor.generated.resources.edit_add_photo
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
import memix.feature.photo_editor.generated.resources.tool_hide_layer
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
 * and its layers, and the tool bar, which a tool panel replaces. [imageSource] decodes the pictures: the composition root
 * passes the engine's, which a feature can't depend on. [onFreeUpSpace] opens the phone's storage manager asking for the
 * given number of bytes.
 */
@Composable
fun PhotoEditorScreen(
    state: PhotoEditorUiState,
    onIntent: (PhotoEditorIntent) -> Unit,
    imageSource: SceneImageSource,
    onFreeUpSpace: (requestedBytes: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // System back closes the editor through the app's own navigation, which saves on the way out. The editor takes back
    // itself while something inside it comes first (a panel, the selection) or while saving fails, to ask first.
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !state.unsavedSheetOpen && (state.panel != null || state.selectedLayerId != null || state.isSaveFailing),
        onBackCompleted = { onIntent(PhotoEditorIntent.Back) },
    )
    LifecycleEventEffect(Lifecycle.Event.ON_START) { onIntent(PhotoEditorIntent.AppStarted) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onIntent(PhotoEditorIntent.AppStopped) }
    val freeUpSpaceForSave = remember(onFreeUpSpace) { { onFreeUpSpace(ImportMediaUseCase.HEADROOM_BYTES) } }

    val importSheetShown = rememberImportSheetShown(state.photoImport)
    val scope = rememberCoroutineScope()
    val bitmaps = remember(imageSource) { SceneBitmaps(imageSource, scope) }
    DisposableEffect(bitmaps) { onDispose { bitmaps.release() } }
    val thumbnails = remember(imageSource) { SceneThumbnails(imageSource, scope) }
    DisposableEffect(thumbnails) { onDispose { thumbnails.release() } }
    val stage = remember { LayerStageState() }
    val held = rememberUpdatedState(state.held)
    val live = remember(stage) { PhotoLive(stage, held) }
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
                if (goesBack && event.type == KeyEventType.KeyUp) onIntent(PhotoEditorIntent.Back)
                goesBack
            },
    ) {
        val panelMaxHeight = maxHeight * PANEL_MAX_SHARE_OF_WINDOW
        Column(
            Modifier
                .fillMaxSize()
                .coveredByModal(state.unsavedSheetOpen || importSheetShown)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            TopBar(state, onIntent)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PhotoStage(state, state.scene, bitmaps, stage, live, coverPx, onIntent, Modifier.fillMaxSize())
                StageMessages(state, onIntent, freeUpSpaceForSave, Modifier.align(Alignment.TopCenter).semantics { traversalIndex = -1f })
            }
            Box(Modifier.coveredByModal(panelOpen).onSizeChanged { toolRegionPx = it.height }) {
                ToolBarRegion(state, onIntent)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { if (it.height > 0) panelHeightPx = it.height }) {
            PhotoPanels(state, live, thumbnails, panelMaxHeight, onIntent)
            LayersPanel(state, live, thumbnails, panelMaxHeight, onIntent)
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
        UnsavedChangesSheet(
            state.unsavedSheetOpen,
            onStay = { onIntent(PhotoEditorIntent.StayInEditor) },
            onLeave = { onIntent(PhotoEditorIntent.LeaveAnyway) },
            onFreeUpSpace = freeUpSpaceForSave.takeIf { state.canFreeUpSpace },
        )
    }
}

/**
 * Close at the start, the ratio pill in the center of the screen (spec → Top bar) and Undo and Redo at the end. A custom
 * [Layout], not overlapping aligns: Close and the end group are measured first and never shrink; the pill is centered on the
 * screen but clamped between them with `space-2` to spare, so at 200% font only the pill grows.
 */
@Composable
private fun TopBar(state: PhotoEditorUiState, onIntent: (PhotoEditorIntent) -> Unit) {
    val gap = MemixSpacing.space2
    val scene = state.scene
    Layout(
        content = {
            IconButton(MemixIcons.Close, stringResource(UiRes.string.editor_close), { onIntent(PhotoEditorIntent.Close) })
            Row(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
                IconButton(MemixIcons.Undo, stringResource(UiRes.string.editor_undo), { onIntent(PhotoEditorIntent.Undo) }, enabled = state.canUndo)
                IconButton(MemixIcons.Redo, stringResource(UiRes.string.editor_redo), { onIntent(PhotoEditorIntent.Redo) }, enabled = state.canRedo)
            }
            if (scene != null) {
                val label = state.canvasPanel?.shownTerms?.lowest()?.toString() ?: scene.ratioLabel
                SettingPill(
                    label,
                    stringResource(UiRes.string.canvas_pill_a11y, label),
                    expanded = state.panel == PhotoPanel.CANVAS,
                    onClick = { onIntent(PhotoEditorIntent.RatioPill) },
                )
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = MemixSpacing.space2, vertical = MemixSpacing.space1),
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val close = measurables[0].measure(loose)
        val endGroup = measurables[1].measure(loose)
        val pill = measurables.getOrNull(2)?.measure(loose)
        val width = constraints.maxWidth
        val height = maxOf(close.height, endGroup.height, pill?.height ?: 0)
        layout(width, height) {
            close.place(0, (height - close.height) / 2)
            endGroup.place(width - endGroup.width, (height - endGroup.height) / 2)
            if (pill != null) {
                val centered = (width - pill.width) / 2
                val rightmost = width - endGroup.width - gapPx - pill.width
                val leftmost = close.width + gapPx
                pill.place(centered.coerceAtMost(rightmost).coerceAtLeast(leftmost), (height - pill.height) / 2)
            }
        }
    }
}

/** The main bar with nothing selected, the selected-layer bar with a layer (spec → Tool bars); they crossfade in 120 ms. */
@Composable
private fun ToolBarRegion(state: PhotoEditorUiState, onIntent: (PhotoEditorIntent) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MemixColors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        // The bar that fades out keeps the layer it was showing, after the selection is gone.
        val lastLayer = remember { arrayOfNulls<SceneLayerUi>(1) }
        state.selectedLayer?.let { lastLayer[0] = it }
        Crossfade(state.selectedLayerId != null, animationSpec = tween(MemixMotion.durationPress), label = "tool bar") { hasSelection ->
            val layer = if (hasSelection) state.selectedLayer ?: lastLayer[0] else null
            if (layer == null) {
                ToolBar(persistentListOf(addPhotoTool(onIntent), layersTool(onIntent)))
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

// Opens the picker for a new top layer (spec P2-13 → Entry and exit); in its final place just before Layers.
@Composable
private fun addPhotoTool(onIntent: (PhotoEditorIntent) -> Unit) =
    ToolBarItem(stringResource(Res.string.tool_add_photo), MemixIcons.Create, { onIntent(PhotoEditorIntent.AddPhoto) })

// Opens the Layers panel; on an empty canvas it explains instead (spec → States).
@Composable
private fun layersTool(onIntent: (PhotoEditorIntent) -> Unit) =
    ToolBarItem(stringResource(Res.string.tool_layers), MemixIcons.Layers, { onIntent(PhotoEditorIntent.OpenPanel(PhotoPanel.LAYERS)) })

/**
 * Position · Opacity · Delete · Duplicate · Lock · Hide · Layers for the selected layer. Delete comes first when its file
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
    val layers = layersTool(onIntent)
    val tools = buildList {
        if (layer.locked) add(lock)
        if (missing) add(delete)
        add(position)
        add(opacity)
        if (!missing) add(delete)
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
            is PhotoToastMessage.Explain, PhotoToastMessage.MissingMedia, PhotoToastMessage.LayersMoved, is PhotoToastMessage.BackgroundMissing -> EXPLANATION_TOAST_MILLIS
            is PhotoToastMessage.Undone, is PhotoToastMessage.Redone, is PhotoToastMessage.Edited, is PhotoToastMessage.LayerOrder,
            is PhotoToastMessage.PositionTransformed, is PhotoToastMessage.PositionMoved, is PhotoToastMessage.OpacityChanged,
            is PhotoToastMessage.RatioStepped, PhotoToastMessage.BackgroundImageSet, PhotoToastMessage.BackgroundImageRemoved,
            PhotoToastMessage.LayerAdded,
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
}

@Composable
private fun editName(edit: PhotoEdit): String = stringResource(
    when (edit) {
        PhotoEdit.TRANSFORM, PhotoEdit.POSITION -> Res.string.edit_transform_layer
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
    },
)

/** A panel is as tall as its content up to this share of the window (spec → Layers panel). */
private const val PANEL_MAX_SHARE_OF_WINDOW = 0.4f
