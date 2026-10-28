package app.memix.feature.videoeditor.captions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.IntOffset
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Transform
import app.memix.core.ui.layers.CanvasFrameRect
import app.memix.core.ui.layers.LayerBox
import app.memix.core.ui.layers.LayerSelectionFrame
import app.memix.core.ui.layers.LayerSnapGuides
import app.memix.core.ui.layers.LayerStageState
import app.memix.core.ui.layers.LayerTransformBubble
import app.memix.core.ui.layers.LayerTurnHandle
import app.memix.core.ui.layers.boxIn
import app.memix.core.ui.layers.drawTwoToneFrame
import app.memix.feature.videoeditor.CanvasUi
import app.memix.feature.videoeditor.CaptionUi
import app.memix.feature.videoeditor.EditorCaptionMeasure
import app.memix.feature.videoeditor.TextEntryUi
import app.memix.feature.videoeditor.TextPanel
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.max
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.tool_position
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.a11y_text_on_canvas
import memix.feature.video_editor.generated.resources.tool_style
import org.jetbrains.compose.resources.stringResource

/**
 * Draws one caption with the engine's renderer, sized to the canvas frame on screen (the composition root passes the
 * engine's stand-in; spec P1-10 → Live editing). [transform] is read while drawing.
 */
typealias CaptionStandInSlot = @Composable (
    text: String,
    style: CaptionStyle,
    transform: () -> Transform,
    canvasWidthPx: Int,
    canvasHeightPx: Int,
    modifier: Modifier,
) -> Unit

/**
 * What the editor draws over the preview for captions (spec P1-10 → The selected text on the preview): the caption
 * being typed or moved, or a committed one the preview hasn't shown yet, drawn by [standInSlot] while the preview
 * leaves it out; the two-tone selection frame and the scale-and-rotate handle; the snap guides and the value bubble
 * during a gesture; and one screen-reader node per caption shown at the playhead ([visible]). With [interactive] false
 * (the Canvas panel is open) the nodes are only heard: no double-tap and no Style or Position actions, so no caption
 * gets selected and no second panel opens behind the panel (P1-11 review M3).
 */
@Composable
internal fun BoxScope.CaptionLayer(
    state: LayerStageState,
    canvas: CanvasUi,
    visible: List<CaptionUi>,
    selectedItemId: String?,
    entry: TextEntryUi?,
    entryText: () -> String,
    standIn: CaptionUi?,
    measure: EditorCaptionMeasure?,
    standInSlot: CaptionStandInSlot,
    selectedFocus: FocusRequester,
    interactive: Boolean,
    onIntent: (VideoEditorIntent) -> Unit,
) {
    val frame = state.frame
    if (frame.isEmpty) return
    val gesture = state.gesture
    // The caption under the finger, as the preview showed it when the gesture began.
    val gestured = gesture?.target as? CaptionUi
    when {
        entry != null && measure != null -> TypingStandIn(entry, entryText, measure, canvas, frame, standInSlot)
        gesture != null && gestured != null -> FrameSized(frame) {
            standInSlot(gestured.text, gestured.style, { gesture.transform }, canvas.widthPx, canvas.heightPx, Modifier.fillMaxSize())
        }
        standIn != null -> FrameSized(frame) {
            standInSlot(standIn.text, standIn.style, { standIn.transform }, canvas.widthPx, canvas.heightPx, Modifier.fillMaxSize())
        }
    }
    if (entry == null) {
        val selected = visible.lastOrNull { it.itemId == selectedItemId }
        val framed = selected?.let { caption -> if (standIn?.itemId == caption.itemId) standIn else caption }
        if (framed != null) LayerSelectionFrame(state, framed, canvas.widthPx)
        if (framed != null) LayerTurnHandle(state, framed, canvas.widthPx)
        LayerSnapGuides(state)
        LayerTransformBubble(state)
        CaptionNodes(state, visible, selectedItemId, canvas, selectedFocus, interactive, onIntent)
    }
}

// The caption being typed: its words from the field, live (spec → Typing: each keystroke shows within one frame). An
// empty entry shows an empty `touch-target` frame at the landing spot.
@Composable
private fun BoxScope.TypingStandIn(
    entry: TextEntryUi,
    entryText: () -> String,
    measure: EditorCaptionMeasure,
    canvas: CanvasUi,
    frame: CanvasFrameRect,
    standInSlot: CaptionStandInSlot,
) {
    val text = entryText()
    val block = remember(text, entry.style, canvas) { measure.blockOf(text, entry.style, canvas.widthPx, canvas.heightPx) }
    FrameSized(frame) {
        if (text.isNotEmpty()) standInSlot(text, entry.style, { entry.transform }, canvas.widthPx, canvas.heightPx, Modifier.fillMaxSize())
    }
    val targetPx = with(LocalDensity.current) { MemixSize.touchTarget.toPx() }
    val outsetPx = with(LocalDensity.current) { MemixSpacing.space1.toPx() }
    val selectionPx = with(LocalDensity.current) { MemixStroke.strokeSelection.toPx() }
    val hairlinePx = with(LocalDensity.current) { MemixStroke.strokeHairline.toPx() }
    Spacer(
        Modifier.matchParentSize().drawBehind {
            val box = LayerBox.of(block.widthPx, block.heightPx, entry.transform, frame, canvas.widthPx)
            // Empty: a touch-target square, so the landing spot shows before the first letter.
            val shown = if (text.isEmpty()) box.copy(halfWidth = targetPx / 2 - outsetPx, halfHeight = targetPx / 2 - outsetPx) else box
            drawTwoToneFrame(shown, outsetPx, selectionPx, hairlinePx)
        },
    )
}

/**
 * One node per caption shown, over its turned block, top of the frame to bottom (spec P1-10 → Accessibility): "Text:
 * WHEN THE BUILD PASSES", selected when it is; double-tap selects it, or opens typing when it's selected; actions
 * Style and Position. No touch handling here: the stage's gestures do that.
 */
@Composable
private fun BoxScope.CaptionNodes(
    state: LayerStageState,
    visible: List<CaptionUi>,
    selectedItemId: String?,
    canvas: CanvasUi,
    selectedFocus: FocusRequester,
    interactive: Boolean,
    onIntent: (VideoEditorIntent) -> Unit,
) {
    val density = LocalDensity.current
    val styleLabel = stringResource(Res.string.tool_style)
    val positionLabel = stringResource(UiRes.string.tool_position)
    for (caption in visible.sortedBy { it.transform.centerY }) {
        val label = stringResource(Res.string.a11y_text_on_canvas, caption.text)
        val box = caption.boxIn(state.frame, canvas.widthPx)
        val corners = listOf(box.corner(-1, -1, 0f), box.corner(1, -1, 0f), box.corner(1, 1, 0f), box.corner(-1, 1, 0f))
        val left = corners.minOf { it.x }
        val top = corners.minOf { it.y }
        val right = max(corners.maxOf { it.x }, left + 1f)
        val bottom = max(corners.maxOf { it.y }, top + 1f)
        val isSelected = caption.itemId == selectedItemId
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { (right - left).toDp() }, with(density) { (bottom - top).toDp() })
                .then(if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier)
                .clearAndSetSemantics {
                    contentDescription = label
                    selected = isSelected
                    if (!interactive) return@clearAndSetSemantics
                    onClick { onIntent(VideoEditorIntent.CaptionTapped(caption.itemId)); true }
                    customActions = listOf(
                        CustomAccessibilityAction(styleLabel) { openPanel(caption.itemId, TextPanel.STYLE, onIntent) },
                        CustomAccessibilityAction(positionLabel) { openPanel(caption.itemId, TextPanel.POSITION, onIntent) },
                    )
                },
        )
    }
}

private fun openPanel(itemId: String, panel: TextPanel, onIntent: (VideoEditorIntent) -> Unit): Boolean {
    onIntent(VideoEditorIntent.SelectItem(itemId))
    onIntent(VideoEditorIntent.OpenTextPanel(panel))
    return true
}

// A box exactly over the canvas frame, for the stand-in.
@Composable
private fun FrameSized(frame: CanvasFrameRect, content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
            .size(with(density) { frame.width.toDp() }, with(density) { frame.height.toDp() }),
        content = content,
    )
}

