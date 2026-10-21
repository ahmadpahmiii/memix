package app.memix.feature.videoeditor.captions

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
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
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.Transform
import app.memix.feature.videoeditor.CaptionUi
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/** What a gesture on a caption is doing (spec P1-10 → On the preview). */
internal enum class CaptionGestureKind {
    /** One finger on a caption: it follows the finger. */
    MOVE,

    /** The handle: the distance from the center sets the size, the angle the turn. */
    HANDLE,

    /** Two fingers anywhere on the stage, with a caption selected: size, turn and place together. */
    PINCH,
}

/**
 * A caption under a finger: the one moving ([itemId]), how ([kind]), where it is now ([transform]) and which snaps hold.
 * Read in draw and placement code, so a gesture frame redraws without recomposing the screen. Main thread only.
 */
@Stable
internal class CaptionGesture(val itemId: String, val caption: CaptionUi) {
    var kind by mutableStateOf(CaptionGestureKind.MOVE)
    var transform by mutableStateOf(caption.transform)
    var snappedCenterX by mutableStateOf(false)
    var snappedCenterY by mutableStateOf(false)
    var cancelled = false

    // The unsnapped values the fingers have reached; snapping only changes what's shown.
    var rawCenter = Offset(caption.transform.centerX, caption.transform.centerY)
    var rawScale = caption.transform.scale
    var rawRotation = caption.transform.rotationDegrees
    var snappedScale = false
    var snappedRotation = false
}

/** The caption layer's live state: the canvas frame on the stage and the gesture in progress, if any. */
@Stable
internal class CaptionLayerState {
    var frame by mutableStateOf(CanvasFrameRect.Empty)
    var stageWidth by mutableStateOf(0f)
    var stageHeight by mutableStateOf(0f)
    var gesture by mutableStateOf<CaptionGesture?>(null)

    /** The stage's height before typing began; typing keeps the frame inside it (spec P1-10 → Typing). */
    var normalStageHeight = 0f
}

/** Sizes the gestures need, in pixels. */
internal class CaptionTouchSizes(
    /** The handle's target and a caption's smallest target: `touch-target`. */
    val targetPx: Float,
    /** How far outside a caption's block its frame sits: `space-1`. */
    val frameOutsetPx: Float,
    /** A center snaps to the frame's middle lines within this: `space-2`. */
    val snapPx: Float,
)

/**
 * Taps, drags, pinches and the handle on the captions shown at the playhead (spec P1-10 → On the preview). Touches
 * that hit no caption, and no two-finger gesture with a caption selected, are left for the stage, whose tap plays or
 * pauses. [visible] is the captions shown now in draw order (the last on top); [selectedId] the selection.
 */
internal suspend fun PointerInputScope.detectCaptionGestures(
    state: CaptionLayerState,
    visible: () -> List<CaptionUi>,
    selectedId: () -> String?,
    canvasWidthPx: () -> Int,
    sizes: CaptionTouchSizes,
    haptics: () -> HapticFeedback?,
    onIntent: (VideoEditorIntent) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val frame = state.frame
    if (frame.isEmpty) return@awaitEachGesture
    val captions = visible()
    val selected = captions.lastOrNull { it.itemId == selectedId() }
    val boxOf = { caption: CaptionUi -> CaptionBox.of(caption.block, caption.transform, frame, canvasWidthPx()) }
    val onHandle = selected?.let { handleCenterOf(boxOf(it), size.width.toFloat(), size.height.toFloat(), sizes.frameOutsetPx) }
        ?.let { (down.position - it).getDistance() <= sizes.targetPx / 2 } == true
    val hit = if (onHandle) selected else captions.lastOrNull { boxOf(it).contains(down.position, sizes.frameOutsetPx, sizes.targetPx / 2) }
    if (hit == null) {
        // Not on a caption: a second finger with a caption selected starts a pinch; otherwise the stage has the touch.
        if (selected == null || !awaitSecondFinger()) return@awaitEachGesture
        runGesture(CaptionGesture(selected.itemId, selected).apply { kind = CaptionGestureKind.PINCH }, state, frame, sizes, haptics, onIntent)
        return@awaitEachGesture
    }
    down.consume()
    val gesture = CaptionGesture(hit.itemId, hit).apply { kind = if (onHandle) CaptionGestureKind.HANDLE else CaptionGestureKind.MOVE }
    if (!onHandle && !awaitDragOrTap(down.id)) {
        onIntent(VideoEditorIntent.CaptionTapped(hit.itemId))
        return@awaitEachGesture
    }
    runGesture(gesture, state, frame, sizes, haptics, onIntent)
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

// True when the finger on a caption moves past the touch slop (or a second finger lands): a drag; false on a tap.
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
    gesture: CaptionGesture,
    state: CaptionLayerState,
    frame: CanvasFrameRect,
    sizes: CaptionTouchSizes,
    haptics: () -> HapticFeedback?,
    onIntent: (VideoEditorIntent) -> Unit,
) {
    state.gesture = gesture
    onIntent(VideoEditorIntent.CaptionGestureStarted(gesture.itemId))
    val startCenter = Offset(frame.left + gesture.rawCenter.x * frame.width, frame.top + gesture.rawCenter.y * frame.height)
    var handleStart: Offset? = null
    while (true) {
        val event = awaitPointerEvent()
        if (gesture.cancelled) return
        val pressed = event.changes.filter { it.pressed }
        if (pressed.isEmpty()) break
        if (pressed.size >= 2) gesture.kind = CaptionGestureKind.PINCH
        when (gesture.kind) {
            CaptionGestureKind.MOVE -> moveBy(gesture, pressed.first().let { it.position - it.previousPosition }, frame)
            CaptionGestureKind.PINCH -> pinch(gesture, event, frame)
            CaptionGestureKind.HANDLE -> {
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
    onIntent(VideoEditorIntent.CaptionGestureEnded(gesture.itemId, gesture.transform))
}

private fun moveBy(gesture: CaptionGesture, delta: Offset, frame: CanvasFrameRect) {
    gesture.rawCenter = Offset(
        (gesture.rawCenter.x + delta.x / frame.width).coerceIn(0f, 1f),
        (gesture.rawCenter.y + delta.y / frame.height).coerceIn(0f, 1f),
    )
}

private fun pinch(gesture: CaptionGesture, event: PointerEvent, frame: CanvasFrameRect) {
    gesture.rawScale = (gesture.rawScale * event.calculateZoom()).coerceIn(CaptionCatalog.MIN_SCALE, CaptionCatalog.MAX_SCALE)
    gesture.rawRotation += event.calculateRotation()
    moveBy(gesture, event.calculatePan(), frame)
}

// The handle: the size follows the finger's distance from the center, the turn its angle, both from where it began.
private fun turnByHandle(gesture: CaptionGesture, center: Offset, from: Offset, finger: Offset) {
    val start = from - center
    val now = finger - center
    if (start.getDistance() <= 0f) return
    gesture.rawScale = (gesture.caption.transform.scale * now.getDistance() / start.getDistance())
        .coerceIn(CaptionCatalog.MIN_SCALE, CaptionCatalog.MAX_SCALE)
    val turned = atan2(now.y, now.x) - atan2(start.y, start.x)
    gesture.rawRotation = gesture.caption.transform.rotationDegrees + turned.toDegrees()
}

/**
 * Shows the raw values with their snaps (spec P1-10 → Snapping): the center to the frame's middle lines within
 * `space-2`, the turn to a quarter within 4°, the size to 100% within 3%. True when a snap newly engaged (one tick).
 */
private fun applySnaps(gesture: CaptionGesture, frame: CanvasFrameRect, snapPx: Float): Boolean {
    val snapX = abs(gesture.rawCenter.x - MIDDLE) * frame.width <= snapPx
    val snapY = abs(gesture.rawCenter.y - MIDDLE) * frame.height <= snapPx
    val canResize = gesture.kind != CaptionGestureKind.MOVE
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
internal fun handleCenterOf(box: CaptionBox, stageWidth: Float, stageHeight: Float, outset: Float): Offset {
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
