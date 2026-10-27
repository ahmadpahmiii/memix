package app.memix.feature.videoeditor.captions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.IntOffset
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Transform
import app.memix.feature.videoeditor.CanvasUi
import app.memix.feature.videoeditor.CaptionUi
import app.memix.feature.videoeditor.EditorCaptionMeasure
import app.memix.feature.videoeditor.TextEntryUi
import app.memix.feature.videoeditor.TextPanel
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.max
import kotlin.math.roundToInt
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.a11y_text_on_canvas
import memix.feature.video_editor.generated.resources.position_rotation_value
import memix.feature.video_editor.generated.resources.position_size_value
import memix.feature.video_editor.generated.resources.text_transform_bubble
import memix.feature.video_editor.generated.resources.tool_position
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
    state: CaptionLayerState,
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
    when {
        entry != null && measure != null -> TypingStandIn(entry, entryText, measure, canvas, frame, standInSlot)
        gesture != null -> FrameSized(frame) {
            standInSlot(gesture.caption.text, gesture.caption.style, { gesture.transform }, canvas.widthPx, canvas.heightPx, Modifier.fillMaxSize())
        }
        standIn != null -> FrameSized(frame) {
            standInSlot(standIn.text, standIn.style, { standIn.transform }, canvas.widthPx, canvas.heightPx, Modifier.fillMaxSize())
        }
    }
    if (entry == null) {
        val selected = visible.lastOrNull { it.itemId == selectedItemId }
        val framed = selected?.let { caption -> if (standIn?.itemId == caption.itemId) standIn else caption }
        if (framed != null) SelectionFrame(state, framed, canvas)
        if (framed != null) TurnHandle(state, framed, canvas)
        SnapGuides(state)
        TransformBubble(state)
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
            val box = CaptionBox.of(block, entry.transform, frame, canvas.widthPx)
            // Empty: a touch-target square, so the landing spot shows before the first letter.
            val shown = if (text.isEmpty()) box.copy(halfWidth = targetPx / 2 - outsetPx, halfHeight = targetPx / 2 - outsetPx) else box
            drawTwoToneFrame(shown, outsetPx, selectionPx, hairlinePx)
        },
    )
}

@Composable
private fun BoxScope.SelectionFrame(state: CaptionLayerState, caption: CaptionUi, canvas: CanvasUi) {
    val density = LocalDensity.current
    val outsetPx = with(density) { MemixSpacing.space1.toPx() }
    val selectionPx = with(density) { MemixStroke.strokeSelection.toPx() }
    val hairlinePx = with(density) { MemixStroke.strokeHairline.toPx() }
    Spacer(
        Modifier.matchParentSize().drawBehind {
            // Read here, so a gesture frame redraws the frame without recomposing.
            val transform = state.gesture?.takeIf { it.itemId == caption.itemId }?.transform ?: caption.transform
            drawTwoToneFrame(CaptionBox.of(caption.block, transform, state.frame, canvas.widthPx), outsetPx, selectionPx, hairlinePx)
        },
    )
}

/**
 * The frame around a caption's block, [outset] outside it and turned with it: a white `stroke-selection` line with an
 * `on-primary` hairline just outside, so it reads over any video (spec → The selected text on the preview).
 */
private fun DrawScope.drawTwoToneFrame(box: CaptionBox, outset: Float, selectionWidth: Float, hairlineWidth: Float) {
    fun outline(extra: Float) = Path().apply {
        val corners = listOf(box.corner(-1, -1, extra), box.corner(1, -1, extra), box.corner(1, 1, extra), box.corner(-1, 1, extra))
        moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(outline(outset + selectionWidth + hairlineWidth / 2), MemixColors.onPrimary, style = Stroke(hairlineWidth))
    drawPath(outline(outset + selectionWidth / 2), MemixColors.selection, style = Stroke(selectionWidth))
}

// The scale-and-rotate handle: drag-only, so it's hidden from accessibility services; the Position panel does the same.
@Composable
private fun BoxScope.TurnHandle(state: CaptionLayerState, caption: CaptionUi, canvas: CanvasUi) {
    val density = LocalDensity.current
    val outsetPx = with(density) { MemixSpacing.space1.toPx() }
    val handlePx = with(density) { MemixSize.canvasHandleSize.toPx() }
    Box(
        Modifier
            // Placed from the gesture's transform, so it follows the finger without recomposing.
            .offset {
                val transform = state.gesture?.takeIf { it.itemId == caption.itemId }?.transform ?: caption.transform
                val box = CaptionBox.of(caption.block, transform, state.frame, canvas.widthPx)
                val center = handleCenterOf(box, state.stageWidth, state.stageHeight, outsetPx)
                IntOffset((center.x - handlePx / 2).roundToInt(), (center.y - handlePx / 2).roundToInt())
            }
            .size(MemixSize.canvasHandleSize)
            .clearAndSetSemantics { }
            .drawBehind {
                drawCircle(MemixColors.onPrimary)
                drawCircle(MemixColors.selection, size.minDimension / 2 - MemixStroke.strokeHairline.toPx())
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(MemixIcons.ScaleRotate, contentDescription = null, tint = MemixColors.onPrimary, modifier = Modifier.size(MemixSize.iconSmall))
    }
}

// Full-height and full-width lines through the canvas middle while a snap holds, two-tone like the frame.
@Composable
private fun BoxScope.SnapGuides(state: CaptionLayerState) {
    Spacer(
        Modifier.matchParentSize().drawBehind {
            val gesture = state.gesture ?: return@drawBehind
            val frame = state.frame
            val line = MemixStroke.strokeHairline.toPx()
            if (gesture.snappedCenterX) {
                val x = frame.left + frame.width / 2
                drawLine(MemixColors.selection, Offset(x - line / 2, frame.top), Offset(x - line / 2, frame.top + frame.height), line)
                drawLine(MemixColors.onPrimary, Offset(x + line / 2, frame.top), Offset(x + line / 2, frame.top + frame.height), line)
            }
            if (gesture.snappedCenterY) {
                val y = frame.top + frame.height / 2
                drawLine(MemixColors.selection, Offset(frame.left, y - line / 2), Offset(frame.left + frame.width, y - line / 2), line)
                drawLine(MemixColors.onPrimary, Offset(frame.left, y + line / 2), Offset(frame.left + frame.width, y + line / 2), line)
            }
        },
    )
}

// "120% · 15°" at the top of the stage while sizing or turning, where fingers don't cover it.
@Composable
private fun BoxScope.TransformBubble(state: CaptionLayerState) {
    val values by remember(state) {
        derivedStateOf {
            val gesture = state.gesture?.takeIf { it.kind != CaptionGestureKind.MOVE } ?: return@derivedStateOf null
            (gesture.transform.scale * PERCENT).roundToInt() to gesture.transform.rotationDegrees.roundToInt()
        }
    }
    val shown = values
    AnimatedVisibility(
        shown != null,
        Modifier.align(Alignment.TopCenter).padding(top = MemixSpacing.space2),
        enter = fadeIn(tween(MemixMotion.durationPress)),
        exit = fadeOut(tween(MemixMotion.durationPress)),
    ) {
        val (percent, degrees) = shown ?: (0 to 0)
        val text = stringResource(
            Res.string.text_transform_bubble,
            stringResource(Res.string.position_size_value, percent),
            stringResource(Res.string.position_rotation_value, degrees),
        )
        Text(
            text,
            MemixTheme.type.timecode,
            Modifier
                .clip(MemixShapes.radiusFull)
                .background(MemixColors.surfaceRaised)
                .padding(horizontal = MemixSpacing.space2, vertical = MemixSpacing.space1),
        )
    }
}

/**
 * One node per caption shown, over its turned block, top of the frame to bottom (spec P1-10 → Accessibility): "Text:
 * WHEN THE BUILD PASSES", selected when it is; double-tap selects it, or opens typing when it's selected; actions
 * Style and Position. No touch handling here: the stage's gestures do that.
 */
@Composable
private fun BoxScope.CaptionNodes(
    state: CaptionLayerState,
    visible: List<CaptionUi>,
    selectedItemId: String?,
    canvas: CanvasUi,
    selectedFocus: FocusRequester,
    interactive: Boolean,
    onIntent: (VideoEditorIntent) -> Unit,
) {
    val density = LocalDensity.current
    val styleLabel = stringResource(Res.string.tool_style)
    val positionLabel = stringResource(Res.string.tool_position)
    for (caption in visible.sortedBy { it.transform.centerY }) {
        val label = stringResource(Res.string.a11y_text_on_canvas, caption.text)
        val box = CaptionBox.of(caption.block, caption.transform, state.frame, canvas.widthPx)
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

private const val PERCENT = 100f
