package app.memix.feature.photoeditor

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.OptionTileLayout
import app.memix.core.designsystem.component.Swatch
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.BubblePoint
import app.memix.core.model.project.BubbleShape
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.ui.layers.LayerStageState
import app.memix.core.ui.panels.ChoiceRow
import app.memix.core.ui.panels.PaletteColorNames
import app.memix.core.ui.panels.PositionStep
import app.memix.core.ui.panels.RepeatingButton
import app.memix.core.ui.panels.StepperRow
import app.memix.core.ui.panels.keptInViewWhen
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.bubble_fill
import memix.feature.photo_editor.generated.resources.bubble_round
import memix.feature.photo_editor.generated.resources.bubble_shape
import memix.feature.photo_editor.generated.resources.bubble_speech
import memix.feature.photo_editor.generated.resources.bubble_thought
import memix.feature.photo_editor.generated.resources.position_tail
import memix.feature.photo_editor.generated.resources.position_tail_down
import memix.feature.photo_editor.generated.resources.position_tail_left
import memix.feature.photo_editor.generated.resources.position_tail_reset
import memix.feature.photo_editor.generated.resources.position_tail_right
import memix.feature.photo_editor.generated.resources.position_tail_up
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * A speech bubble's editor pieces (spec docs/ux/specs/P2-02-meme-formats.md → Bubbles): the Style panel's Shape and Fill rows, the
 * Position panel's Tail row and the tail handle on the stage.
 */

private fun BubbleShape.label(): StringResource = when (this) {
    BubbleShape.SPEECH -> Res.string.bubble_speech
    BubbleShape.ROUND -> Res.string.bubble_round
    BubbleShape.THOUGHT -> Res.string.bubble_thought
}

/** The Style panel's first row: the three shapes as tiles, drawn from data. */
@Composable
internal fun BubbleShapeRow(selected: BubbleShape, onPick: (BubbleShape) -> Unit) {
    ChoiceRow(stringResource(Res.string.bubble_shape)) {
        BubbleShape.entries.forEach { shape ->
            OptionTile(
                stringResource(shape.label()),
                selected = shape == selected,
                onClick = { onPick(shape) },
                layout = OptionTileLayout.STACKED,
                modifier = Modifier.keptInViewWhen(shape == selected),
            ) { tint -> BubbleShapePicture(shape, tint) }
        }
    }
}

/** The Style panel's last row: the bubble's fill, the ten palette colors. */
@Composable
internal fun BubbleFillRow(selected: ArgbColor, onPick: (Long) -> Unit) {
    ChoiceRow(stringResource(Res.string.bubble_fill)) {
        CaptionCatalog.palette.forEachIndexed { index, color ->
            Swatch(Color(color.argb.toInt()), stringResource(PaletteColorNames[index]), selected = selected == color, onClick = { onPick(color.argb) })
        }
    }
}

@Composable
private fun BubbleShapePicture(shape: BubbleShape, color: Color) {
    Box(Modifier.size(MemixSize.iconSmall * 1.5f).drawBehind { drawBubbleShape(shape, color) })
}

// In the 24-unit box of the icon grammar: a solid body with a tail at its lower left; Thought is an ellipse with three dots.
private fun DrawScope.drawBubbleShape(shape: BubbleShape, color: Color) {
    val unit = size.minDimension / BOX_UNITS
    fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
    when (shape) {
        BubbleShape.SPEECH -> {
            drawRoundRect(color, point(2f, 3f), Size(20f * unit, 13f * unit), CornerRadius(4f * unit))
            drawPath(Path().apply { moveTo(5f * unit, 15f * unit); lineTo(5f * unit, 21f * unit); lineTo(11f * unit, 15f * unit); close() }, color)
        }
        BubbleShape.ROUND -> {
            drawOval(color, point(2f, 3f), Size(20f * unit, 13f * unit))
            drawPath(Path().apply { moveTo(6f * unit, 14f * unit); lineTo(5f * unit, 21f * unit); lineTo(11f * unit, 15f * unit); close() }, color)
        }
        BubbleShape.THOUGHT -> {
            drawOval(color, point(2f, 2f), Size(20f * unit, 12f * unit))
            drawCircle(color, 2.4f * unit, point(7f, 17.5f))
            drawCircle(color, 1.6f * unit, point(5f, 20.5f))
            drawCircle(color, 1f * unit, point(3.5f, 22.5f))
        }
    }
}

private const val BOX_UNITS = 24f

/** The Position panel's Tail row (four arrows, held repeats) and its Reset tail button. */
@Composable
internal fun TailRows(layerId: String, onIntent: (PhotoEditorIntent) -> Unit) {
    StepperRow(stringResource(Res.string.position_tail), spokenState = null) {
        TailButton(layerId, PositionStep.MOVE_LEFT, Res.string.position_tail_left, ARROW_LEFT, onIntent)
        TailButton(layerId, PositionStep.MOVE_UP, Res.string.position_tail_up, ARROW_UP, onIntent)
        TailButton(layerId, PositionStep.MOVE_DOWN, Res.string.position_tail_down, ARROW_DOWN, onIntent)
        TailButton(layerId, PositionStep.MOVE_RIGHT, Res.string.position_tail_right, ARROW_RIGHT, onIntent)
    }
    Button(stringResource(Res.string.position_tail_reset), { onIntent(PhotoEditorIntent.ResetTail(layerId)) })
}

@Composable
private fun TailButton(layerId: String, step: PositionStep, label: StringResource, turnDegrees: Float, onIntent: (PhotoEditorIntent) -> Unit) {
    RepeatingButton(
        MemixIcons.Arrow,
        stringResource(label),
        onHeld = { count -> onIntent(PhotoEditorIntent.TailStepHeld(layerId, step, count)) },
        onStep = { count -> onIntent(PhotoEditorIntent.TailStepped(layerId, step, count)) },
        turnDegrees = turnDegrees,
    )
}

private const val ARROW_RIGHT = 0f
private const val ARROW_DOWN = 90f
private const val ARROW_LEFT = 180f
private const val ARROW_UP = 270f

/**
 * The tail handle (spec → Bubbles): a `canvas-handle-size` white circle with the `position` icon at the tail's tip, a 48 dp target
 * that stays that size at any bubble scale; a drag moves the tip 1:1 and commits on release (one "Tail" step). It is above the
 * scale-and-rotate handle where they overlap. Drag-only, so hidden from accessibility: the Position panel's Tail row is the route.
 */
@Composable
internal fun BoxScope.TailHandle(layer: SceneLayerUi, stage: LayerStageState, live: PhotoLive, canvasWidthPx: Int, onIntent: (PhotoEditorIntent) -> Unit) {
    val bubble = layer.bubble ?: return
    val density = LocalDensity.current
    val targetPx = with(density) { MemixSize.touchTarget.toPx() }
    val currentLayer by rememberUpdatedState(layer)
    val currentOnIntent by rememberUpdatedState(onIntent)
    Box(
        Modifier
            .zIndex(1f)
            .offset {
                val shownTail = live.tailOf(layer.id) ?: BubblePoint(bubble.tailX, bubble.tailY)
                val center = tipOnStage(layer, shownTail, stage, canvasWidthPx)
                IntOffset((center.x - targetPx / 2).roundToInt(), (center.y - targetPx / 2).roundToInt())
            }
            .size(MemixSize.touchTarget)
            .clearAndSetSemantics { }
            .pointerInput(layer.id) {
                var tail = BubblePoint(0f, 0f)
                detectDragGestures(
                    onDragStart = { tail = live.tailOf(currentLayer.id) ?: currentLayer.bubble?.let { BubblePoint(it.tailX, it.tailY) } ?: tail },
                    onDragEnd = {
                        live.settleTail(currentLayer.id, tail)
                        currentOnIntent(PhotoEditorIntent.TailMoved(currentLayer.id, tail.x, tail.y))
                    },
                    onDragCancel = { live.releaseTail() },
                ) { change, drag ->
                    change.consume()
                    val target = currentLayer
                    val body = target.bubble ?: return@detectDragGestures
                    val pixelScale = if (canvasWidthPx > 0) stage.frame.width / canvasWidthPx else 0f
                    if (pixelScale <= 0f || body.bodyWidthPx <= 0f || body.bodyHeightPx <= 0f) return@detectDragGestures
                    // The finger's move in the bubble's own axes (turned back, unscaled), then in body widths and heights.
                    val radians = -target.transform.rotationDegrees * (kotlin.math.PI.toFloat() / 180f)
                    val unscale = target.transform.scale * pixelScale
                    val localX = (drag.x * cos(radians) - drag.y * sin(radians)) / unscale
                    val localY = (drag.x * sin(radians) + drag.y * cos(radians)) / unscale
                    tail = app.memix.core.model.project.clampedTail(tail.x + localX / body.bodyWidthPx, tail.y + localY / body.bodyHeightPx)
                    live.setTail(target.id, tail)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(MemixSize.canvasHandleSize)
                .drawBehind {
                    drawCircle(MemixColors.onPrimary)
                    drawCircle(MemixColors.selection, size.minDimension / 2 - MemixStroke.strokeHairline.toPx())
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(MemixIcons.Position, contentDescription = null, tint = MemixColors.onPrimary, modifier = Modifier.size(MemixSize.iconSmall))
        }
    }
}

// Where the tail's tip is on the stage: the bubble's own axes, scaled, turned about the body's center.
private fun tipOnStage(layer: SceneLayerUi, tail: BubblePoint, stage: LayerStageState, canvasWidthPx: Int): Offset {
    val body = layer.bubble ?: return Offset.Zero
    val frame = stage.frame
    val pixelScale = if (canvasWidthPx > 0) frame.width / canvasWidthPx else 0f
    val radians = layer.transform.rotationDegrees * (kotlin.math.PI.toFloat() / 180f)
    val x = tail.x * body.bodyWidthPx * layer.transform.scale * pixelScale
    val y = tail.y * body.bodyHeightPx * layer.transform.scale * pixelScale
    val centerX = frame.left + layer.transform.centerX * frame.width
    val centerY = frame.top + layer.transform.centerY * frame.height
    return Offset(centerX + x * cos(radians) - y * sin(radians), centerY + x * sin(radians) + y * cos(radians))
}
