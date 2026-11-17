package app.memix.feature.videoeditor.masks

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.model.project.ClipMask
import app.memix.core.model.project.MaskCatalog
import app.memix.core.model.project.MaskShape
import app.memix.core.ui.layers.CanvasFrameRect
import app.memix.feature.videoeditor.CanvasUi
import app.memix.feature.videoeditor.MaskUi
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.atan2
import kotlin.math.hypot
import org.jetbrains.compose.resources.painterResource

/**
 * The mask on the stage while the Mask panel is open and a shape is picked (spec P4-03b → The mask on the stage): its two-tone frame, the
 * scale-and-rotate handle, the edge handles of a Rectangle or Circle, a Mirror's band lines, and the gestures: one finger inside the clip's
 * box moves the mask, a handle sizes and turns it, two fingers pinch, twist and pan it. A touch outside the box plays or pauses, as
 * everywhere. The fingers' mask is held here and drawn in the draw phase; the editor hears it as [VideoEditorIntent.MaskGestureMoved] and
 * the preview draws it at once, so a finger move never recomposes the screen. Drag-only, so hidden from accessibility: the panel's Position
 * tab is the route.
 */
@Composable
internal fun BoxScope.MaskStage(
    panel: MaskUi,
    canvas: CanvasUi,
    frame: CanvasFrameRect,
    playheadUs: () -> Long,
    canPlay: Boolean,
    onIntent: (VideoEditorIntent) -> Unit,
) {
    if (frame.isEmpty || panel.mask == null || panel.boxWidthPx <= 0f || panel.boxHeightPx <= 0f) return
    var live by remember(panel.itemId) { mutableStateOf<ClipMask?>(null) }
    var snaps by remember(panel.itemId) { mutableStateOf(MaskSnaps()) }
    val currentPanel by rememberUpdatedState(panel)
    val currentFrame by rememberUpdatedState(frame)
    val currentOnIntent by rememberUpdatedState(onIntent)
    val currentCanPlay by rememberUpdatedState(canPlay)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val slopPx = LocalViewConfiguration.current.touchSlop
    val targetRadiusPx = with(density) { (MemixSize.touchTarget / 2).toPx() }
    val snapPx = with(density) { MemixSpacing.space2.toPx() }
    val handle = painterResource(MemixIcons.ScaleRotate)
    val handleRadiusPx = with(density) { MemixSize.canvasHandleSize.toPx() / 2f }
    val arrowhead = remember { Path() }
    val cache = remember { GeometryCache() }
    // One geometry per draw or touch; a box that doesn't follow keyframes is the same one until the panel, the frame or the stage changes.
    fun geometry(stageWidth: Float, stageHeight: Float): MaskStageGeometry =
        cache.get(currentPanel, currentFrame, canvas.widthPx, canvas.heightPx, playheadUs(), stageWidth, stageHeight, handleRadiusPx)
    Box(
        Modifier
            .matchParentSize()
            .clearAndSetSemantics { }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = live ?: currentPanel.mask ?: return@awaitEachGesture
                    // The box is frozen for the gesture, so a keyed overlay moving under playback doesn't drift under the finger.
                    val frozen = geometry(size.width.toFloat(), size.height.toFloat())
                    var grab = frozen.grabAt(start, down.position, targetRadiusPx)
                    if (grab == MaskGrab.NONE) {
                        // Outside the box: a lone tap plays or pauses; a second finger turns it into a pinch (spec: anywhere on the stage).
                        when (waitForTapOrSecondFinger(down.position, slopPx)) {
                            Outside.TAP -> if (currentCanPlay) currentOnIntent(VideoEditorIntent.PlayPause)
                            Outside.PINCH -> grab = MaskGrab.MOVE
                            Outside.NOTHING -> Unit
                        }
                        if (grab == MaskGrab.NONE) return@awaitEachGesture
                    } else {
                        down.consume()
                    }
                    currentOnIntent(VideoEditorIntent.MaskGestureStarted)
                    val drag = MaskDrag(start, grab, down.position, frozen)
                    var sent = start
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            var count = 0
                            var first = Offset.Zero
                            var second = Offset.Zero
                            for (change in event.changes) {
                                change.consume()
                                if (!change.pressed) continue
                                if (count == 0) first = change.position else if (count == 1) second = change.position
                                count++
                            }
                            if (count == 0) break
                            val result = drag.update(count, first, second, snapPx)
                            if (result.snaps.engagedSince(snaps)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            snaps = result.snaps
                            live = result.mask
                            if (result.mask != sent) {
                                sent = result.mask
                                currentOnIntent(VideoEditorIntent.MaskGestureMoved(result.mask))
                            }
                        }
                    } finally {
                        val ended = live ?: start
                        live = null
                        snaps = MaskSnaps()
                        currentOnIntent(VideoEditorIntent.MaskGestureEnded(ended))
                    }
                }
            }
            .drawBehind {
                val mask = live ?: currentPanel.mask ?: return@drawBehind
                drawMask(geometry(size.width, size.height), mask, snaps, handle, size, arrowhead)
            },
    )
}

private enum class Outside { TAP, PINCH, NOTHING }

// A touch outside the box: a tap when the finger lifts without moving past the touch slop, a pinch when a second finger comes down, else nothing.
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForTapOrSecondFinger(from: Offset, slopPx: Float): Outside {
    var moved = false
    while (true) {
        val event: PointerEvent = awaitPointerEvent()
        var pressed = 0
        for (change in event.changes) if (change.pressed) pressed++
        if (pressed >= 2) {
            for (change in event.changes) change.consume()
            return Outside.PINCH
        }
        val change = event.changes.firstOrNull() ?: return Outside.NOTHING
        if ((change.position - from).getDistance() > slopPx) moved = true
        if (!change.pressed) return if (moved) Outside.NOTHING else Outside.TAP
    }
}

/** The geometry of the last draw or touch, kept while nothing it was made from changes (a keyframed box follows the playhead, so it is made again). */
private class GeometryCache {
    private var panel: MaskUi? = null
    private var frame: CanvasFrameRect? = null
    private var stageWidth = 0f
    private var stageHeight = 0f
    private var geometry: MaskStageGeometry? = null

    fun get(panel: MaskUi, frame: CanvasFrameRect, canvasWidthPx: Int, canvasHeightPx: Int, timeUs: Long, stageWidth: Float, stageHeight: Float, handleRadiusPx: Float): MaskStageGeometry {
        val kept = geometry
        val still = panel.overlay?.keyframes.isNullOrEmpty()
        if (kept != null && still && panel === this.panel && frame == this.frame && stageWidth == this.stageWidth && stageHeight == this.stageHeight) return kept
        this.panel = panel
        this.frame = frame
        this.stageWidth = stageWidth
        this.stageHeight = stageHeight
        return MaskStageGeometry(frame, canvasWidthPx, panel.boxAt(timeUs, canvasWidthPx, canvasHeightPx), stageWidth, stageHeight, handleRadiusPx).also { geometry = it }
    }
}

/** One gesture on the mask: the grab, the mask and fingers it began with, and the mask the fingers now make. */
private class MaskDrag(begun: ClipMask, grab: MaskGrab, firstFinger: Offset, private val geometry: MaskStageGeometry) {
    private var base = begun
    private var anchor = firstFinger
    private var activeGrab = grab
    private var pair: FingerPair? = null

    /** The mask for the [count] fingers down, the first at [first] and the second at [second] (stage pixels), snapped within [snapPx] stage pixels. */
    fun update(count: Int, first: Offset, second: Offset, snapPx: Float): Result {
        val box = geometry.boxPx
        val tolerance = if (geometry.scale > 0f) snapPx / geometry.scale else 0f
        if (count >= 2) return twoFingers(first, second, geometry, tolerance)
        pair?.let {
            // Back to one finger: the mask as it is now is the new start.
            pair = null
            base = it.latest
            anchor = first
            activeGrab = MaskGrab.MOVE
        }
        val delta = geometry.toLocal(first) - geometry.toLocal(anchor)
        return when (activeGrab) {
            MaskGrab.MOVE -> snap(MaskGestureMath.moved(base, box, delta), geometry, tolerance, place = true)
            MaskGrab.TURN -> {
                // From where the handle is drawn, which is kept on the stage.
                val from = geometry.toLocal(geometry.turnHandleOnStage(base))
                val turned = if (base.shape == MaskShape.RECTANGLE || base.shape == MaskShape.CIRCLE) {
                    MaskGestureMath.scaledAndTurned(base, box, from, from + delta)
                } else {
                    MaskGestureMath.turnedTo(base, box, from, from + delta)
                }
                snap(turned, geometry, tolerance, place = false)
            }
            MaskGrab.BAND_EDGE -> Result(MaskGestureMath.bandWidened(base, box, geometry.toLocal(first)), MaskSnaps())
            MaskGrab.EDGE_RIGHT, MaskGrab.EDGE_LEFT, MaskGrab.EDGE_BOTTOM, MaskGrab.EDGE_TOP -> {
                val edge = activeGrab.edge() ?: return Result(base, MaskSnaps())
                Result(MaskGestureMath.edgeResized(base, box, edge, geometry.edgeHandle(base, edge) + delta), MaskSnaps())
            }
            MaskGrab.NONE -> Result(base, MaskSnaps())
        }
    }

    private fun twoFingers(a: Offset, b: Offset, geometry: MaskStageGeometry, tolerance: Float): Result {
        val local = geometry.toLocal(a) to geometry.toLocal(b)
        val current = pair ?: FingerPair(local.first, local.second, base).also { pair = it }
        val distance = hypot(local.second.x - local.first.x, local.second.y - local.first.y)
        val factor = if (current.distance > 0f) distance / current.distance else 1f
        val angle = atan2(local.second.y - local.first.y, local.second.x - local.first.x)
        val turn = (angle - current.angle).toDegrees()
        val pan = Offset((local.first.x + local.second.x) / 2f, (local.first.y + local.second.y) / 2f) - current.middle
        val pinched = MaskGestureMath.pinched(current.base, geometry.boxPx, factor, turn, pan)
        return snap(pinched, geometry, tolerance, place = true).also { current.latest = it.mask }
    }

    private fun snap(mask: ClipMask, geometry: MaskStageGeometry, tolerance: Float, place: Boolean): Result {
        val (snapped, snaps) = MaskGestureMath.snapped(mask, geometry.boxPx, tolerance, snapsPlace = place)
        return Result(snapped, snaps)
    }

    class Result(val mask: ClipMask, val snaps: MaskSnaps)

    // The two fingers as they began: where they were, and the mask they began with.
    private class FingerPair(first: Offset, second: Offset, val base: ClipMask) {
        val distance = hypot(second.x - first.x, second.y - first.y)
        val angle = atan2(second.y - first.y, second.x - first.x)
        val middle = Offset((first.x + second.x) / 2f, (first.y + second.y) / 2f)
        var latest: ClipMask = base
    }
}

// ---- Drawing ----------------------------------------------------------------------------------------------------------------------

private fun DrawScope.drawMask(geometry: MaskStageGeometry, mask: ClipMask, snaps: MaskSnaps, handle: Painter, stage: Size, arrowhead: Path) {
    val selection = MemixStroke.strokeSelection.toPx()
    val hairline = MemixStroke.strokeHairline.toPx()
    val box = geometry.boxPx
    val centerStage = geometry.toStage(MaskGestureMath.centerOf(mask, box))
    val totalDegrees = geometry.box.rotationDegrees + mask.rotationDegrees
    val half = MaskGestureMath.halfExtentsOf(mask, box)
    val scale = geometry.scale
    clipRect(geometry.frame.left, geometry.frame.top, geometry.frame.left + geometry.frame.width, geometry.frame.top + geometry.frame.height) {
        withTransform({
            translate(centerStage.x, centerStage.y)
            rotate(totalDegrees, Offset.Zero)
        }) {
            when (mask.shape) {
                MaskShape.RECTANGLE -> {
                    val radius = mask.cornerRoundness * MaskCatalog.CORNER_RADIUS_SHARE * minOf(half.x, half.y) * scale
                    twoTone(selection, hairline) { color, width ->
                        drawRoundRect(color, Offset(-half.x * scale, -half.y * scale), Size(half.x * 2 * scale, half.y * 2 * scale), CornerRadius(radius), Stroke(width))
                    }
                }
                MaskShape.CIRCLE -> twoTone(selection, hairline) { color, width ->
                    drawOval(color, Offset(-half.x * scale, -half.y * scale), Size(half.x * 2 * scale, half.y * 2 * scale), style = Stroke(width))
                }
                MaskShape.LINEAR -> {
                    val reach = stage.width + stage.height
                    twoTone(selection, hairline) { color, width -> drawLine(color, Offset(-reach, 0f), Offset(reach, 0f), width) }
                    drawArrowhead(arrowhead, MemixSize.iconSmall.toPx(), selection, hairline)
                }
                MaskShape.MIRROR -> {
                    val reach = stage.width + stage.height
                    val band = half.x * scale
                    twoTone(selection, hairline) { color, width ->
                        drawLine(color, Offset(-reach, -band), Offset(reach, -band), width)
                        drawLine(color, Offset(-reach, band), Offset(reach, band), width)
                    }
                }
            }
        }
        drawSnapGuides(geometry, snaps, selection, hairline)
    }
    drawHandles(geometry, mask, handle)
}

// The arrow at a Linear mask's point, pointing to the side that stays (up in the mask's own axes).
private fun DrawScope.drawArrowhead(head: Path, size: Float, selection: Float, hairline: Float) {
    head.reset()
    head.moveTo(0f, -size)
    head.lineTo(size * ARROW_HALF_WIDTH, 0f)
    head.lineTo(-size * ARROW_HALF_WIDTH, 0f)
    head.close()
    drawPath(head, MemixColors.onPrimary, style = Stroke(selection + 2 * hairline))
    drawPath(head, MemixColors.selection)
}

// Lines through the box's middle while the mask's center holds there: two-tone like the frame.
private fun DrawScope.drawSnapGuides(geometry: MaskStageGeometry, snaps: MaskSnaps, selection: Float, hairline: Float) {
    val halfW = geometry.box.width / 2f
    val halfH = geometry.box.height / 2f
    if (snaps.centerX) twoTone(hairline, hairline) { color, width -> drawLine(color, geometry.toStage(Offset(0f, -halfH)), geometry.toStage(Offset(0f, halfH)), width) }
    if (snaps.centerY) twoTone(hairline, hairline) { color, width -> drawLine(color, geometry.toStage(Offset(-halfW, 0f)), geometry.toStage(Offset(halfW, 0f)), width) }
}

private fun DrawScope.drawHandles(geometry: MaskStageGeometry, mask: ClipMask, handle: Painter) {
    val hairline = MemixStroke.strokeHairline.toPx()
    val diameter = MemixSize.canvasHandleSize.toPx()
    // Kept whole on the stage (spec → Look), by the same function the touch uses.
    val turn = geometry.turnHandleOnStage(mask)
    if (mask.shape == MaskShape.RECTANGLE || mask.shape == MaskShape.CIRCLE) {
        MaskEdge.entries.forEach { edge -> drawEdgeHandle(geometry, mask, edge) }
    }
    drawCircle(MemixColors.onPrimary, diameter / 2, turn)
    drawCircle(MemixColors.selection, diameter / 2 - hairline, turn)
    val icon = MemixSize.iconSmall.toPx()
    translate(turn.x - icon / 2, turn.y - icon / 2) {
        with(handle) { draw(Size(icon, icon), colorFilter = ColorFilter.tint(MemixColors.onPrimary)) }
    }
}

// A `selection` bar across the middle of an edge with a hairline `on-primary` edge, turned with the mask (as the censor's, P2-05).
private fun DrawScope.drawEdgeHandle(geometry: MaskStageGeometry, mask: ClipMask, edge: MaskEdge) {
    val hairline = MemixStroke.strokeHairline.toPx()
    val length = MemixSize.edgeHandleLength.toPx()
    val thickness = MemixSize.edgeHandleThickness.toPx()
    val at = geometry.toStage(geometry.edgeHandle(mask, edge))
    val horizontal = edge.signY != 0
    val size = if (horizontal) Size(length, thickness) else Size(thickness, length)
    withTransform({
        translate(at.x, at.y)
        rotate(geometry.box.rotationDegrees + mask.rotationDegrees, Offset.Zero)
    }) {
        val corner = CornerRadius(1f)
        drawRoundRect(MemixColors.onPrimary, Offset(-size.width / 2 - hairline, -size.height / 2 - hairline), Size(size.width + 2 * hairline, size.height + 2 * hairline), corner)
        drawRoundRect(MemixColors.selection, Offset(-size.width / 2, -size.height / 2), size, corner)
    }
}

// The dark edge first, a hairline wider on each side, then the white line over it.
private inline fun twoTone(selection: Float, hairline: Float, draw: (Color, Float) -> Unit) {
    draw(MemixColors.onPrimary, selection + 2 * hairline)
    draw(MemixColors.selection, selection)
}

/** Half the arrowhead's base as a share of its height. */
private const val ARROW_HALF_WIDTH = 0.6f
