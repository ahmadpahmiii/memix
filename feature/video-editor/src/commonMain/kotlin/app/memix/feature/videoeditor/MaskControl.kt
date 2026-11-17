package app.memix.feature.videoeditor

import app.memix.core.domain.project.MaskLimit
import app.memix.core.domain.project.MaskStep
import app.memix.core.domain.project.keyableClip
import app.memix.core.domain.project.limit
import app.memix.core.domain.project.mainClipBox
import app.memix.core.domain.project.maskOf
import app.memix.core.domain.project.overlayBoxAtScaleOne
import app.memix.core.domain.project.setClipMask
import app.memix.core.domain.project.setMaskShape
import app.memix.core.domain.project.steppedBy
import app.memix.core.model.project.ClipMask
import app.memix.core.model.project.MaskShape
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind
import kotlin.math.roundToInt

/**
 * The Mask panel and the mask on the stage (spec P4-03b). Picking a shape, a slider's release, Invert, a stage gesture's release, a
 * Position tab tap and a held arrow's release are one undo step each; the sliders, the stage's fingers and a held arrow move the mask live
 * ([app.memix.core.domain.video.PreviewSession.previewMask]) and change the project on release. The fingers' live mask is held by the
 * stage layer itself, so a finger move never recomposes the editor.
 */
internal class MaskControl(private val host: EditorHost) {
    /** True while a finger is down on the stage's mask: Undo and Redo wait (spec → Gestures). */
    var fingerDown: Boolean = false
        private set

    /** The Mask tool: the panel opens on Shape, playback left as it was; the playhead jumps to the clip when it is outside it. */
    fun open() {
        val itemId = host.uiState.selectedItemId ?: return
        val project = host.currentProject() ?: return
        val clip = project.keyableClip(itemId) ?: return
        showAtPlayhead(clip)
        host.updateUiState { it.copy(maskPanel = panelOf(project, clip, MaskTab.SHAPE, host.uiState.overlays)) }
    }

    /** Done, back or Escape: the panel and the frame go, the mask stays; focus goes back to the tool. */
    fun close() {
        if (host.uiState.maskPanel == null) return
        fingerDown = false
        host.updateUiState { it.copy(maskPanel = null) }
        host.returnFocusTo(FocusTarget.MASK_TOOL)
    }

    fun selectTab(tab: MaskTab) {
        val panel = host.uiState.maskPanel ?: return
        host.updateUiState { it.copy(maskPanel = panel.copy(tab = tab)) }
    }

    /** A shape tile (null is None): one "Mask" step. Edge softness and Invert stay; the place starts at the shape's defaults. */
    fun pickShape(shape: MaskShape?) {
        val panel = host.uiState.maskPanel ?: return
        host.commit(VideoEdit.MASK, pausesPlayback = false, value = shape?.id ?: NONE_VALUE) { it.setMaskShape(panel.itemId, shape) }
    }

    fun softnessChanged(softness: Float) = moveLive { it.copy(softness = softness.coerceIn(0f, 1f)) }

    fun cornersChanged(roundness: Float) = moveLive { it.copy(cornerRoundness = roundness.coerceIn(0f, 1f)) }

    /** A slider was let go (or stepped, whichever way): one "Mask" step, none if it is where it began. */
    fun sliderReleased() {
        val panel = host.uiState.maskPanel ?: return
        commitPanelMask(panel, VideoEdit.MASK_ADJUST)
    }

    /** The Invert toggle: one "Mask" step. */
    fun invertChanged(invert: Boolean) {
        val panel = host.uiState.maskPanel ?: return
        val mask = panel.mask ?: return
        commitMask(panel, mask.copy(invert = invert), VideoEdit.MASK_ADJUST)
    }

    /** A Position tab tap, or a held arrow's release ([repeats] steps): one "Mask position" step, or an explanation at a limit. */
    fun step(step: MaskStep, repeats: Int) {
        val panel = host.uiState.maskPanel ?: return
        val mask = committedMask(panel) ?: return host.showToast(ToastMessage.Explain(Explanation.MASK_NONE))
        val stepped = mask.steppedBy(step, repeats) ?: return explainLimit(step.limit)
        if (!commitMask(panel, stepped, VideoEdit.MASK_POSITION)) return
        host.showToast(spokenResult(step, stepped), spokenOnly = true)
    }

    /** A held arrow, [repeats] steps along: the picture and the frame show it, nothing is committed until the release. */
    fun stepHeld(step: MaskStep, repeats: Int) {
        val panel = host.uiState.maskPanel ?: return
        // [repeats] counts from where the hold began, so it steps from the project's mask, not from what the last repeat showed.
        val stepped = committedMask(panel)?.steppedBy(step, repeats) ?: return
        moveLive { stepped }
    }

    /** A finger went down on the stage's mask. */
    fun gestureStarted() {
        fingerDown = true
    }

    /** The mask under the fingers: the preview draws it at once; the project and the panel wait for the release. */
    fun gestureMoved(mask: ClipMask) {
        val panel = host.uiState.maskPanel ?: return
        host.preview?.previewMask(panel.itemId, mask)
    }

    /** The fingers were lifted with the mask where they left it: one "Mask position" step, none if it didn't move. */
    fun gestureEnded(mask: ClipMask) {
        fingerDown = false
        val panel = host.uiState.maskPanel ?: return
        if (mask == panel.mask) return
        commitMask(panel, mask, VideoEdit.MASK_POSITION)
    }

    /** The project changed (an edit, undo or redo): the panel shows the clip's mask, and closes when the clip is gone. */
    fun syncedWith(panel: MaskUi?, project: Project, overlays: List<OverlayUi>): MaskUi? {
        val shown = panel ?: return null
        val clip = project.keyableClip(shown.itemId) ?: return null
        return panelOf(project, clip, shown.tab, overlays)
    }

    // The mask the project has, which the panel's own copy leaves while a slider, a finger or a held arrow moves it.
    private fun committedMask(panel: MaskUi): ClipMask? = host.currentProject()?.maskOf(panel.itemId)

    // The slider's or held arrow's newest mask: the picture and the panel show it at once.
    private fun moveLive(change: (ClipMask) -> ClipMask) {
        val panel = host.uiState.maskPanel ?: return
        val mask = panel.mask ?: return
        val moved = change(mask)
        if (moved == mask) return
        host.preview?.previewMask(panel.itemId, moved)
        host.updateUiState { it.copy(maskPanel = panel.copy(mask = moved)) }
    }

    private fun commitPanelMask(panel: MaskUi, edit: VideoEdit) {
        val mask = panel.mask ?: return
        commitMask(panel, mask, edit)
    }

    // The panel shows [mask] at once; the project follows as one step. False when nothing changed.
    private fun commitMask(panel: MaskUi, mask: ClipMask, edit: VideoEdit): Boolean {
        host.updateUiState { it.copy(maskPanel = panel.copy(mask = mask)) }
        return host.commit(edit, pausesPlayback = false) { it.setClipMask(panel.itemId, mask) }
    }

    private fun explainLimit(limit: MaskLimit?) {
        val explanation = when (limit) {
            MaskLimit.SMALLEST -> Explanation.MASK_MIN
            MaskLimit.BIGGEST -> Explanation.MASK_MAX
            MaskLimit.EDGE -> Explanation.MASK_EDGE
            // Center on a centered mask changes nothing and has nothing to say.
            null -> return
        }
        host.showToast(ToastMessage.Explain(explanation))
    }

    // The result is spoken, never only seen.
    private fun spokenResult(step: MaskStep, mask: ClipMask): ToastMessage = when (step) {
        MaskStep.NARROWER, MaskStep.WIDER -> ToastMessage.MaskStepped(MaskReading.WIDTH, (mask.width * PERCENT).roundToInt())
        MaskStep.SHORTER, MaskStep.TALLER -> ToastMessage.MaskStepped(MaskReading.HEIGHT, (mask.height * PERCENT).roundToInt())
        MaskStep.ROTATE_LEFT, MaskStep.ROTATE_RIGHT -> ToastMessage.MaskStepped(MaskReading.ROTATION, mask.rotationDegrees.roundToInt())
        MaskStep.MOVE_LEFT, MaskStep.MOVE_UP, MaskStep.MOVE_DOWN, MaskStep.MOVE_RIGHT, MaskStep.CENTER ->
            ToastMessage.TextPositioned((mask.centerX * PERCENT).roundToInt(), (mask.centerY * PERCENT).roundToInt())
    }

    private fun panelOf(project: Project, clip: MediaClip, tab: MaskTab, overlays: List<OverlayUi>): MaskUi {
        val canvas = PixelSize(project.canvas.widthPx, project.canvas.heightPx)
        val isOverlay = project.findTrackKind(clip.id) == TrackKind.OVERLAY
        val box = if (isOverlay) overlayBoxAtScaleOne(clip, canvas).let { it.width to it.height } else mainClipBox(clip, canvas)
        val overlay = if (isOverlay) overlays.firstOrNull { it.itemId == clip.id } else null
        return MaskUi(clip.id, project.maskOf(clip.id), box.first, box.second, overlay, tab)
    }

    private fun Project.findTrackKind(itemId: String): TrackKind? = video?.tracks?.firstOrNull { track -> track.items.any { it.id == itemId } }?.kind

    // Opening with the playhead outside the clip moves it to the clip's start, so the mask can be seen (the Opacity panel's rule).
    private fun showAtPlayhead(clip: MediaClip) {
        val atUs = host.currentPositionUs()
        if (atUs >= clip.startUs && atUs < clip.startUs + clip.durationUs) return
        host.preview?.seekTo(clip.startUs)
    }

    private companion object {
        const val NONE_VALUE = "none"
        const val PERCENT = 100f
    }
}
