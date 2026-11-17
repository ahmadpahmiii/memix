package app.memix.feature.videoeditor

import app.memix.core.domain.project.overlayClip
import app.memix.core.domain.project.overlayLayerOf
import app.memix.core.domain.project.reorderOverlay
import app.memix.core.domain.project.setOverlayOpacity
import app.memix.core.domain.project.setOverlayTransform
import app.memix.core.domain.project.clampedForOverlay
import app.memix.core.model.project.KeyframeCatalog
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.OverlayCatalog
import app.memix.core.model.project.SizeLadder
import app.memix.core.model.project.Transform
import app.memix.core.ui.panels.PositionLimit
import app.memix.core.ui.panels.PositionStep
import app.memix.core.ui.panels.changesPlace
import app.memix.core.ui.panels.limit
import app.memix.core.ui.panels.steppedBy
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The overlays (spec P4-01b): picking the one on the stage, its gestures, its Position, Opacity and Layer order panels. Each
 * committed change is one undo step. While a finger or a held arrow moves an overlay the preview hides it and the editor's
 * stand-in ([OverlayStandInUi], drawn from [OverlayFrames]) follows, so it never shows twice; the project changes on release only.
 * The opacity slider is heard and seen live by the preview itself ([app.memix.core.domain.video.PreviewSession.previewOpacity]).
 */
internal class OverlayControl(private val host: EditorHost) {
    // The stand-in waits for the preview to start and then finish catching up with its commit (the captions' rule).
    private var standInSawCatchUp = false
    private var standInTimeout: Job? = null

    // A held Position arrow shows the stand-in but hasn't been released yet: if its panel goes away first, the overlay must show again.
    private var holdingArrow = false

    /** The selected overlay, or null when the selection isn't one. */
    fun selectedOverlay(): MediaClip? {
        val itemId = host.uiState.selectedItemId ?: return null
        return host.currentProject()?.overlayClip(itemId)
    }

    /** A tap on an overlay that isn't selected: pauses and selects it. */
    fun select(itemId: String) {
        cancelHeldArrow()
        host.preview?.pause()
        host.select(itemId)
    }

    /** A gesture on overlay [itemId] began: it's selected, and the preview leaves it to the stand-in until the release. */
    fun startGesture(itemId: String) {
        host.preview?.pause()
        if (host.uiState.selectedItemId != itemId) host.select(itemId)
        hideForStandIn(itemId)
    }

    /** The gesture ended at [transform]: one "Position and size" step. */
    fun endGesture(itemId: String, transform: Transform) {
        val overlay = host.currentProject()?.overlayClip(itemId) ?: return showPreviewsOverlay()
        commitTransform(overlay, transform, VideoEdit.TRANSFORM_OVERLAY)
    }

    fun cancelGesture() {
        showPreviewsOverlay()
    }

    fun openPanel(panel: OverlayPanel) {
        val overlay = selectedOverlay() ?: return
        // Opacity keeps playing, so the blend is seen (spec → Opacity panel); the others wait for a still frame.
        // A keyed item's panel shows its values under the playhead, so the playhead waits too.
        val keyed = host.routesThroughKeyframes(overlay.id)
        if (panel != OverlayPanel.OPACITY || keyed) host.preview?.pause()
        showAtPlayhead(overlay)
        host.updateUiState { it.copy(overlayPanel = OverlayPanelUi(panel, overlay.id, opacityNow(overlay), keyed)) }
    }

    /** A held arrow that never got its release (panel closed, selection changed): the overlay shows where it was. */
    fun cancelHeldArrow() {
        if (!holdingArrow) return
        clearStandIn()
        showPreviewsOverlay()
    }

    fun closePanel() {
        cancelHeldArrow()
        val panel = host.uiState.overlayPanel ?: return
        host.updateUiState { it.copy(overlayPanel = null) }
        host.returnFocusTo(
            when (panel.panel) {
                OverlayPanel.OPACITY -> FocusTarget.OPACITY_TOOL
                OverlayPanel.POSITION -> FocusTarget.POSITION_TOOL
                OverlayPanel.ORDER -> FocusTarget.LAYER_ORDER_TOOL
            },
        )
    }

    /** The opacity slider moved to [opacity]: the preview shows it at once; nothing is committed yet. */
    fun opacityChanged(opacity: Float) {
        val panel = host.uiState.overlayPanel ?: return
        val value = opacity.coerceIn(if (host.routesThroughKeyframes(panel.itemId)) KeyframeCatalog.OPACITY_RANGE else OverlayCatalog.OPACITY_RANGE)
        host.preview?.previewOpacity(panel.itemId, value)
        host.updateUiState { it.copy(overlayPanel = panel.copy(opacity = value)) }
    }

    /** The slider was let go (or stepped): one "Opacity" step, none if it is where it began. */
    fun opacityReleased() {
        val panel = host.uiState.overlayPanel ?: return
        val recorded = if (host.routesThroughKeyframes(panel.itemId)) {
            host.commitLook(VideoEdit.OVERLAY_OPACITY, panel.itemId, pausesPlayback = false) { it.copy(opacity = panel.opacity) }
        } else {
            host.commit(VideoEdit.OVERLAY_OPACITY, pausesPlayback = false) { it.setOverlayOpacity(panel.itemId, panel.opacity) }
        }
        if (!recorded) {
            host.preview?.previewOpacity(panel.itemId, null)
            return
        }
        host.showToast(ToastMessage.OverlayOpacity((panel.opacity * PERCENT).roundToInt()), spokenOnly = true)
    }

    /** A Position panel tap, or a held arrow's release ([repeats] steps): one step, or an explanation at a limit. */
    fun step(step: PositionStep, repeats: Int) {
        val overlay = selectedOverlay() ?: return
        val stepped = placeNow(overlay).steppedBy(step, repeats, SIZE_LADDER, centerRangeOf(overlay.id))
            ?: return host.showToast(ToastMessage.Explain(explanationOf(step.limit)))
        val edit = if (step.changesPlace) VideoEdit.POSITION_OVERLAY else VideoEdit.TRANSFORM_OVERLAY
        if (!commitTransform(overlay, stepped, edit)) return
        // The result is spoken, never only seen.
        val result = if (step.changesPlace) {
            ToastMessage.TextPositioned((stepped.centerX * PERCENT).roundToInt(), (stepped.centerY * PERCENT).roundToInt())
        } else {
            ToastMessage.TextTransformed((stepped.scale * PERCENT).roundToInt(), stepped.rotationDegrees.roundToInt())
        }
        host.showToast(result, spokenOnly = true)
    }

    /** A held Move arrow: the overlay shows [repeats] steps along in the stand-in; the release commits. */
    fun stepHeld(step: PositionStep, repeats: Int) {
        val overlay = selectedOverlay() ?: return
        val stepped = placeNow(overlay).steppedBy(step, repeats, SIZE_LADDER, centerRangeOf(overlay.id)) ?: return
        if (!hideForStandIn(overlay.id)) return
        holdingArrow = true
        showStandIn(OverlayStandInUi(overlay.id, stepped.clampedForOverlay(centerRangeOf(overlay.id)), opacityNow(overlay)), awaitingCommit = false)
    }

    /** Bring forward ([forward]) or Send back: one "Layer order" step, or an explanation when no overlay shares a moment in that direction. */
    fun order(forward: Boolean) {
        val itemId = host.uiState.selectedItemId ?: return
        val newTrackId = host.newId()
        if (!host.commit(VideoEdit.OVERLAY_ORDER) { it.reorderOverlay(itemId, forward, newTrackId) }) {
            return host.showToast(ToastMessage.Explain(if (forward) Explanation.LAYER_ORDER_FRONT else Explanation.LAYER_ORDER_BACK))
        }
        // Said, never only seen: the new place among all overlay layers.
        host.updateUiState { it.copy(revealed = host.newLanding(persistentSetOf(itemId))) }
        host.currentProject()?.overlayLayerOf(itemId)?.let { host.showToast(ToastMessage.OverlayLayer(it.layer, it.count), spokenOnly = true) }
    }

    /** The preview's catching-up state changed; once it has shown the stand-in's commit, the stand-in goes. */
    fun onPreviewCatchingUp(catchingUp: Boolean) {
        if (host.uiState.overlayStandIn == null) return
        if (catchingUp) standInSawCatchUp = true else if (standInSawCatchUp) clearStandIn()
    }

    /** The overlay a stand-in belongs to went (an undo): so does the stand-in. */
    fun dropStandInOf(vanished: (String) -> Boolean) {
        val standIn = host.uiState.overlayStandIn ?: return
        if (vanished(standIn.itemId)) clearStandIn()
    }

    // False when it changed nothing (no step).
    private fun commitTransform(overlay: MediaClip, transform: Transform, edit: VideoEdit): Boolean {
        val clamped = transform.clampedForOverlay(centerRangeOf(overlay.id))
        val hidden = hideForStandIn(overlay.id)
        // A keyed item's every edit is a keyframe edit; the static setter would write fields its engine ignores (spec D4).
        val recorded = if (host.routesThroughKeyframes(overlay.id)) {
            host.commitLook(edit, overlay.id) { it.copy(centerX = clamped.centerX, centerY = clamped.centerY, scale = clamped.scale, rotationDegrees = transform.rotationDegrees) }
        } else {
            host.commit(edit) { it.setOverlayTransform(overlay.id, clamped) }
        }
        if (!recorded) {
            clearStandIn()
            showPreviewsOverlay()
            return false
        }
        // The committed overlay stays drawn by the editor until the preview's new frame shows it.
        if (hidden) showStandIn(OverlayStandInUi(overlay.id, clamped, opacityNow(overlay)), awaitingCommit = true)
        return true
    }

    // The overlay's place and opacity under the playhead: its own, or the keyframes' (spec P4-02b → Value panels while the item has keyframes).
    private fun placeNow(overlay: MediaClip): Transform = host.lookNow(overlay.id)?.transform ?: overlay.transform

    private fun opacityNow(overlay: MediaClip): Float = host.lookNow(overlay.id)?.opacity ?: overlay.opacity

    // A keyed item may leave the frame by half a frame (spec Question 1).
    private fun centerRangeOf(itemId: String): ClosedFloatingPointRange<Float> =
        if (host.routesThroughKeyframes(itemId)) KeyframeCatalog.CENTER_RANGE else 0f..1f

    // The preview draws an overlay invisible only when the editor can draw it in its place: no picture, no hiding.
    private fun hideForStandIn(itemId: String): Boolean {
        val overlay = host.uiState.overlays.firstOrNull { it.itemId == itemId } ?: return false
        if (host.uiState.overlayFrames?.isReady(overlay, host.currentPositionUs()) != true) return false
        host.preview?.hideOverlay(itemId)
        host.updateUiState { it.copy(hiddenOverlayId = itemId) }
        return true
    }

    // The preview draws every overlay again.
    private fun showPreviewsOverlay() {
        host.preview?.hideOverlay(null)
        if (host.uiState.hiddenOverlayId != null) host.updateUiState { it.copy(hiddenOverlayId = null) }
    }

    private fun showStandIn(standIn: OverlayStandInUi, awaitingCommit: Boolean) {
        holdingArrow = !awaitingCommit
        standInSawCatchUp = false
        host.updateUiState { it.copy(overlayStandIn = standIn) }
        standInTimeout?.cancel()
        if (!awaitingCommit) return
        // Defensive: if the preview never reports catching up (it failed), the stand-in still goes.
        standInTimeout = host.scope.launch {
            delay(STAND_IN_TIMEOUT_MS)
            clearStandIn()
        }
    }

    private fun clearStandIn() {
        holdingArrow = false
        standInTimeout?.cancel()
        standInTimeout = null
        standInSawCatchUp = false
        if (host.uiState.overlayStandIn != null || host.uiState.hiddenOverlayId != null) host.updateUiState { it.copy(overlayStandIn = null, hiddenOverlayId = null) }
    }

    // Opening Position, Opacity or Layer order with the playhead outside the overlay moves it to the overlay's start, so the
    // change can be seen (spec → Opacity panel).
    private fun showAtPlayhead(overlay: MediaClip) {
        val atUs = host.currentPositionUs()
        if (atUs >= overlay.startUs && atUs < overlay.startUs + overlay.durationUs) return
        host.preview?.seekTo(overlay.startUs)
    }

    private fun explanationOf(limit: PositionLimit): Explanation = when (limit) {
        PositionLimit.SMALLEST -> Explanation.TEXT_SMALLEST
        PositionLimit.BIGGEST -> Explanation.TEXT_BIGGEST
        PositionLimit.EDGE -> Explanation.OVERLAY_EDGE
    }

    private companion object {
        const val PERCENT = 100
        const val STAND_IN_TIMEOUT_MS = 2_000L
        val SIZE_LADDER = SizeLadder(OverlayCatalog.SIZE_STEPS)
    }
}
