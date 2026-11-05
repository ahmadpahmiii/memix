package app.memix.feature.photoeditor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import app.memix.core.ui.text.TextEntryUi
import app.memix.core.ui.scene.SceneCaptions
import app.memix.core.ui.layers.LayerBox
import app.memix.core.ui.layers.drawTwoToneFrame
import app.memix.core.model.project.Layer
import app.memix.core.model.project.TextLayer
import app.memix.core.designsystem.MemixStroke
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.model.project.Transform
import app.memix.core.ui.editor.FrameEdge
import app.memix.core.ui.layers.CanvasFrameRect
import app.memix.core.ui.layers.GestureTarget
import app.memix.core.ui.layers.LayerGestureEvents
import app.memix.core.ui.layers.LayerSelectionFrame
import app.memix.core.ui.layers.LayerSnapGuides
import app.memix.core.ui.layers.LayerStageState
import app.memix.core.ui.layers.LayerTouchSizes
import app.memix.core.ui.layers.LayerTransformBubble
import app.memix.core.ui.layers.LayerTurnHandle
import app.memix.core.ui.layers.boxIn
import app.memix.core.ui.layers.detectLayerGestures
import app.memix.core.ui.layers.handleCenterOf
import app.memix.core.ui.scene.SceneLive
import app.memix.core.ui.scene.drawPhotoScene
import kotlin.math.max
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.a11y_text_on_canvas
import memix.core.ui.generated.resources.tool_delete
import memix.core.ui.generated.resources.tool_edit_text
import memix.core.ui.generated.resources.tool_style
import memix.core.ui.generated.resources.tool_duplicate
import memix.core.ui.generated.resources.tool_position
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.a11y_layer_hidden
import memix.feature.photo_editor.generated.resources.a11y_bubble_on_canvas
import memix.feature.photo_editor.generated.resources.a11y_layer_locked
import memix.feature.photo_editor.generated.resources.a11y_layer_missing
import memix.feature.photo_editor.generated.resources.a11y_layer_opacity
import memix.feature.photo_editor.generated.resources.a11y_layer_row
import memix.feature.photo_editor.generated.resources.layer_name_photo
import memix.feature.photo_editor.generated.resources.layers_move_down
import memix.feature.photo_editor.generated.resources.layers_move_up
import memix.feature.photo_editor.generated.resources.layer_name_sticker
import memix.feature.photo_editor.generated.resources.photo_empty_body
import memix.feature.photo_editor.generated.resources.photo_empty_body_blank
import memix.feature.photo_editor.generated.resources.photo_empty_title
import memix.feature.photo_editor.generated.resources.photo_stage_a11y
import memix.feature.photo_editor.generated.resources.tool_hide_layer
import memix.feature.photo_editor.generated.resources.tool_layer_opacity
import memix.feature.photo_editor.generated.resources.tool_lock
import memix.feature.photo_editor.generated.resources.tool_show_layer
import memix.feature.photo_editor.generated.resources.tool_unlock
import org.jetbrains.compose.resources.stringResource

/**
 * What differs from the project while a finger, a held arrow or the opacity slider is down, plus the place a released
 * gesture ended at until the project reports it. Read only while drawing, so the scene redraws without recomposing
 * (spec P2-01 → Gestures).
 */
@Stable
internal class PhotoLive(private val stage: LayerStageState, private val held: State<HeldTransform?>) : SceneLive {
    private var settled by mutableStateOf<Pair<String, Transform>?>(null)
    private var opacityLayerId by mutableStateOf<String?>(null)
    private var opacity by mutableFloatStateOf(1f)

    /** The opacity the slider shows now, or null when the project's own value applies. */
    fun opacityOf(): Float? = opacityLayerId?.let { opacity }

    fun setOpacity(layerId: String, value: Float) {
        opacityLayerId = layerId
        opacity = value
    }

    /** A gesture released at [transform]: the layer stays there until the project has it ([release]). */
    fun settle(layerId: String, transform: Transform) {
        settled = layerId to transform
    }

    /** The project has caught up with the gesture, the held arrow or the slider. */
    fun release() {
        settled = null
        opacityLayerId = null
    }

    override fun transformOf(layerId: String): Transform? =
        stage.gesture?.takeIf { it.layerId == layerId }?.transform
            ?: settled?.takeIf { it.first == layerId }?.second
            ?: held.value?.takeIf { it.layerId == layerId }?.transform

    override fun opacityOf(layerId: String): Float? = if (layerId == opacityLayerId) opacity else null
}

/** The stage's geometry: where the canvas frame sits with [coverPx] of the stage's bottom hidden under a panel. */
private data class StageGeometry(val width: Float, val height: Float, val aspectRatio: Float, val insetPx: Float) {
    fun frameFor(coverPx: Float): CanvasFrameRect = CanvasFrameRect.fit(width, (height - coverPx).coerceAtLeast(0f), aspectRatio, insetPx)
}

/**
 * The stage (spec P2-01 → Stage and canvas frame): `stage` black with the canvas frame inset `space-4`, the scene drawn
 * into it by the one scene-drawing function, the selection frame, handle, guides and bubble over it, one screen-reader
 * node per layer, and the empty-canvas card. A panel open over the bottom ([coverPx] of the stage) makes the frame refit
 * above it over 200 ms by redrawing the bitmaps that are there at the smaller size: nothing is laid out again or decoded.
 */
@Composable
internal fun PhotoStage(
    state: PhotoEditorUiState,
    scene: SceneUi?,
    bitmaps: SceneBitmaps,
    captions: SceneCaptions,
    stage: LayerStageState,
    live: PhotoLive,
    coverPx: Int,
    entryField: TextFieldState,
    onIntent: (PhotoEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canvasPanelOpen = state.panel == PhotoPanel.CANVAS
    // Canvas touches do nothing while the Canvas or Formats panel is open (spec P2-02 → Formats panel).
    val panelHoldsCanvas = canvasPanelOpen || state.panel == PhotoPanel.FORMATS
    val entry = state.textEntry
    BoxWithConstraints(modifier.clipToBounds().background(MemixColors.stage).semantics { isTraversalGroup = true }) {
        val density = LocalDensity.current
        val geometry = scene?.let { StageGeometry(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), it.aspectRatio, with(density) { MemixSpacing.space4.toPx() }) }
        // While typing the keyboard shrinks the stage; the pictures keep the size they were decoded for (the stage without it).
        var normalHeight by remember { mutableFloatStateOf(0f) }
        SideEffect { if (entry == null) normalHeight = constraints.maxHeight.toFloat() }
        if (scene != null && geometry != null) {
            FrameRefit(stage, geometry, coverPx)
            // The pictures are decoded for the frame without a panel, and again only when a committed change needs it.
            val normalWidth = (if (entry != null && normalHeight > 0f) geometry.copy(height = normalHeight) else geometry).frameFor(0f).width
            val needed = remember(state.layers, scene.canvas, normalWidth) { neededLongSides(state.layers, scene.canvas, normalWidth) }
            val currentOnIntent by rememberUpdatedState(onIntent)
            LaunchedEffect(needed) {
                bitmaps.ensure(needed) { failed -> currentOnIntent(PhotoEditorIntent.MediaMissing(failed)) }
            }
            SceneLayer(scene, bitmaps, captions, stage, live, entry, entryField)
            GestureLayer(state, stage, live, enabled = !panelHoldsCanvas && entry == null, onIntent)
            FrameEdge({ stage.frame }, visible = canvasPanelOpen || stage.gesture != null)
            if (entry != null) {
                TypingFrame(entry, entryField, state.captionMeasure, scene, stage)
            } else {
                SelectionOverlay(state, scene, stage, panelHoldsCanvas)
                LayerNodes(state, scene, stage, onIntent, interactive = !panelHoldsCanvas)
                SlotHints(state, scene, stage, onIntent)
            }
            if (state.showsEmptyCard && !canvasPanelOpen && entry == null) EmptyCanvasCard(hasHistory = state.emptiedByUser, Modifier.align(Alignment.Center))
        }
    }
}

// Keeps stage.frame at the frame for the current panel cover, animating a change of cover and snapping anything else
// (a ratio change, a new stage size): spec → Motion, "ratio change: at once".
@Composable
private fun FrameRefit(stage: LayerStageState, geometry: StageGeometry, coverPx: Int) {
    val currentGeometry by rememberUpdatedState(geometry)
    val cover = remember { Animatable(coverPx.toFloat()) }
    val reduceMotion = MemixTheme.reduceMotion
    SideEffect {
        stage.stageWidth = geometry.width
        stage.stageHeight = geometry.height
        stage.frame = geometry.frameFor(cover.value)
    }
    LaunchedEffect(coverPx, reduceMotion) {
        if (reduceMotion) cover.snapTo(coverPx.toFloat()) else cover.animateTo(coverPx.toFloat(), tween(MemixMotion.durationSheet))
    }
    LaunchedEffect(Unit) {
        snapshotFlow { cover.value }.collect { stage.frame = currentGeometry.frameFor(it) }
    }
}

// The scene, drawn in the frame in the draw phase: a gesture, a held arrow or a slider moves a layer without recomposing.
@Composable
private fun BoxScope.SceneLayer(scene: SceneUi, bitmaps: SceneBitmaps, captions: SceneCaptions, stage: LayerStageState, live: PhotoLive, entry: TextEntryUi?, entryField: TextFieldState) {
    Box(
        Modifier.matchParentSize().drawBehind {
            val frame = stage.frame
            if (frame.isEmpty) return@drawBehind
            // A text being typed draws live from the field, over the stack or in the place of the layer it edits.
            val layers = if (entry == null) scene.layers else typingLayers(scene.layers, entry, entryField.text.toString())
            inset(frame.left, frame.top, size.width - frame.left - frame.width, size.height - frame.top - frame.height) {
                drawPhotoScene(scene.canvas, layers, bitmaps, live, captions, scene.format)
            }
        },
    )
}

// The scene while [text] is typed: a new text is one more layer at the front; an edited text is its layer with the new words
// (the committed copy is never drawn twice).
private fun typingLayers(layers: List<Layer>, entry: TextEntryUi, text: String): List<Layer> =
    if (entry.itemId == null) {
        layers + TextLayer(TYPING_LAYER_ID, text, entry.style, entry.transform)
    } else {
        layers.map { if (it.id == entry.itemId && it is TextLayer) it.copy(text = text) else it }
    }

private const val TYPING_LAYER_ID = "typing"

/**
 * The frame of the text being typed (spec P2-03 → Typing): its words measured on every keystroke with the caption renderer, the
 * two-tone frame around them, or an empty `touch-target` square at the landing spot before the first letter. No handle:
 * nothing can be dragged while the keyboard is up.
 */
@Composable
private fun BoxScope.TypingFrame(entry: TextEntryUi, field: TextFieldState, measure: PhotoCaptionMeasure?, scene: SceneUi, stage: LayerStageState) {
    val density = LocalDensity.current
    val targetPx = with(density) { MemixSize.touchTarget.toPx() }
    val outsetPx = with(density) { MemixSpacing.space1.toPx() }
    val selectionPx = with(density) { MemixStroke.strokeSelection.toPx() }
    val hairlinePx = with(density) { MemixStroke.strokeHairline.toPx() }
    val text = field.text.toString()
    val block = remember(text, entry.style, scene.canvas, measure) {
        if (text.isEmpty() || measure == null) null else measure.blockOf(text, entry.style, scene.canvas.widthPx, scene.canvas.heightPx)
    }
    Spacer(
        Modifier.matchParentSize().drawBehind {
            val frame = stage.frame
            if (frame.isEmpty) return@drawBehind
            val box = LayerBox.of(block?.widthPx ?: 0f, block?.heightPx ?: 0f, entry.transform, frame, scene.canvas.widthPx)
            val shown = if (block == null) box.copy(halfWidth = targetPx / 2 - outsetPx, halfHeight = targetPx / 2 - outsetPx) else box
            drawTwoToneFrame(shown, outsetPx, selectionPx, hairlinePx)
        },
    )
}

// A tap on nothing clears the selection (there is no play or pause to compete with); touches that hit a layer are the
// layer gestures', which sit after it and take them first.
@Composable
private fun BoxScope.GestureLayer(state: PhotoEditorUiState, stage: LayerStageState, live: PhotoLive, enabled: Boolean, onIntent: (PhotoEditorIntent) -> Unit) {
    if (!enabled) return
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val sizes = remember(density) {
        with(density) { LayerTouchSizes(MemixSize.touchTarget.toPx(), MemixSpacing.space1.toPx(), MemixSpacing.space2.toPx()) }
    }
    val visible = remember(state.layers) { state.layers.filter { !it.hidden } }
    val currentTargets by rememberUpdatedState<List<GestureTarget>>(visible)
    val selected by rememberUpdatedState(state.selectedLayerId)
    val canvasWidth by rememberUpdatedState(state.canvas?.widthPx ?: 0)
    val currentOnIntent by rememberUpdatedState(onIntent)
    val currentHaptics by rememberUpdatedState(haptics)
    val events = remember(stage, live) { PhotoGestureEvents(live) { currentOnIntent(it) } }
    CancelGestureOnBack(stage, onIntent)
    Box(
        Modifier
            .matchParentSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnIntent(PhotoEditorIntent.ClearSelection) }) }
            .pointerInput(stage, sizes) {
                detectLayerGestures(stage, { currentTargets }, { selected }, { canvasWidth }, sizes, { currentHaptics }, events)
            },
    )
}

/** The stage's gestures as the photo editor's intents; a released gesture holds its layer at its final place meanwhile. */
private class PhotoGestureEvents(private val live: PhotoLive, private val onIntent: (PhotoEditorIntent) -> Unit) : LayerGestureEvents {
    override fun onTap(layerId: String) = onIntent(PhotoEditorIntent.SelectLayer(layerId))

    override fun onStarted(layerId: String) = onIntent(PhotoEditorIntent.LayerGestureStarted(layerId))

    override fun onEnded(layerId: String, transform: Transform) {
        live.settle(layerId, transform)
        onIntent(PhotoEditorIntent.LayerGestureEnded(layerId, transform))
    }

    override fun onLockedTouch(layerId: String) = onIntent(PhotoEditorIntent.LockedLayerTouched(layerId))
}

/** System back during a gesture cancels it: the layer goes back to where it was (spec → System back order). */
@Composable
private fun CancelGestureOnBack(stage: LayerStageState, onIntent: (PhotoEditorIntent) -> Unit) {
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = stage.gesture != null,
        onBackCompleted = {
            stage.gesture?.cancelled = true
            stage.gesture = null
            onIntent(PhotoEditorIntent.LayerGestureCancelled)
        },
    )
}

// The two-tone frame and the scale-and-rotate handle of the selected layer (a lock badge in the handle's place when it is
// locked), the snap guides and the value bubble. With the Canvas panel open the frame and handle are hidden.
@Composable
private fun BoxScope.SelectionOverlay(state: PhotoEditorUiState, scene: SceneUi, stage: LayerStageState, hidden: Boolean) {
    val selected = state.selectedLayer?.takeIf { !it.hidden && !hidden }
    val canvasWidth = scene.canvas.widthPx
    if (selected != null) {
        LayerSelectionFrame(stage, selected, canvasWidth)
        if (selected.locked) LockBadge(stage, selected, canvasWidth) else LayerTurnHandle(stage, selected, canvasWidth)
    }
    LayerSnapGuides(stage)
    LayerTransformBubble(stage)
}

// A scrim chip with the closed lock where the handle would be: the layer can't be turned or sized.
@Composable
private fun BoxScope.LockBadge(stage: LayerStageState, target: GestureTarget, canvasWidthPx: Int) {
    val density = LocalDensity.current
    val outsetPx = with(density) { MemixSpacing.space1.toPx() }
    val badgePx = with(density) { MemixSize.badge.toPx() }
    Box(
        Modifier
            .offset {
                val center = handleCenterOf(target.boxIn(stage.frame, canvasWidthPx), stage.stageWidth, stage.stageHeight, outsetPx)
                IntOffset((center.x - badgePx / 2).roundToInt(), (center.y - badgePx / 2).roundToInt())
            }
            .size(MemixSize.badge)
            .clip(MemixShapes.radiusSm)
            .background(MemixColors.scrim)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(MemixIcons.Lock, contentDescription = null, tint = MemixColors.text, modifier = Modifier.size(MemixSize.iconSmall))
    }
}

/**
 * One screen-reader node per visible layer, front to back, over its turned box (spec → Accessibility): "Photo, layer 1 of
 * 3", then what is special as sentences; selected when it is. Double-tap selects; the actions are the single-pointer
 * routes that replace dragging and pinching. No touch handling here: the stage's gestures do that.
 */
@Composable
private fun BoxScope.LayerNodes(state: PhotoEditorUiState, scene: SceneUi, stage: LayerStageState, onIntent: (PhotoEditorIntent) -> Unit, interactive: Boolean) {
    val density = LocalDensity.current
    val canvasLabel = stringResource(Res.string.photo_stage_a11y, scene.ratioLabel)
    val labels = LayerActionLabels.rememberLabels()
    val count = state.layers.size
    Box(Modifier.matchParentSize().semantics { contentDescription = canvasLabel; isTraversalGroup = true }) {
        state.layers.forEachIndexed { index, layer ->
            if (layer.hidden) return@forEachIndexed
            val missing = layer.id in state.missingLayerIds
            val spoken = layerSpokenName(layer, missing, position = count - index, count = count, allWords = true)
            val box = layer.boxIn(stage.frame, scene.canvas.widthPx)
            val corners = listOf(box.corner(-1, -1, 0f), box.corner(1, -1, 0f), box.corner(1, 1, 0f), box.corner(-1, 1, 0f))
            val left = corners.minOf { it.x }
            val top = corners.minOf { it.y }
            val right = max(corners.maxOf { it.x }, left + 1f)
            val bottom = max(corners.maxOf { it.y }, top + 1f)
            val isSelected = layer.id == state.selectedLayerId
            Box(
                Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { (right - left).toDp() }, with(density) { (bottom - top).toDp() })
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        selected = isSelected
                        if (!interactive) return@clearAndSetSemantics
                        onClick { onIntent(PhotoEditorIntent.SelectLayer(layer.id)); true }
                        customActions = layerActions(layer, labels, onIntent)
                    },
            )
        }
    }
}

/** The spoken names of the single-pointer actions on a layer, read once per composition. */
internal class LayerActionLabels(
    val position: String,
    val opacity: String,
    val lock: String,
    val unlock: String,
    val hide: String,
    val show: String,
    val duplicate: String,
    val delete: String,
    val moveUp: String,
    val moveDown: String,
    val editText: String,
    val style: String,
) {
    companion object {
        @Composable
        fun rememberLabels() = LayerActionLabels(
            position = stringResource(UiRes.string.tool_position),
            opacity = stringResource(Res.string.tool_layer_opacity),
            lock = stringResource(Res.string.tool_lock),
            unlock = stringResource(Res.string.tool_unlock),
            hide = stringResource(Res.string.tool_hide_layer),
            show = stringResource(Res.string.tool_show_layer),
            duplicate = stringResource(UiRes.string.tool_duplicate),
            delete = stringResource(UiRes.string.tool_delete),
            moveUp = stringResource(Res.string.layers_move_up),
            moveDown = stringResource(Res.string.layers_move_down),
            editText = stringResource(UiRes.string.tool_edit_text),
            style = stringResource(UiRes.string.tool_style),
        )
    }
}

/** Position, Opacity, Lock or Unlock, Hide or Show, Duplicate, Delete, Move up, Move down on [layer] (spec → Accessibility). */
internal fun layerActions(layer: SceneLayerUi, labels: LayerActionLabels, onIntent: (PhotoEditorIntent) -> Unit): List<CustomAccessibilityAction> {
    fun action(label: String, intent: PhotoEditorIntent) = CustomAccessibilityAction(label) { onIntent(intent); true }
    fun open(label: String, panel: PhotoPanel) = CustomAccessibilityAction(label) {
        onIntent(PhotoEditorIntent.SelectLayer(layer.id))
        onIntent(PhotoEditorIntent.OpenPanel(panel))
        true
    }
    val textActions = if (layer.kind != LayerKind.TEXT) emptyList() else listOf(
        CustomAccessibilityAction(labels.editText) {
            onIntent(PhotoEditorIntent.SelectLayer(layer.id))
            // A tap on the selected text opens typing, so a second select would; EditText is the direct route.
            onIntent(PhotoEditorIntent.EditText)
            true
        },
        open(labels.style, PhotoPanel.STYLE),
    )
    return textActions + listOf(
        open(labels.position, PhotoPanel.POSITION),
        open(labels.opacity, PhotoPanel.OPACITY),
        action(if (layer.locked) labels.unlock else labels.lock, PhotoEditorIntent.SetLocked(layer.id, !layer.locked)),
        action(if (layer.hidden) labels.show else labels.hide, PhotoEditorIntent.SetHidden(layer.id, !layer.hidden)),
        action(labels.duplicate, PhotoEditorIntent.DuplicateLayer(layer.id)),
        action(labels.delete, PhotoEditorIntent.DeleteLayer(layer.id)),
        action(labels.moveUp, PhotoEditorIntent.MoveLayer(layer.id, towardFront = true)),
        action(labels.moveDown, PhotoEditorIntent.MoveLayer(layer.id, towardFront = false)),
    )
}

/** "Photo, layer 2 of 3. Locked. 40 percent opacity." (`position` counts from the front); the extras are sentences, never a state. */
@Composable
internal fun layerSpokenName(layer: SceneLayerUi, missing: Boolean, position: Int, count: Int, allWords: Boolean = false): String {
    val name = stringResource(Res.string.a11y_layer_row, spokenKindName(layer, allWords), position, count)
    val locked = if (layer.locked) stringResource(Res.string.a11y_layer_locked) else null
    val hidden = if (layer.hidden) stringResource(Res.string.a11y_layer_hidden) else null
    val gone = if (missing) stringResource(Res.string.a11y_layer_missing) else null
    val percent = (layer.opacity * PERCENT).roundToInt()
    val faded = if (percent < PERCENT) stringResource(Res.string.a11y_layer_opacity, percent) else null
    return listOfNotNull(name, locked, hidden, gone, faded).joinToString(" ")
}

/**
 * What a layer is called: "Photo" for every image layer and "Sticker" for every sticker (a number that changes on reorder
 * would mislead); a text is its words, [allWords] or only the first non-blank line (a row never reads 250 characters).
 */
@Composable
internal fun spokenKindName(layer: SceneLayerUi, allWords: Boolean): String = when (layer.kind) {
    LayerKind.IMAGE -> stringResource(Res.string.layer_name_photo)
    LayerKind.STICKER -> stringResource(Res.string.layer_name_sticker)
    LayerKind.TEXT -> stringResource(UiRes.string.a11y_text_on_canvas, if (allWords) layer.text.orEmpty() else firstLineOf(layer.text.orEmpty()))
    LayerKind.BUBBLE -> stringResource(Res.string.a11y_bubble_on_canvas, if (allWords) layer.text.orEmpty() else firstLineOf(layer.text.orEmpty()))
}

/** A row's visible name: "Photo", "Sticker", or a text's first non-blank line as typed, in the interface font. */
@Composable
internal fun rowName(layer: SceneLayerUi): String = when (layer.kind) {
    LayerKind.IMAGE -> stringResource(Res.string.layer_name_photo)
    LayerKind.STICKER -> stringResource(Res.string.layer_name_sticker)
    LayerKind.TEXT, LayerKind.BUBBLE -> firstLineOf(layer.text.orEmpty())
}

private fun firstLineOf(text: String): String = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

// Over the stage, not the frame, so a narrow 9:16 frame doesn't cut the Hindi body at 200% (P1-04's lesson).
@Composable
private fun EmptyCanvasCard(hasHistory: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(horizontal = MemixSpacing.space4)
            .clip(MemixShapes.radiusLg)
            .background(MemixColors.surfaceRaised)
            .verticalScroll(rememberScrollState())
            .padding(MemixSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.photo_empty_title), MemixTheme.type.title, textAlign = TextAlign.Center)
        // Two bodies: a canvas that never had a layer says what to tap; an emptied one points to Undo first (spec P2-03 → The empty-canvas card).
        Text(stringResource(if (hasHistory) Res.string.photo_empty_body else Res.string.photo_empty_body_blank), MemixTheme.type.body, textAlign = TextAlign.Center)
    }
}

private const val PERCENT = 100
