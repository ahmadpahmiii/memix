package app.memix.feature.videoeditor.timeline

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
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
