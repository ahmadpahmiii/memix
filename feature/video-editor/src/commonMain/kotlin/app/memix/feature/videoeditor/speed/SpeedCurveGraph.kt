package app.memix.feature.videoeditor.speed

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.domain.project.snappedToNormal
import app.memix.core.model.project.SpeedCatalog
import app.memix.core.model.project.SpeedPoint
import app.memix.feature.videoeditor.SpeedCurveAction
import app.memix.feature.videoeditor.SpeedPanelUi
import app.memix.feature.videoeditor.lengthSecondsText
import app.memix.feature.videoeditor.speedText
import kotlin.math.abs
import kotlin.math.roundToInt
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.a11y_speed_point
import memix.feature.video_editor.generated.resources.edit_speed_curve
import memix.feature.video_editor.generated.resources.fx_speed_value
import memix.feature.video_editor.generated.resources.speed_length
import memix.feature.video_editor.generated.resources.speed_point_earlier
import memix.feature.video_editor.generated.resources.speed_point_faster
import memix.feature.video_editor.generated.resources.speed_point_later
import memix.feature.video_editor.generated.resources.speed_point_remove
import memix.feature.video_editor.generated.resources.speed_point_slower
import org.jetbrains.compose.resources.stringResource

/**
 * The speed curve of the Curve tab (spec docs/ux/specs/P4-09-speed.md → Curve): 96 dp tall, x is the place in the clip's picture (0 to 100%), y is the speed on a
 * log scale (0.1x at the bottom, 1x on a hairline, 10x at the top). Tap on empty graph adds a point, tap on a point selects it, drag moves a point (LongPress haptic at
 * pick-up, SegmentTick at the 1x snap); the picture follows when the finger lifts. Points are also nodes for a screen reader, with the same moves as actions, and the
 * panel's steppers do everything without dragging. The line is decorative. The curve is drawn from [SpeedPanelUi.curve]; it is only data here.
 */
@Composable
internal fun SpeedCurveGraph(panel: SpeedPanelUi, onAction: (SpeedCurveAction) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentPanel by rememberUpdatedState(panel)
    val currentAction by rememberUpdatedState(onAction)
    val groupName = stringResource(Res.string.edit_speed_curve)
    val lengthText = stringResource(Res.string.speed_length, lengthSecondsText(panel.lengthUs))
    val labels = rememberAxisLabels()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1)) {
    // The length sits in its own right-aligned row above the graph, so nothing of the drawing can run under it at a large font.
    Text(lengthText, MemixTheme.type.timecode, Modifier.fillMaxWidth(), color = MemixColors.textSecondary, textAlign = TextAlign.End)
    Box(
        Modifier
            .fillMaxWidth()
            .height(GRAPH_HEIGHT)
            .clip(MemixShapes.radiusMd)
            .background(MemixColors.surfaceRaised)
            .semantics { contentDescription = groupName }
            .pointerInput(density) { graphGestures(density.run { GUTTER.toPx() }, density.run { EDGE.toPx() }, density.run { HIT_RADIUS.toPx() }, { currentPanel }, { currentAction(it) }, { haptics.performHapticFeedback(it) }) },
    ) {
        // The line is built once per curve, not on every frame of a drag that doesn't change it.
        Spacer(Modifier.fillMaxSize().drawWithCache {
            val gutter = GUTTER.toPx()
            val edge = EDGE.toPx()
            val line = linePath(panel.curve, Geometry(size.width, size.height, gutter, edge))
            onDrawBehind { drawGraph(panel, line, gutter, edge) }
        })
        // The axis labels are part of a drawing, so they stop growing at 130% of the font scale (the nodes carry the spoken values).
        CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, LABEL_FONT_SCALE_CAP))) {
            AxisLabel(labels.top, Alignment.TopStart)
            AxisLabel(labels.normal, Alignment.CenterStart)
            AxisLabel(labels.bottom, Alignment.BottomStart)
        }
        PointNodes(panel, onAction)
    }
    }
}

private class AxisLabels(val top: String, val normal: String, val bottom: String)

@Composable
private fun rememberAxisLabels(): AxisLabels {
    val top = stringResource(Res.string.fx_speed_value, speedText(SpeedCatalog.MAX_SPEED))
    val normal = stringResource(Res.string.fx_speed_value, speedText(SpeedCatalog.NORMAL))
    val bottom = stringResource(Res.string.fx_speed_value, speedText(SpeedCatalog.MIN_SPEED))
    return remember(top, normal, bottom) { AxisLabels(top, normal, bottom) }
}

@Composable
private fun BoxScope.AxisLabel(text: String, alignment: Alignment) {
    Text(text, MemixTheme.type.caption, Modifier.align(alignment).padding(horizontal = MemixSpacing.space1), color = MemixColors.textMuted)
}

// The points, drawn by the canvas, are also nodes here: a 48 dp square on each, named "Point 2 of 5, 50 percent, 0.15x", with the moves as actions (WCAG 2.5.1, 2.5.7).
@Composable
private fun PointNodes(panel: SpeedPanelUi, onAction: (SpeedCurveAction) -> Unit) {
    val earlier = stringResource(Res.string.speed_point_earlier)
    val later = stringResource(Res.string.speed_point_later)
    val faster = stringResource(Res.string.speed_point_faster)
    val slower = stringResource(Res.string.speed_point_slower)
    val remove = stringResource(Res.string.speed_point_remove)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val geometry = Geometry(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), with(LocalDensity.current) { GUTTER.toPx() }, with(LocalDensity.current) { EDGE.toPx() })
        panel.curve.forEachIndexed { index, point ->
            val spoken = stringResource(Res.string.a11y_speed_point, index + 1, panel.curve.size, (point.positionFraction * PERCENT).roundToInt(), speedText(point.speed))
            val half = with(LocalDensity.current) { MemixSize.touchTarget.toPx() / 2 }
            Box(
                Modifier
                    .offset { IntOffset((geometry.xOf(point.positionFraction) - half).roundToInt(), (geometry.yOf(point.speed) - half).roundToInt()) }
                    .size(MemixSize.touchTarget)
                    .semantics {
                        contentDescription = spoken
                        selected = index == panel.selectedPoint
                        onClick { onAction(SpeedCurveAction.SelectPoint(index)); true }
                        customActions = listOf(
                            CustomAccessibilityAction(earlier) { onAction(SpeedCurveAction.SelectPoint(index)); onAction(SpeedCurveAction.StepTime(-1, held = false)); true },
                            CustomAccessibilityAction(later) { onAction(SpeedCurveAction.SelectPoint(index)); onAction(SpeedCurveAction.StepTime(1, held = false)); true },
                            CustomAccessibilityAction(faster) { onAction(SpeedCurveAction.SelectPoint(index)); onAction(SpeedCurveAction.StepSpeed(1, held = false)); true },
                            CustomAccessibilityAction(slower) { onAction(SpeedCurveAction.SelectPoint(index)); onAction(SpeedCurveAction.StepSpeed(-1, held = false)); true },
                            CustomAccessibilityAction(remove) { onAction(SpeedCurveAction.SelectPoint(index)); onAction(SpeedCurveAction.RemovePoint); true },
                        )
                    },
            )
        }
    }
}

// Where the curve and its points sit in a graph [width] by [height] px: [gutter] px kept free at the left for the axis labels, [edge] px at the other three sides.
private class Geometry(val width: Float, val height: Float, private val gutter: Float, private val edge: Float) {
    private val plotWidth get() = (width - gutter - edge).coerceAtLeast(1f)
    private val plotHeight get() = (height - 2 * edge).coerceAtLeast(1f)

    fun xOf(fraction: Float): Float = gutter + fraction * plotWidth
    fun yOf(speed: Float): Float = edge + (1f - SpeedCatalog.positionOfSpeed(speed)) * plotHeight
    fun fractionAt(x: Float): Float = ((x - gutter) / plotWidth).coerceIn(0f, 1f)
    fun speedAt(y: Float): Float = SpeedCatalog.speedAtPosition(1f - ((y - edge) / plotHeight).coerceIn(0f, 1f))
}

private fun linePath(curve: List<SpeedPoint>, geometry: Geometry): Path {
    val line = Path()
    if (curve.size < 2) return line
    for (step in 0..LINE_SAMPLES) {
        val fraction = step / LINE_SAMPLES.toFloat()
        val point = Offset(geometry.xOf(fraction), geometry.yOf(SpeedCatalog.curveSpeedAt(curve, fraction)))
        if (step == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
    }
    return line
}

private fun DrawScope.drawGraph(panel: SpeedPanelUi, line: Path, gutter: Float, edge: Float) {
    val geometry = Geometry(size.width, size.height, gutter, edge)
    val normalY = geometry.yOf(SpeedCatalog.NORMAL)
    drawLine(MemixColors.hairline, Offset(0f, normalY), Offset(size.width, normalY), strokeWidth = MemixStroke.strokeHairline.toPx())
    if (panel.curve.size < 2) return
    drawPath(line, MemixColors.text, style = Stroke(MemixStroke.strokeSelection.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    panel.curve.forEachIndexed { index, point ->
        val selected = index == panel.selectedPoint
        drawCircle(
            if (selected) MemixColors.selection else MemixColors.textSecondary,
            radius = (if (selected) SELECTED_POINT else POINT).toPx() / 2,
            center = Offset(geometry.xOf(point.positionFraction), geometry.yOf(point.speed)),
        )
    }
}

// A gesture on the graph: touching near a point picks it (a drag moves it, a tap selects it); touching elsewhere and lifting without moving adds a point there.
private suspend fun PointerInputScope.graphGestures(
    gutter: Float,
    edge: Float,
    hitRadius: Float,
    panel: () -> SpeedPanelUi,
    act: (SpeedCurveAction) -> Unit,
    haptic: (HapticFeedbackType) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val geometry = Geometry(size.width.toFloat(), size.height.toFloat(), gutter, edge)
        val curve = panel().curve
        val index = nearestPoint(curve, geometry, down.position, hitRadius)
        val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
        if (slop == null) {
            // Lifted (or cancelled) without moving.
            if (index != null) act(SpeedCurveAction.SelectPoint(index)) else act(SpeedCurveAction.Tap(geometry.fractionAt(down.position.x)))
            return@awaitEachGesture
        }
        if (index == null) return@awaitEachGesture
        haptic(HapticFeedbackType.LongPress)
        // The point keeps the offset it was grabbed with: it moves by what the finger moves.
        val start = Offset(geometry.xOf(curve[index].positionFraction), geometry.yOf(curve[index].speed))
        var snapped = false
        drag(slop.id) { change ->
            change.consume()
            val position = start + (change.position - down.position)
            val speed = geometry.speedAt(position.y)
            val nowSnapped = snappedToNormal(speed) == SpeedCatalog.NORMAL
            if (nowSnapped && !snapped) haptic(HapticFeedbackType.SegmentTick)
            snapped = nowSnapped
            act(SpeedCurveAction.Drag(index, geometry.fractionAt(position.x), speed))
        }
        act(SpeedCurveAction.DragEnd)
    }
}

private fun nearestPoint(curve: List<SpeedPoint>, geometry: Geometry, at: Offset, hitRadius: Float): Int? =
    curve.indices
        .map { it to (Offset(geometry.xOf(curve[it].positionFraction), geometry.yOf(curve[it].speed)) - at) }
        .filter { (_, delta) -> abs(delta.x) <= hitRadius && abs(delta.y) <= hitRadius }
        .minByOrNull { (_, delta) -> delta.getDistanceSquared() }
        ?.first

private val GRAPH_HEIGHT: Dp = 96.dp
private val GUTTER: Dp = 32.dp
private val EDGE: Dp = 12.dp
private val POINT: Dp = 12.dp
private val SELECTED_POINT: Dp = 16.dp
private val HIT_RADIUS: Dp = MemixSize.touchTarget / 2
private const val LINE_SAMPLES = 96

/** The font scale the axis labels stop at (spec P4-09 amendment). */
private const val LABEL_FONT_SCALE_CAP = 1.3f
private const val PERCENT = 100f
