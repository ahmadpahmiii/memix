package app.memix.feature.videoeditor.timeline

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import app.memix.core.domain.project.TrimEdge
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/**
 * Sees every touch on the timeline before its scrolls and taps do (the Initial pass): [onTouchDown] when the first
 * finger lands (playback pauses, spec P1-05 → Scroll, zoom and scrub), [onTouchUp] when the last one lifts, and
 * [onPinch] with each step's change of scale once two fingers have pinched past the touch slop. From then until
 * every finger is up the touches are consumed, so neither scroll moves and no tap fires.
 */
internal suspend fun PointerInputScope.detectTouchAndPinch(
    onTouchDown: () -> Unit,
    onPinch: (zoomChange: Float) -> Unit,
    onTouchUp: () -> Unit,
) = awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    onTouchDown()
    var pinching = false
    var zoomBeforeSlop = 1f
    try {
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoomChange = event.calculateZoom()
                if (pinching) {
                    if (zoomChange != 1f) onPinch(zoomChange)
                } else {
                    zoomBeforeSlop *= zoomChange
                    pinching = abs(1f - zoomBeforeSlop) * event.calculateCentroidSize(useCurrent = false) > viewConfiguration.touchSlop
                    // The scale catches up with the fingers in one step.
                    if (pinching) onPinch(zoomBeforeSlop)
                }
            }
            if (pinching) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    } finally {
        onTouchUp()
    }
}

/**
 * Drags a trim handle (spec P1-06 → Trim): when a finger lands on a handle ([handleAt] says which), the drag starts at
 * once, with no long-press and no touch slop, and owns that finger until it lifts. It runs on the Initial pass and
 * consumes every change, so the timeline's scroll, its taps and the lanes' vertical scroll never see the gesture.
 * [onDrag] gets the finger's x on every move; [onEnd] runs when it lifts, [onCancel] when the gesture is taken away.
 */
internal suspend fun PointerInputScope.detectTrimDrag(
    handleAt: (Offset) -> TrimEdge?,
    onStart: (edge: TrimEdge, x: Float) -> Unit,
    onDrag: (x: Float) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val edge = handleAt(down.position) ?: return@awaitEachGesture
    down.consume()
    onStart(edge, down.position.x)
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val finger = event.changes.firstOrNull { it.id == down.id }
            // Read before consuming: a consumed change reports no movement.
            val moved = finger != null && finger.positionChange() != Offset.Zero
            event.changes.forEach { it.consume() }
            if (finger == null) break
            if (!finger.pressed) {
                onEnd()
                return@awaitEachGesture
            }
            if (moved) onDrag(finger.position.x)
        }
        onCancel()
    } catch (e: CancellationException) {
        onCancel()
        throw e
    }
}

/**
 * Reorder mode's touches (spec P1-06 → Reorder). Outside it, a long-press on a main clip ([mainClipAt]) lifts it
 * ([onLift]; false when there's nothing to reorder) and the same finger then drags it; until the press is long
 * enough nothing is consumed, so taps and scrolling work as usual. In reorder mode every touch is taken, but only the
 * strip's row ([isOnStrip]) responds: a press on the moving tile ([onGrab]) drags it and the release drops it
 * ([onDrop]); a tap on another tile goes to [onTap]. Anywhere else (the washed ruler and lanes) a touch does nothing
 * (spec P1-06 → Reorder: the washed areas don't respond).
 */
internal suspend fun PointerInputScope.detectReorderGestures(
    isReordering: () -> Boolean,
    isOnStrip: (Offset) -> Boolean,
    mainClipAt: (Offset) -> ItemUi?,
    onLift: (clip: ItemUi, x: Float) -> Boolean,
    onGrab: (x: Float) -> Boolean,
    onDrag: (x: Float) -> Unit,
    onDrop: () -> Unit,
    onTap: (x: Float) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    // A trim handle took this touch.
    if (down.isConsumed) return@awaitEachGesture
    if (isReordering()) {
        down.consume()
        when {
            // Taken, so nothing below scrolls or taps, and ignored: tapping the wash is how people try to get out.
            !isOnStrip(down.position) -> tapUntilUp(down, onTap = {})
            onGrab(down.position.x) -> dragUntilUp(down.id, onDrag, onDrop)
            else -> tapUntilUp(down, onTap)
        }
        return@awaitEachGesture
    }
    val clip = mainClipAt(down.position) ?: return@awaitEachGesture
    if (!heldStillForLongPress(down)) return@awaitEachGesture
    if (onLift(clip, down.position.x)) dragUntilUp(down.id, onDrag, onDrop)
}

/** True when the finger stays down, alone and within the touch slop, for the long-press time. Consumes nothing. */
private suspend fun AwaitPointerEventScope.heldStillForLongPress(down: PointerInputChange): Boolean {
    val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val finger = event.changes.firstOrNull { it.id == down.id }
            val movedAway = finger != null && (finger.position - down.position).getDistance() > viewConfiguration.touchSlop
            if (finger == null || !finger.pressed || finger.isConsumed || movedAway || event.changes.count { it.pressed } > 1) break
        }
    }
    return ended == null
}

/** Takes every change of the gesture; [onDrag] on each move of finger [pointerId], [onDrop] when it lifts or is lost. */
private suspend fun AwaitPointerEventScope.dragUntilUp(pointerId: PointerId, onDrag: (Float) -> Unit, onDrop: () -> Unit) {
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val finger = event.changes.firstOrNull { it.id == pointerId }
            val moved = finger != null && finger.positionChange() != Offset.Zero
            event.changes.forEach { it.consume() }
            if (finger == null || !finger.pressed) break
            if (moved) onDrag(finger.position.x)
        }
    } finally {
        onDrop()
    }
}

/** Takes every change of the gesture; a release within the touch slop is a tap at its x. */
private suspend fun AwaitPointerEventScope.tapUntilUp(down: PointerInputChange, onTap: (Float) -> Unit) {
    var isTap = true
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val finger = event.changes.firstOrNull { it.id == down.id }
        event.changes.forEach { it.consume() }
        if (finger == null) return
        if ((finger.position - down.position).getDistance() > viewConfiguration.touchSlop) isTap = false
        if (!finger.pressed) break
    }
    if (isTap) onTap(down.position.x)
}
