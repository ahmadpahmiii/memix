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

    /** The scales a pinch or the handle can reach. */
    val scaleRange: ClosedFloatingPointRange<Float>

    /** A locked layer can be tapped, but a drag, pinch or the handle's place only explains (spec P2-01 → Locked layers). */
    val locked: Boolean
}

/** [this] layer's box on the stage frame [frame], for a canvas [canvasWidthPx] wide. */
fun GestureTarget.boxIn(frame: CanvasFrameRect, canvasWidthPx: Int, transform: Transform = this.transform): LayerBox =
    LayerBox.of(boxWidthPx, boxHeightPx, transform, frame, canvasWidthPx)

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
    val onHandle = selected?.let { handleCenterOf(boxOf(it), size.width.toFloat(), size.height.toFloat(), sizes.frameOutsetPx) }
        ?.let { (down.position - it).getDistance() <= sizes.targetPx / 2 } == true
    val hit = if (onHandle) selected else layers.lastOrNull { boxOf(it).contains(down.position, sizes.frameOutsetPx, sizes.targetPx / 2) }
    if (hit == null) {
        // Not on a layer: a second finger with a layer selected starts a pinch; otherwise the stage has the touch.
        if (selected == null || !awaitSecondFinger()) return@awaitEachGesture
        if (selected.locked) return@awaitEachGesture explainLocked(selected, events)
        runGesture(LayerGesture(selected).apply { kind = LayerGestureKind.PINCH }, state, frame, sizes, haptics, events)
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
    runGesture(gesture, state, frame, sizes, haptics, events)
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
        val engaged = applySnaps(gesture, frame, sizes.snapPx)
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
private fun applySnaps(gesture: LayerGesture, frame: CanvasFrameRect, snapPx: Float): Boolean {
    val snapX = abs(gesture.rawCenter.x - MIDDLE) * frame.width <= snapPx
    val snapY = abs(gesture.rawCenter.y - MIDDLE) * frame.height <= snapPx
    val canResize = gesture.kind != LayerGestureKind.MOVE
    val nearestQuarter = (gesture.rawRotation / QUARTER_TURN).roundToInt() * QUARTER_TURN
    val snapTurn = canResize && abs(gesture.rawRotation - nearestQuarter) <= ROTATION_SNAP_DEGREES
    val snapSize = canResize && abs(gesture.rawScale - 1f) <= SCALE_SNAP
    val engaged = (snapX && !gesture.snappedCenterX) || (snapY && !gesture.snappedCenterY) ||
        (snapTurn && !gesture.snappedRotation) || (snapSize && !gesture.snappedScale)
    gesture.snappedCenterX = snapX
    gesture.snappedCenterY = snapY
    gesture.snappedRotation = snapTurn
    gesture.snappedScale = snapSize
    gesture.transform = Transform(
        centerX = if (snapX) MIDDLE else gesture.rawCenter.x,
        centerY = if (snapY) MIDDLE else gesture.rawCenter.y,
        scale = if (snapSize) 1f else gesture.rawScale,
        rotationDegrees = if (snapTurn) nearestQuarter else gesture.rawRotation,
    )
    return engaged
}

/**
 * Where the scale-and-rotate handle sits: the frame's bottom-right corner, or, when that's off the stage
 * ([stageWidth] × [stageHeight]), the nearest corner that's on it.
 */
fun handleCenterOf(box: LayerBox, stageWidth: Float, stageHeight: Float, outset: Float): Offset {
    val corners = listOf(box.corner(1, 1, outset), box.corner(1, -1, outset), box.corner(-1, 1, outset), box.corner(-1, -1, outset))
    val preferred = corners.first()
    fun onStage(point: Offset) = point.x in 0f..stageWidth && point.y in 0f..stageHeight
    if (onStage(preferred)) return preferred
    return corners.filter(::onStage).minByOrNull { (it - preferred).getDistance() } ?: preferred
}

private const val MIDDLE = 0.5f
private const val QUARTER_TURN = 90f
private const val ROTATION_SNAP_DEGREES = 4f
private const val SCALE_SNAP = 0.03f
