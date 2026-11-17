package app.memix.feature.videoeditor.overlays

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.memix.core.model.project.MediaKind
import app.memix.core.ui.layers.LayerSelectionFrame
import app.memix.core.ui.layers.LayerStageState
import app.memix.core.ui.layers.LayerTurnHandle
import app.memix.core.ui.layers.boxIn
import app.memix.feature.videoeditor.CanvasUi
import app.memix.feature.videoeditor.EditorPreview
import app.memix.feature.videoeditor.OverlayFrames
import app.memix.feature.videoeditor.OverlayPanel
import app.memix.feature.videoeditor.OverlayStandInUi
import app.memix.feature.videoeditor.OverlayUi
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.import_item_photo
import memix.core.ui.generated.resources.import_item_video
import memix.core.ui.generated.resources.layers_opacity
import memix.core.ui.generated.resources.tool_delete
import memix.core.ui.generated.resources.tool_position
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.a11y_overlay_node
import memix.feature.video_editor.generated.resources.tool_layer_order
import memix.feature.video_editor.generated.resources.tool_split
import org.jetbrains.compose.resources.stringResource

/**
 * What the editor draws over the preview for overlays (spec P4-01b → The selected overlay on the stage, Live feedback): the stand-in
 * of an overlay a finger or a held arrow moves (its own frame, drawn where the fingers put it while the preview leaves it out, and
 * until the preview shows the commit), the two-tone frame and the scale-and-rotate handle of the selected overlay, and one
 * screen-reader node per overlay shown at the playhead. The snap guides and the value bubble are the caption layer's (one gesture
 * draws them once). [visible] is back to front. With [interactive] false (the Canvas panel is open, or a caption is typed) the
 * frame and handle are hidden and the nodes are only heard.
 */
@Composable
internal fun BoxScope.OverlayLayer(
    state: LayerStageState,
    canvas: CanvasUi,
    visible: List<OverlayUi>,
    all: List<OverlayUi>,
    selectedItemId: String?,
    standIn: OverlayStandInUi?,
    hiddenItemId: String?,
    frames: OverlayFrames?,
    preview: EditorPreview?,
    selectedFocus: FocusRequester,
    interactive: Boolean,
    playheadUs: () -> Long,
    onIntent: (VideoEditorIntent) -> Unit,
) {
    val frame = state.frame
    if (frame.isEmpty) return
    if (frames != null) {
        PrefetchFrames(frames, preview, all, canvas, frame, stageLongSidePx = max(state.stageWidth, state.stageHeight).roundToInt())
        StandIn(state, frames, all, standIn, hiddenItemId, canvas)
    }
    if (interactive) {
        val selected = visible.lastOrNull { it.itemId == selectedItemId }
        // While a committed change waits for the preview, the frame follows the stand-in's place; a keyed overlay's follows the keyframes,
        // read while drawing and placing so a playing frame never recomposes the stage.
        val framed: (() -> OverlayUi)? = selected?.let { overlay ->
            {
                val timed = overlay.atTime(playheadUs())
                standIn?.takeIf { it.itemId == overlay.itemId }?.let { timed.copy(transform = it.transform) } ?: timed
            }
        }
        if (framed != null) LayerSelectionFrame(state, framed, canvas.widthPx)
        if (framed != null) LayerTurnHandle(state, framed, canvas.widthPx)
    }
    OverlayNodes(state, visible, selectedItemId, canvas, selectedFocus, interactive, playheadUs, onIntent)
}

// The stand-in's picture of each overlay shown, asked for once the playhead rests (a moving playhead asks for nothing): a touch finds it ready.
@OptIn(FlowPreview::class)
@Composable
private fun PrefetchFrames(frames: OverlayFrames, preview: EditorPreview?, all: List<OverlayUi>, canvas: CanvasUi, frame: app.memix.core.ui.layers.CanvasFrameRect, stageLongSidePx: Int) {
    if (preview == null) return
    LaunchedEffect(frames, preview, all, frame, stageLongSidePx) {
        preview.positionUs.debounce(PLAYHEAD_REST_MS).collect { atUs ->
            all.filter { it.isShownAt(atUs) }.forEach { overlay ->
                // Decoded at the size it is drawn at (box x scale on the stage), never more than the stage's long side.
                val drawnPx = max(overlay.boxWidthPx, overlay.boxHeightPx) * overlay.transform.scale * frame.width / canvas.widthPx.coerceAtLeast(1)
                frames.want(overlay, atUs, drawnPx.roundToInt().coerceIn(MIN_FRAME_SIDE_PX, stageLongSidePx.coerceAtLeast(MIN_FRAME_SIDE_PX)))
            }
        }
    }
}

// The overlay under a finger or a held arrow, or a commit the preview hasn't shown: its own frame at the live transform, clipped to the canvas frame. Read in draw, so a gesture frame redraws only this.
@Composable
private fun BoxScope.StandIn(state: LayerStageState, frames: OverlayFrames, all: List<OverlayUi>, standIn: OverlayStandInUi?, hiddenItemId: String?, canvas: CanvasUi) {
    // Looked up once per list, not per frame.
    val byId = remember(all) { all.associateBy { it.itemId } }
    val overlayOf = { itemId: String -> byId[itemId] }
    Spacer(
        Modifier.matchParentSize().drawBehind {
            frames.version
            val itemId = hiddenItemId ?: return@drawBehind
            val overlay = overlayOf(itemId) ?: return@drawBehind
            val gesture = state.gesture?.takeIf { it.layerId == itemId }
            val transform = gesture?.transform ?: standIn?.takeIf { it.itemId == itemId }?.transform ?: return@drawBehind
            val image = frames.imageOf(itemId) ?: return@drawBehind
            val frame = state.frame
            val box = overlay.boxIn(frame, canvas.widthPx, transform)
            val width = (box.halfWidth * 2).roundToInt().coerceAtLeast(1)
            val height = (box.halfHeight * 2).roundToInt().coerceAtLeast(1)
            clipRect(frame.left, frame.top, frame.left + frame.width, frame.top + frame.height) {
                withTransform({
                    translate(box.center.x, box.center.y)
                    rotate(box.rotationDegrees, Offset.Zero)
                }) {
                    drawImage(image, dstOffset = IntOffset(-width / 2, -height / 2), dstSize = IntSize(width, height), alpha = standIn?.takeIf { it.itemId == itemId }?.opacity ?: overlay.opacity)
                }
            }
        },
    )
}

/**
 * One node per overlay shown, front to back (spec P4-01b → Accessibility): "Overlay, Video, layer 1 of 2", selected when it is;
 * double-tap selects it; actions Position, Opacity, Layer order, Delete and Split. No touch handling: the stage's gestures do that.
 */
@Composable
private fun BoxScope.OverlayNodes(
    state: LayerStageState,
    visible: List<OverlayUi>,
    selectedItemId: String?,
    canvas: CanvasUi,
    selectedFocus: FocusRequester,
    interactive: Boolean,
    playheadUs: () -> Long,
    onIntent: (VideoEditorIntent) -> Unit,
) {
    val density = LocalDensity.current
    val positionLabel = stringResource(UiRes.string.tool_position)
    val opacityLabel = stringResource(UiRes.string.layers_opacity)
    val orderLabel = stringResource(Res.string.tool_layer_order)
    val deleteLabel = stringResource(UiRes.string.tool_delete)
    val splitLabel = stringResource(Res.string.tool_split)
    val videoLabel = stringResource(UiRes.string.import_item_video)
    val photoLabel = stringResource(UiRes.string.import_item_photo)
    for ((index, overlay) in visible.withIndex().reversed()) {
        val kind = if (overlay.media.kind == MediaKind.IMAGE) photoLabel else videoLabel
        val label = stringResource(Res.string.a11y_overlay_node, kind, visible.size - index, visible.size)
        val isSelected = overlay.itemId == selectedItemId
        Box(
            Modifier
                // Placed and sized from the keyframes' look under the playhead, in layout, so a keyed overlay's node follows it.
                .offset { nodeBounds(overlay.atTime(playheadUs()), state.frame, canvas.widthPx).let { IntOffset(it.left.roundToInt(), it.top.roundToInt()) } }
                .layout { measurable, _ ->
                    val bounds = nodeBounds(overlay.atTime(playheadUs()), state.frame, canvas.widthPx)
                    val placeable = measurable.measure(Constraints.fixed(bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1)))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .then(if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier)
                .clearAndSetSemantics {
                    contentDescription = label
                    selected = isSelected
                    if (!interactive) return@clearAndSetSemantics
                    onClick { onIntent(VideoEditorIntent.SelectItem(overlay.itemId)); true }
                    customActions = listOf(
                        CustomAccessibilityAction(positionLabel) { openPanel(overlay.itemId, OverlayPanel.POSITION, onIntent) },
                        CustomAccessibilityAction(opacityLabel) { openPanel(overlay.itemId, OverlayPanel.OPACITY, onIntent) },
                        CustomAccessibilityAction(orderLabel) { openPanel(overlay.itemId, OverlayPanel.ORDER, onIntent) },
                        CustomAccessibilityAction(deleteLabel) { selectThen(overlay.itemId, VideoEditorIntent.DeleteSelected, onIntent) },
                        CustomAccessibilityAction(splitLabel) { selectThen(overlay.itemId, VideoEditorIntent.SplitSelected, onIntent) },
                    )
                },
        )
    }
}

// The bounds of the turned box, for a screen-reader node.
private fun nodeBounds(overlay: OverlayUi, frame: app.memix.core.ui.layers.CanvasFrameRect, canvasWidthPx: Int): androidx.compose.ui.geometry.Rect {
    val box = overlay.boxIn(frame, canvasWidthPx)
    val corners = listOf(box.corner(-1, -1, 0f), box.corner(1, -1, 0f), box.corner(1, 1, 0f), box.corner(-1, 1, 0f))
    val left = corners.minOf { it.x }
    val top = corners.minOf { it.y }
    return androidx.compose.ui.geometry.Rect(left, top, max(corners.maxOf { it.x }, left + 1f), max(corners.maxOf { it.y }, top + 1f))
}

private fun openPanel(itemId: String, panel: OverlayPanel, onIntent: (VideoEditorIntent) -> Unit): Boolean =
    selectThen(itemId, VideoEditorIntent.OpenOverlayPanel(panel), onIntent)

private fun selectThen(itemId: String, then: VideoEditorIntent, onIntent: (VideoEditorIntent) -> Unit): Boolean {
    onIntent(VideoEditorIntent.SelectItem(itemId))
    onIntent(then)
    return true
}

/** A moving playhead asks for no pictures; this long after it rests, the shown overlays' frames are asked for. */
private const val PLAYHEAD_REST_MS = 150L

/** The smallest long side a stand-in picture is read at. */
private const val MIN_FRAME_SIDE_PX = 64
