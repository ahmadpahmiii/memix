package app.memix.feature.videoeditor.timeline

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import app.memix.core.designsystem.MemixMotion
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What the timeline's gestures and accessibility actions do to its [state] and the preview (spec P1-05 → Scroll, zoom
 * and scrub; Motion). The composable hands it the newest timeline, intents and settings on every composition.
 * Main thread only.
 */
@Stable
internal class TimelineController(val state: TimelineState, private val scope: CoroutineScope) {
    var timeline: TimelineUi = TimelineUi.Empty
    var onIntent: (VideoEditorIntent) -> Unit = {}
    var reduceMotion = false
    var density = 1f

    private var seekAnimation: Job? = null
    private var zoomAnimation: Job? = null

    /** The most zoomed-out scale for this video and screen: the whole video fits in half the width. */
    val minDpPerSecond: Float get() = minDpPerSecond(timeline.lengthUs, state.widthPx / density)

    val canZoomIn: Boolean get() = state.dpPerSecond < MAX_DP_PER_SECOND
    val canZoomOut: Boolean get() = state.dpPerSecond > minDpPerSecond

    /** A finger landed: playback pauses, and an animated seek or zoom stops where it is. */
    fun onTouchDown() {
        state.isFingerDown = true
        zoomAnimation?.cancel()
        if (seekAnimation?.isActive == true) {
            seekAnimation?.cancel()
            // The preview went straight to the target; bring it back to where the timeline stopped.
            onIntent(VideoEditorIntent.SeekTo(state.playheadUs))
        }
        onIntent(VideoEditorIntent.TimelineTouched)
    }

    fun onTouchUp() {
        state.isFingerDown = false
    }

    /** A drag or fling frame of [deltaPx]; returns what was used, so a fling stops at 0:00 and at the end. */
    fun scrollBy(deltaPx: Float): Float {
        val consumedPx = state.scrollBy(deltaPx, timeline.lengthUs, density)
        if (consumedPx != 0f) onIntent(VideoEditorIntent.ScrubTo(state.playheadUs))
        return consumedPx
    }

    /** One pinch step: the scale follows the fingers; the time under the playhead stays put. */
    fun zoomBy(zoomChange: Float) {
        state.dpPerSecond = clampDpPerSecond(state.dpPerSecond * zoomChange, minDpPerSecond)
    }

    fun zoomIn() = zoomTo(clampDpPerSecond(state.dpPerSecond * ZOOM_MENU_STEP, minDpPerSecond))

    fun zoomOut() = zoomTo(clampDpPerSecond(state.dpPerSecond / ZOOM_MENU_STEP, minDpPerSecond))

    fun showWholeVideo() = zoomTo(minDpPerSecond)

    /** A tap on the ruler: [timeUs] scrolls under the playhead over 200 ms (a jump with reduce motion). */
    fun seekAnimated(timeUs: Long) {
        val targetUs = timeUs.coerceIn(0, timeline.lengthUs.coerceAtLeast(0))
        seekAnimation?.cancel()
        onIntent(VideoEditorIntent.SeekTo(targetUs))
        if (reduceMotion) {
            state.playheadUs = targetUs
            return
        }
        val startUs = state.playheadUs
        seekAnimation = scope.launch {
            state.isSeekAnimating = true
            try {
                animate(0f, 1f, animationSpec = tween(MemixMotion.durationSheet)) { fraction, _ ->
                    state.playheadUs = startUs + ((targetUs - startUs) * fraction).roundToLong()
                }
                state.playheadUs = targetUs
            } finally {
                state.isSeekAnimating = false
            }
        }
    }

    /** Accessibility steps and jumps: the playhead moves at once. */
    fun seekNow(timeUs: Long) {
        seekAnimation?.cancel()
        val targetUs = timeUs.coerceIn(0, timeline.lengthUs.coerceAtLeast(0))
        state.playheadUs = targetUs
        onIntent(VideoEditorIntent.SeekTo(targetUs))
    }

    private fun zoomTo(dpPerSecond: Float) {
        zoomAnimation?.cancel()
        if (reduceMotion) {
            state.dpPerSecond = dpPerSecond
            return
        }
        zoomAnimation = scope.launch {
            animate(state.dpPerSecond, dpPerSecond, animationSpec = tween(MemixMotion.durationSheet)) { value, _ ->
                state.dpPerSecond = value
            }
        }
    }
}
