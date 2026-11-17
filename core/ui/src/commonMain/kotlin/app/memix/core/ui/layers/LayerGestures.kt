package app.memix.core.ui.layers

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import app.memix.core.model.project.Transform
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * What the gestures need to know about one layer (a caption on a video, any layer on a photo): its id, where it is,
 * the size of its own box at scale 1 in canvas pixels, how far it may scale and whether it is locked. Implemented by each
 * editor's own layer type, so the lists the editors already hold are passed as they are.
 */
@Immutable
interface GestureTarget {
    val id: String
    val transform: Transform

    /** The layer's own box at scale 1, in canvas pixels. */
    val boxWidthPx: Float
    val boxHeightPx: Float

    /**
     * Where the box's center is from the layer's own center, in the layer's own unturned axes at scale 1 (canvas pixels): zero for
     * every layer but a speech bubble, whose box covers its tail and so sits off its body's center.
     */
    val boxOffsetXPx: Float get() = 0f
    val boxOffsetYPx: Float get() = 0f

    /**
     * A point in the layer's own axes at scale 1 (canvas pixels from its center) the scale-and-rotate handle keeps clear of: a
     * bubble's tail tip, so the two handles never sit on each other. Null for every other layer.
     */
    val handleAvoidXPx: Float? get() = null
    val handleAvoidYPx: Float? get() = null

    /** The scales a pinch or the handle can reach. */
    val scaleRange: ClosedFloatingPointRange<Float>

    /** A locked layer can be tapped, but a drag, pinch or the handle's place only explains (spec P2-01 → Locked layers). */
    val locked: Boolean

    /**
     * Whether the layer's sides also snap flush to the canvas's edges within `space-2` while it moves or sizes (spec P4-01b →
     * Snapping: a corner picture-in-picture is the common layout). The centers' and the turn's snaps hold for every layer.
     */
    val snapsToEdges: Boolean get() = false

    /** Whether the scale-and-rotate handle is kept whole on the stage: its center never nearer to the stage's edge than half its size (spec P4-01b → Frame, handle). */
    val keepsHandleOnStage: Boolean get() = false
}

/** [this] layer's box on the stage frame [frame], for a canvas [canvasWidthPx] wide. */
fun GestureTarget.boxIn(frame: CanvasFrameRect, canvasWidthPx: Int, transform: Transform = this.transform): LayerBox =
    LayerBox.of(boxWidthPx, boxHeightPx, transform, frame, canvasWidthPx, boxOffsetXPx, boxOffsetYPx, handleAvoidXPx, handleAvoidYPx)

/** What a gesture on a layer is doing (spec P1-10 → On the preview). */
enum class LayerGestureKind {
    /** One finger on a layer: it follows the finger. */
    MOVE,

    /** The handle: the distance from the center sets the size, the angle the turn. */
    HANDLE,

    /** Two fingers anywhere on the stage, with a layer selected: size, turn and place together. */
    PINCH,
}

/**
 * A layer under a finger: the one moving ([target]), how ([kind]), where it is now ([transform]) and which snaps hold.
 * Read in draw and placement code, so a gesture frame redraws without recomposing the screen. Main thread only.
 */
@Stable
class LayerGesture(val target: GestureTarget) {
    val layerId: String get() = target.id
    var kind by mutableStateOf(LayerGestureKind.MOVE)
    var transform by mutableStateOf(target.transform)
    var snappedCenterX by mutableStateOf(false)
    var snappedCenterY by mutableStateOf(false)

    /** Which of the layer's sides are flush with the canvas's edges now (only for a target that [GestureTarget.snapsToEdges]). */
    var snappedLeft by mutableStateOf(false)
    var snappedRight by mutableStateOf(false)
    var snappedTop by mutableStateOf(false)
    var snappedBottom by mutableStateOf(false)
    var cancelled = false

    // The unsnapped values the fingers have reached; snapping only changes what's shown.
    internal var rawCenter = Offset(target.transform.centerX, target.transform.centerY)
    internal var rawScale = target.transform.scale
    internal var rawRotation = target.transform.rotationDegrees
    internal var snappedScale = false
    internal var snappedRotation = false
}

/** The stage's live state: the canvas frame on it and the gesture in progress, if any. */
@Stable
class LayerStageState {
    var frame by mutableStateOf(CanvasFrameRect.Empty)
    var stageWidth by mutableStateOf(0f)
    var stageHeight by mutableStateOf(0f)
    var gesture by mutableStateOf<LayerGesture?>(null)
}

/** Sizes the gestures need, in pixels. */
class LayerTouchSizes(
    /** The handle's target and a layer's smallest target: `touch-target`. */
    val targetPx: Float,
    /** How far outside a layer's box its frame sits: `space-1`. */
    val frameOutsetPx: Float,
    /** A center snaps to the frame's middle lines within this: `space-2`. */
    val snapPx: Float,
    /** Half the handle's drawn size: how far from the stage's edge a [GestureTarget.keepsHandleOnStage] layer's handle stays. */
    val handleHalfPx: Float = 0f,
)

/** What the stage's gestures report; each editor turns these into its own intents. Called on the main thread. */
interface LayerGestureEvents {
    /** A tap on layer [layerId] (a finger lifted without dragging). */
    fun onTap(layerId: String)

    /** A drag, pinch or the handle started on [layerId]: select it, leave it out of whatever else draws it. */
    fun onStarted(layerId: String)

    /** The fingers lifted: [layerId] ends at [transform], to commit as one undo step. */
    fun onEnded(layerId: String, transform: Transform)

    /** A drag, pinch or the handle's place was tried on a locked layer; says so, once per touch. */
    fun onLockedTouch(layerId: String)
}

/**
 * Taps, drags, pinches and the handle on the layers in [targets] (spec P1-10 → On the preview, P2-01 → Gestures). Touches
 * that hit no layer, and no two-finger gesture with a layer selected, are left for the stage: the video's tap plays or
 * pauses, the photo's clears the selection. [targets] are in draw order (the last on top); [selectedId] is the selection.
 */
suspend fun PointerInputScope.detectLayerGestures(
    state: LayerStageState,
    targets: () -> List<GestureTarget>,
    selectedId: () -> String?,
    canvasWidthPx: () -> Int,
    sizes: LayerTouchSizes,
    haptics: () -> HapticFeedback?,
    events: LayerGestureEvents,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val frame = state.frame
    if (frame.isEmpty) return@awaitEachGesture
    val layers = targets()
    val selected = layers.lastOrNull { it.id == selectedId() }
    val boxOf = { layer: GestureTarget -> layer.boxIn(frame, canvasWidthPx()) }
    val onHandle = selected?.let { handleCenterOf(boxOf(it), size.width.toFloat(), size.height.toFloat(), sizes.frameOutsetPx, if (it.keepsHandleOnStage) sizes.handleHalfPx else 0f) }
        ?.let { (down.position - it).getDistance() <= sizes.targetPx / 2 } == true
    val hit = if (onHandle) selected else layers.lastOrNull { boxOf(it).contains(down.position, sizes.frameOutsetPx, sizes.targetPx / 2) }
    if (hit == null) {
        // Not on a layer: a second finger with a layer selected starts a pinch; otherwise the stage has the touch.
        if (selected == null || !awaitSecondFinger()) return@awaitEachGesture
        if (selected.locked) return@awaitEachGesture explainLocked(selected, events)
        runGesture(LayerGesture(selected).apply { kind = LayerGestureKind.PINCH }, state, frame, canvasWidthPx(), sizes, haptics, events)
        return@awaitEachGesture
    }
    down.consume()
    if (hit.locked) {
        // A tap still selects it; a drag, a pinch or its handle's place move nothing and explain once.
        if (!onHandle && !awaitDragOrTap(down.id)) return@awaitEachGesture events.onTap(hit.id)
        return@awaitEachGesture explainLocked(hit, events)
    }
    val gesture = LayerGesture(hit).apply { kind = if (onHandle) LayerGestureKind.HANDLE else LayerGestureKind.MOVE }
    if (!onHandle && !awaitDragOrTap(down.id)) {
        events.onTap(hit.id)
        return@awaitEachGesture
    }
    runGesture(gesture, state, frame, canvasWidthPx(), sizes, haptics, events)
}

// Says "locked" once, then takes the rest of the touch, so nothing else on the stage reacts to it.
private suspend fun AwaitPointerEventScope.explainLocked(layer: GestureTarget, events: LayerGestureEvents) {
    events.onLockedTouch(layer.id)
    while (true) {
        val event = awaitPointerEvent()
        event.changes.forEach(PointerInputChange::consume)
        if (event.changes.none { it.pressed }) return
    }
}

// While one finger is down, leaves its events to the stage. True when a second finger lands, false when all lift.
private suspend fun AwaitPointerEventScope.awaitSecondFinger(): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.count { it.pressed }
        if (pressed >= 2) {
            event.changes.forEach(PointerInputChange::consume)
            return true
        }
        if (pressed == 0) return false
    }
}

// True when the finger on a layer moves past the touch slop (or a second finger lands): a drag; false on a tap.
private suspend fun AwaitPointerEventScope.awaitDragOrTap(pointerId: PointerId): Boolean {
    var moved = Offset.Zero
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return false
        if (event.changes.count { it.pressed } >= 2) return true
        if (!change.pressed) {
            change.consume()
            return false
        }
        moved += change.position - change.previousPosition
        change.consume()
        if (moved.getDistance() > viewConfiguration.touchSlop) return true
    }
}

private suspend fun AwaitPointerEventScope.runGesture(
    gesture: LayerGesture,
    state: LayerStageState,
    frame: CanvasFrameRect,
    canvasWidthPx: Int,
    sizes: LayerTouchSizes,
    haptics: () -> HapticFeedback?,
    events: LayerGestureEvents,
) {
    state.gesture = gesture
    events.onStarted(gesture.layerId)
    val startCenter = Offset(frame.left + gesture.rawCenter.x * frame.width, frame.top + gesture.rawCenter.y * frame.height)
    var handleStart: Offset? = null
    while (true) {
        val event = awaitPointerEvent()
        if (gesture.cancelled) return
        val pressed = event.changes.filter { it.pressed }
        if (pressed.isEmpty()) break
        if (pressed.size >= 2) gesture.kind = LayerGestureKind.PINCH
        when (gesture.kind) {
            LayerGestureKind.MOVE -> moveBy(gesture, pressed.first().let { it.position - it.previousPosition }, frame)
            LayerGestureKind.PINCH -> pinch(gesture, event, frame)
            LayerGestureKind.HANDLE -> {
                val finger = pressed.first().position
                val from = handleStart ?: finger.also { handleStart = it }
                turnByHandle(gesture, startCenter, from, finger)
            }
        }
        val engaged = applySnaps(gesture, frame, canvasWidthPx, sizes.snapPx)
        if (engaged) haptics()?.performHapticFeedback(HapticFeedbackType.SegmentTick)
        event.changes.forEach { if (it.positionChanged() || it.pressed) it.consume() }
    }
    state.gesture = null
    events.onEnded(gesture.layerId, gesture.transform)
}

private fun moveBy(gesture: LayerGesture, delta: Offset, frame: CanvasFrameRect) {
    gesture.rawCenter = Offset(
        (gesture.rawCenter.x + delta.x / frame.width).coerceIn(0f, 1f),
        (gesture.rawCenter.y + delta.y / frame.height).coerceIn(0f, 1f),
    )
}

private fun pinch(gesture: LayerGesture, event: PointerEvent, frame: CanvasFrameRect) {
    gesture.rawScale = (gesture.rawScale * event.calculateZoom()).coerceIn(gesture.target.scaleRange)
    gesture.rawRotation += event.calculateRotation()
    moveBy(gesture, event.calculatePan(), frame)
}

// The handle: the size follows the finger's distance from the center, the turn its angle, both from where it began.
private fun turnByHandle(gesture: LayerGesture, center: Offset, from: Offset, finger: Offset) {
    val start = from - center
    val now = finger - center
    if (start.getDistance() <= 0f) return
    gesture.rawScale = (gesture.target.transform.scale * now.getDistance() / start.getDistance()).coerceIn(gesture.target.scaleRange)
    val turned = atan2(now.y, now.x) - atan2(start.y, start.x)
    gesture.rawRotation = gesture.target.transform.rotationDegrees + turned.toDegrees()
}

/**
 * Shows the raw values with their snaps (spec P1-10 → Snapping): the center to the frame's middle lines within
 * `space-2`, the turn to a quarter within 4°, the size to 100% within 3%. True when a snap newly engaged (one tick).
 */
private fun applySnaps(gesture: LayerGesture, frame: CanvasFrameRect, canvasWidthPx: Int, snapPx: Float): Boolean {
    val snapX = abs(gesture.rawCenter.x - MIDDLE) * frame.width <= snapPx
    val snapY = abs(gesture.rawCenter.y - MIDDLE) * frame.height <= snapPx
    val edges = if (gesture.target.snapsToEdges) edgeSnapsOf(gesture, frame, canvasWidthPx, snapPx, snapX, snapY) else EdgeSnaps.None
    val canResize = gesture.kind != LayerGestureKind.MOVE
    val nearestQuarter = (gesture.rawRotation / QUARTER_TURN).roundToInt() * QUARTER_TURN
    val snapTurn = canResize && abs(gesture.rawRotation - nearestQuarter) <= ROTATION_SNAP_DEGREES
    val snapSize = canResize && abs(gesture.rawScale - 1f) <= SCALE_SNAP
    val engaged = (snapX && !gesture.snappedCenterX) || (snapY && !gesture.snappedCenterY) ||
        (snapTurn && !gesture.snappedRotation) || (snapSize && !gesture.snappedScale) ||
        (edges.left && !gesture.snappedLeft) || (edges.right && !gesture.snappedRight) ||
        (edges.top && !gesture.snappedTop) || (edges.bottom && !gesture.snappedBottom)
    gesture.snappedCenterX = snapX
    gesture.snappedCenterY = snapY
    gesture.snappedRotation = snapTurn
    gesture.snappedScale = snapSize
    gesture.snappedLeft = edges.left
    gesture.snappedRight = edges.right
    gesture.snappedTop = edges.top
    gesture.snappedBottom = edges.bottom
    gesture.transform = Transform(
        centerX = if (snapX) MIDDLE else edges.centerX ?: gesture.rawCenter.x,
        centerY = if (snapY) MIDDLE else edges.centerY ?: gesture.rawCenter.y,
        scale = if (snapSize) 1f else gesture.rawScale,
        rotationDegrees = if (snapTurn) nearestQuarter else gesture.rawRotation,
    )
    return engaged
}

/** Which sides of a layer are flush with the canvas's edges, and the center that makes them so (null on an axis where none is). */
private class EdgeSnaps(val left: Boolean, val right: Boolean, val top: Boolean, val bottom: Boolean, val centerX: Float?, val centerY: Float?) {
    companion object {
        val None = EdgeSnaps(left = false, right = false, top = false, bottom = false, centerX = null, centerY = null)
    }
}

// The layer's turned bounds against the frame's edges (spec P4-01b → Snapping): a side within `space-2` of its edge sits flush on it.
// A center snap on the same axis wins, so the middle lines keep their place.
private fun edgeSnapsOf(gesture: LayerGesture, frame: CanvasFrameRect, canvasWidthPx: Int, snapPx: Float, centerSnappedX: Boolean, centerSnappedY: Boolean): EdgeSnaps {
    val shown = Transform(gesture.rawCenter.x, gesture.rawCenter.y, gesture.rawScale, gesture.rawRotation)
    val box = gesture.target.boxIn(frame, canvasWidthPx, shown)
    val radians = box.rotationDegrees.toRadians()
    val halfAcross = abs(box.halfWidth * cos(radians)) + abs(box.halfHeight * sin(radians))
    val halfDown = abs(box.halfWidth * sin(radians)) + abs(box.halfHeight * cos(radians))
    val centerXpx = frame.left + gesture.rawCenter.x * frame.width
    val centerYpx = frame.top + gesture.rawCenter.y * frame.height
    val left = !centerSnappedX && abs(centerXpx - halfAcross - frame.left) <= snapPx
    val right = !centerSnappedX && !left && abs(centerXpx + halfAcross - (frame.left + frame.width)) <= snapPx
    val top = !centerSnappedY && abs(centerYpx - halfDown - frame.top) <= snapPx
    val bottom = !centerSnappedY && !top && abs(centerYpx + halfDown - (frame.top + frame.height)) <= snapPx
    return EdgeSnaps(
        left, right, top, bottom,
        centerX = when {
            left -> halfAcross / frame.width
            right -> 1f - halfAcross / frame.width
            else -> null
        },
        centerY = when {
            top -> halfDown / frame.height
            bottom -> 1f - halfDown / frame.height
            else -> null
        },
    )
}

/**
 * Where the scale-and-rotate handle sits: the frame's bottom-right corner, or, when that's off the stage
 * ([stageWidth] × [stageHeight]), the nearest corner that's on it.
 */
fun handleCenterOf(box: LayerBox, stageWidth: Float, stageHeight: Float, outset: Float, wholeInset: Float = 0f): Offset {
    val corners = listOf(box.corner(1, 1, outset), box.corner(1, -1, outset), box.corner(-1, 1, outset), box.corner(-1, -1, outset))
    val preferred = corners.first()
    // With [wholeInset] (half the handle's size) the handle lies whole on the stage, not only its center.
    fun onStage(point: Offset) = point.x in wholeInset..(stageWidth - wholeInset) && point.y in wholeInset..(stageHeight - wholeInset)
    // A point to keep clear of (a bubble's tail handle): the first corner, in the usual order, at least a handle's reach from it.
    val avoid = box.avoid
    fun clear(point: Offset) = avoid == null || (point - avoid).getDistance() >= HANDLE_CLEARANCE_PX
    if (onStage(preferred) && clear(preferred)) return preferred
    val nearest = corners.filter { onStage(it) && clear(it) }.minByOrNull { (it - preferred).getDistance() }
        ?: corners.filter(::onStage).minByOrNull { (it - preferred).getDistance() }
    if (nearest != null || wholeInset == 0f) return nearest ?: preferred
    // A full-bleed layer: no corner has room, so the handle is held inside the stage at the preferred corner's side.
    return Offset(preferred.x.coerceIn(wholeInset, stageWidth - wholeInset), preferred.y.coerceIn(wholeInset, stageHeight - wholeInset))
}

private const val MIDDLE = 0.5f
private const val QUARTER_TURN = 90f
private const val ROTATION_SNAP_DEGREES = 4f
private const val SCALE_SNAP = 0.03f

// Two 48 dp handles: their centers stay at least this far apart (about 40 dp at 3x), so neither covers the other.
private const val HANDLE_CLEARANCE_PX = 120f
