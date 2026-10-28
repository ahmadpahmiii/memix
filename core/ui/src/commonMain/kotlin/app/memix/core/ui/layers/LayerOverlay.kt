package app.memix.core.ui.layers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import memix.core.ui.generated.resources.Res
import memix.core.ui.generated.resources.position_rotation_value
import memix.core.ui.generated.resources.position_size_value
import memix.core.ui.generated.resources.text_transform_bubble
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/*
 * What every editor draws over its stage for the selected layer (spec P1-10 → The selected text on the preview, P2-01 →
 * Gestures): the two-tone frame, the scale-and-rotate handle, the snap guides and the value bubble. Each reads the
 * gesture's own transform while drawing or placing, so a gesture frame redraws without recomposing anything.
 */

/** The two-tone selection frame around [target], following its gesture. Fills the stage it is placed on. */
@Composable
fun BoxScope.LayerSelectionFrame(state: LayerStageState, target: GestureTarget, canvasWidthPx: Int) {
    val density = LocalDensity.current
    val outsetPx = with(density) { MemixSpacing.space1.toPx() }
    val selectionPx = with(density) { MemixStroke.strokeSelection.toPx() }
    val hairlinePx = with(density) { MemixStroke.strokeHairline.toPx() }
    Spacer(
        Modifier.matchParentSize().drawBehind {
            // Read here, so a gesture frame redraws the frame without recomposing.
            val transform = state.gesture?.takeIf { it.layerId == target.id }?.transform ?: target.transform
            drawTwoToneFrame(target.boxIn(state.frame, canvasWidthPx, transform), outsetPx, selectionPx, hairlinePx)
        },
    )
}

/**
 * The frame around a layer's box, [outset] outside it and turned with it: a white `stroke-selection` line with an
 * `on-primary` hairline just outside, so it reads over any content (spec P1-10 → The selected text on the preview).
 */
fun DrawScope.drawTwoToneFrame(box: LayerBox, outset: Float, selectionWidth: Float, hairlineWidth: Float) {
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
fun BoxScope.LayerTurnHandle(state: LayerStageState, target: GestureTarget, canvasWidthPx: Int) {
    val density = LocalDensity.current
    val outsetPx = with(density) { MemixSpacing.space1.toPx() }
    val handlePx = with(density) { MemixSize.canvasHandleSize.toPx() }
    Box(
        Modifier
            // Placed from the gesture's transform, so it follows the finger without recomposing.
            .offset {
                val transform = state.gesture?.takeIf { it.layerId == target.id }?.transform ?: target.transform
                val box = target.boxIn(state.frame, canvasWidthPx, transform)
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
fun BoxScope.LayerSnapGuides(state: LayerStageState) {
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
fun BoxScope.LayerTransformBubble(state: LayerStageState) {
    val values by remember(state) {
        derivedStateOf {
            val gesture = state.gesture?.takeIf { it.kind != LayerGestureKind.MOVE } ?: return@derivedStateOf null
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

private const val PERCENT = 100f
