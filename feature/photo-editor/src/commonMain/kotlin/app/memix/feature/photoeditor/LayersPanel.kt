package app.memix.feature.photoeditor

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.LayerRow
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.ui.scene.SceneThumbnails
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.layer_hide_a11y
import memix.feature.photo_editor.generated.resources.layer_lock_a11y
import memix.feature.photo_editor.generated.resources.layer_meta_hidden
import memix.feature.photo_editor.generated.resources.layer_meta_locked
import memix.feature.photo_editor.generated.resources.layer_meta_missing
import memix.feature.photo_editor.generated.resources.layer_meta_opacity
import memix.feature.photo_editor.generated.resources.layer_meta_separator
import memix.feature.photo_editor.generated.resources.layer_show_a11y
import memix.feature.photo_editor.generated.resources.layer_unlock_a11y
import memix.feature.photo_editor.generated.resources.layers_move_down
import memix.feature.photo_editor.generated.resources.layers_move_up
import memix.feature.photo_editor.generated.resources.layers_title
import memix.feature.photo_editor.generated.resources.tool_add_photo
import org.jetbrains.compose.resources.stringResource

/**
 * The Layers panel (spec P2-01 → Layers panel): a ToolPanel as tall as its list up to [maxHeight], front layer first. Its top
 * row holds Move layer up and down for the selected layer; the Opacity row follows while a layer is selected (the list's
 * first item when the panel is tight); then one
 * [LayerRow] per layer. A tap selects, the buttons lock and hide only their layer, a long press lifts a row to drag it
 * (LongPress haptic; the list scrolls near its edges, the other rows slide 200 ms to open the slot); the drop is one step.
 */
@Composable
internal fun LayersPanel(
    state: PhotoEditorUiState,
    live: PhotoLive,
    thumbnails: SceneThumbnails,
    maxHeight: Dp,
    onIntent: (PhotoEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.selectedLayer
    // Tight panels give the list the room: the Opacity row scrolls with the rows (spec → Where the Opacity row lives).
    val opacityInList = selected != null && (maxHeight < ROOMY_PANEL_MIN_HEIGHT || LocalDensity.current.fontScale >= TIGHT_FONT_SCALE)
    val listState = rememberLazyListState()
    val frontFirst = remember(state.layers) { state.layers.asReversed() }
    val reorder = rememberLayerReorder(listState, frontFirst.map { it.id }) { id, indexFromFront -> onIntent(PhotoEditorIntent.ReorderLayer(id, indexFromFront)) }
    // Opening, or selecting on the canvas or by undo, brings the row into view (spec → Interactions).
    LaunchedEffect(state.selectedLayerId, state.panel, opacityInList) {
        if (state.panel != PhotoPanel.LAYERS) return@LaunchedEffect
        val index = frontFirst.indexOfFirst { it.id == state.selectedLayerId }
        if (index < 0) return@LaunchedEffect
        val itemsAbove = if (opacityInList) 1 else 0
        // The front row shows with the Add photo button above it (where a new layer lands); any other row scrolls to the top.
        listState.animateScrollToItem(if (index == 0) itemsAbove else index + ITEMS_BEFORE_ROWS + itemsAbove)
    }
    ToolPanel(
        state.panel == PhotoPanel.LAYERS,
        stringResource(Res.string.layers_title),
        stringResource(UiRes.string.panel_done),
        { onIntent(PhotoEditorIntent.ClosePanel) },
        modifier,
        contentMaxHeight = maxHeight,
        headerActions = if (selected != null) ({ MoveButtons(selected.id, onIntent) }) else null,
        scrolls = false,
    ) {
        if (selected != null && !opacityInList) OpacityRow(selected, live, onIntent)
        val opacityItem: (@Composable () -> Unit)? = if (selected != null && opacityInList) ({ OpacityRow(selected, live, onIntent) }) else null
        LayerList(state, frontFirst, listState, reorder, thumbnails, opacityItem, onIntent)
    }
}

@Composable
private fun MoveButtons(layerId: String, onIntent: (PhotoEditorIntent) -> Unit) {
    // One arrow drawing turned up and down; both stay enabled and explain at an end (spec → Reorder without dragging).
    IconButton(MemixIcons.Arrow, stringResource(Res.string.layers_move_up), { onIntent(PhotoEditorIntent.MoveLayer(layerId, towardFront = true)) }, turnDegrees = ARROW_UP)
    IconButton(MemixIcons.Arrow, stringResource(Res.string.layers_move_down), { onIntent(PhotoEditorIntent.MoveLayer(layerId, towardFront = false)) }, turnDegrees = ARROW_DOWN)
}

@Composable
private fun LayerList(
    state: PhotoEditorUiState,
    frontFirst: List<SceneLayerUi>,
    listState: LazyListState,
    reorder: LayerReorder,
    thumbnails: SceneThumbnails,
    opacityItem: (@Composable () -> Unit)?,
    onIntent: (PhotoEditorIntent) -> Unit,
) {
    val count = frontFirst.size
    val labels = LayerActionLabels.rememberLabels()
    val shown = reorder.orderedLike(frontFirst)
    LazyColumn(Modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1)) {
        // Reordering finds rows by key, so this extra first item shifts no index there; it is never a drop target.
        if (opacityItem != null) item(key = OPACITY_ITEM_KEY, contentType = "opacity") { opacityItem() }
        // Where the new layer will land: the first stop before the rows (spec P2-13 → Entry and exit). Never a drop target.
        item(key = ADD_PHOTO_ITEM_KEY, contentType = "add") {
            Button(
                stringResource(Res.string.tool_add_photo),
                { onIntent(PhotoEditorIntent.AddPhoto) },
                Modifier.fillMaxWidth(),
                leadingIcon = MemixIcons.Create,
            )
        }
        items(shown, key = { it.id }, contentType = { "layer" }) { layer ->
            val index = frontFirst.indexOfFirst { it.id == layer.id }
            val lifted = reorder.draggedId == layer.id
            LayerRowFor(
                layer,
                missing = layer.id in state.missingLayerIds || layer.path?.let(thumbnails::failed) == true,
                selected = layer.id == state.selectedLayerId,
                lifted = lifted,
                position = count - index,
                count = count,
                thumbnails = thumbnails,
                labels = labels,
                onIntent = onIntent,
                modifier = Modifier
                    .then(if (lifted) Modifier.zIndex(1f).graphicsLayer { translationY = reorder.dragOffset } else Modifier.animateItemSlide(this))
                    .liftOnLongPress(layer.id, reorder),
            )
        }
    }
}

// The other rows slide 200 ms to open the slot, and not at all with reduce motion (it reads as animator scale 0).
private fun Modifier.animateItemSlide(scope: androidx.compose.foundation.lazy.LazyItemScope): Modifier =
    with(scope) { animateItem(fadeInSpec = null, placementSpec = tween(MemixMotion.durationSheet), fadeOutSpec = null) }

@Composable
private fun LayerRowFor(
    layer: SceneLayerUi,
    missing: Boolean,
    selected: Boolean,
    lifted: Boolean,
    position: Int,
    count: Int,
    thumbnails: SceneThumbnails,
    labels: LayerActionLabels,
    onIntent: (PhotoEditorIntent) -> Unit,
    modifier: Modifier,
) {
    layer.path?.let { path -> LaunchedEffect(path) { thumbnails.request(path) } }
    val spoken = layerSpokenName(layer, missing, position, count)
    val meta = layerMeta(layer, missing)
    LayerRow(
        name = layerName(),
        meta = meta,
        selected = selected,
        lifted = lifted,
        locked = layer.locked,
        hidden = layer.hidden,
        opacity = layer.opacity,
        missing = missing,
        lockDescription = stringResource(if (layer.locked) Res.string.layer_unlock_a11y else Res.string.layer_lock_a11y),
        visibilityDescription = stringResource(if (layer.hidden) Res.string.layer_show_a11y else Res.string.layer_hide_a11y),
        onClick = { onIntent(PhotoEditorIntent.SelectLayer(layer.id)) },
        onToggleLock = { onIntent(PhotoEditorIntent.SetLocked(layer.id, !layer.locked)) },
        onToggleVisibility = { onIntent(PhotoEditorIntent.SetHidden(layer.id, !layer.hidden)) },
        semantics = {
            contentDescription = spoken
            this.selected = selected
            onClick { onIntent(PhotoEditorIntent.SelectLayer(layer.id)); true }
            customActions = layerActions(layer, labels, onIntent)
        },
        modifier = modifier,
    ) {
        val picture = layer.path?.let(thumbnails::pictureOf)
        if (picture != null) Image(BitmapPainter(picture), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

/** "Locked · Hidden · File missing · 40% opacity": only what is worth saying, in this order, or null (spec → LayerRow). */
@Composable
private fun layerMeta(layer: SceneLayerUi, missing: Boolean): String? {
    val percent = (layer.opacity * PERCENT).roundToInt()
    val parts = listOfNotNull(
        if (layer.locked) stringResource(Res.string.layer_meta_locked) else null,
        if (layer.hidden) stringResource(Res.string.layer_meta_hidden) else null,
        if (missing) stringResource(Res.string.layer_meta_missing) else null,
        if (percent < PERCENT) stringResource(Res.string.layer_meta_opacity, percent) else null,
    )
    if (parts.isEmpty()) return null
    return parts.joinToString(" ${stringResource(Res.string.layer_meta_separator)} ")
}

// ---- Drag to reorder -----------------------------------------------------------------------------------------------

/**
 * A row being dragged: its place in the list while it moves ([orderedLike] swaps it live past each neighbour it crosses,
 * so the others slide), its offset from where the list laid it out ([dragOffset]) and the drop. Main thread only.
 */
@Stable
internal class LayerReorder(
    private val listState: LazyListState,
    private val frontFirstIds: () -> List<String>,
    private val haptics: () -> HapticFeedback,
    private val scope: CoroutineScope,
    private val edgePx: Float,
    private val onDrop: (layerId: String, indexFromFront: Int) -> Unit,
) {
    var draggedId by mutableStateOf<String?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set
    private var order by mutableStateOf<List<String>?>(null)
    private var scroller: Job? = null

    /** [layers] in the order the list shows them now: dragging, the dragged row is already where it will drop. */
    fun orderedLike(layers: List<SceneLayerUi>): List<SceneLayerUi> {
        val ids = order ?: return layers
        val byId = layers.associateBy { it.id }
        return ids.mapNotNull(byId::get).takeIf { it.size == layers.size } ?: layers
    }

    fun start(layerId: String) {
        haptics().performHapticFeedback(HapticFeedbackType.LongPress)
        draggedId = layerId
        dragOffset = 0f
        order = frontFirstIds()
        scroller = scope.launch { autoScroll() }
    }

    fun drag(delta: Float) {
        val id = draggedId ?: return
        dragOffset += delta
        val ids = order ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == id } ?: return
        val center = dragged.offset + dragged.size / 2f + dragOffset
        val target = items.firstOrNull { it.key != id && center >= it.offset && center <= it.offset + it.size } ?: return
        val from = ids.indexOf(id)
        val to = ids.indexOf(target.key as String)
        if (from < 0 || to < 0) return
        // The dragged row takes the target's slot, so what lays it out moves by the target's size: keep it under the finger.
        dragOffset += if (to > from) -target.size else target.size
        order = ids.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun end() {
        val id = draggedId
        val ids = order
        finish()
        if (id != null && ids != null) onDrop(id, ids.indexOf(id))
    }

    fun cancel() = finish()

    private fun finish() {
        scroller?.cancel()
        scroller = null
        draggedId = null
        order = null
        dragOffset = 0f
    }

    // Near the panel's top or bottom edge the list scrolls, and the row stays under the finger.
    private suspend fun autoScroll() {
        while (scroller?.isActive == true) {
            val id = draggedId
            val dragged = id?.let { key -> listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } }
            if (dragged != null) {
                val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                val top = dragged.offset + dragOffset
                val bottom = top + dragged.size
                val step = when {
                    top < edgePx -> -SCROLL_STEP_PX
                    bottom > viewportHeight - edgePx -> SCROLL_STEP_PX
                    else -> 0f
                }
                if (step != 0f) dragOffset += listState.scrollBy(step)
            }
            delay(SCROLL_FRAME_MILLIS)
        }
    }

    private companion object {
        const val SCROLL_STEP_PX = 16f
        const val SCROLL_FRAME_MILLIS = 16L
    }
}

@Composable
private fun rememberLayerReorder(listState: LazyListState, frontFirstIds: List<String>, onDrop: (String, Int) -> Unit): LayerReorder {
    val haptics = LocalHapticFeedback.current
    val currentHaptics by rememberUpdatedState(haptics)
    val scope = rememberCoroutineScope()
    val edge = with(LocalDensity.current) { REORDER_EDGE.toPx() }
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentIds by rememberUpdatedState(frontFirstIds)
    return remember(listState) { LayerReorder(listState, { currentIds }, { currentHaptics }, scope, edge) { id, index -> currentOnDrop(id, index) } }
}

private fun Modifier.liftOnLongPress(layerId: String, reorder: LayerReorder): Modifier = pointerInput(layerId, reorder) {
    detectDragGesturesAfterLongPress(
        onDragStart = { reorder.start(layerId) },
        onDrag = { change, delta ->
            change.consume()
            reorder.drag(delta.y)
        },
        onDragEnd = reorder::end,
        onDragCancel = reorder::cancel,
    )
}

private val REORDER_EDGE = MemixSpacing.space6

// Spec P2-01 → Layers panel → Where the Opacity row lives: a layout rule, not a visual token. The list must keep room for two rows.
private val ROOMY_PANEL_MIN_HEIGHT = 300.dp
private const val TIGHT_FONT_SCALE = 1.3f
private const val OPACITY_ITEM_KEY = "opacity"
private const val ADD_PHOTO_ITEM_KEY = "add-photo"

// The Add photo button comes before the first row, so the row at index i is item i + 1 (and one more with the Opacity item).
private const val ITEMS_BEFORE_ROWS = 1
private const val PERCENT = 100
private const val ARROW_UP = 270f
private const val ARROW_DOWN = 90f
