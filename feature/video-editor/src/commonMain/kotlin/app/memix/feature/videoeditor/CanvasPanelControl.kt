package app.memix.feature.videoeditor

import app.memix.core.domain.project.CanvasRatioChange
import app.memix.core.domain.project.CaptionBlockMeasure
import app.memix.core.domain.project.setCanvasBackground
import app.memix.core.domain.project.setCanvasRatio
import app.memix.core.domain.project.setClipsFit
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.CanvasCatalog
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.FrameFit
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.RatioTerms

/**
 * The Canvas panel (spec docs/ux/specs/P1-11-canvas.md): the ratio, a custom ratio's steppers, Fit or Fill for every
 * main clip, and the background. Each choice is one undo step that applies at once; playback keeps going, so the
 * change can be judged while the video plays. A ratio change keeps captions inside the frame in the same step, measured
 * with the caption renderer ([measure]).
 */
internal class CanvasPanelControl(private val host: EditorHost, private val measure: CaptionBlockMeasure) {
    /** The ratio pill: opens the panel, or closes it when it's open. Reorder mode explains instead. */
    fun togglePanel() {
        if (host.uiState.canvasPanel != null) return close()
        if (host.uiState.reorder != null) return host.showToast(ToastMessage.Explain(Explanation.CANVAS_REORDER))
        // Panels never stack: another one closes, its changes already applied (spec → Opening from other states).
        val canvas = host.currentProject()?.canvas ?: return
        // Focus goes to the panel's title, not where a surface just closed asked for it (typing commits to its caption).
        host.updateUiState {
            it.copy(canvasPanel = CanvasPanelUi(customTerms = customTermsOf(canvas)), volumePanel = null, textPanel = null, returnFocus = null)
        }
    }

    /** Done, back, Escape or the pill: focus goes back to the pill. */
    fun close() {
        if (host.uiState.canvasPanel == null) return
        host.updateUiState { it.copy(canvasPanel = null) }
        host.returnFocusTo(FocusTarget.RATIO_PILL)
    }

    /** A preset ratio tile; the selected one does nothing. A tap also ends a custom visit: the steppers go. */
    fun setRatio(ratio: CanvasRatio) {
        if (ratio == CanvasRatio.CUSTOM) return selectCustom()
        val label = CanvasCatalog.presetSize(ratio)?.let(RatioTerms::of)?.toString() ?: return
        host.updateUiState { it.copy(canvasPanel = it.canvasPanel?.copy(customTerms = null, heldTerms = null)) }
        commitRatio(ratio, customTerms = null, analyticsValue = label)?.let(::showMoved)
    }

    /**
     * The Custom tile: the steppers show, filled with the current ratio's terms. Nothing changes until the first step,
     * so the picture doesn't jump.
     */
    private fun selectCustom() {
        val canvas = host.currentProject()?.canvas ?: return
        val panel = host.uiState.canvasPanel ?: return
        if (panel.customTerms != null) return
        host.updateUiState { it.copy(canvasPanel = panel.copy(customTerms = RatioTerms.of(canvas.size()))) }
    }

    /** A held stepper: the readout and the pill show [repeats] steps along (stopping at a limit); nothing is committed. */
    fun stepHeld(step: RatioStep, repeats: Int) {
        val panel = host.uiState.canvasPanel ?: return
        val terms = panel.customTerms ?: return
        val stepped = stepped(terms, step, repeats).terms
        host.updateUiState { it.copy(canvasPanel = panel.copy(heldTerms = stepped)) }
    }

    /**
     * A stepper tap, or a held stepper's release after [repeats] steps: the canvas takes the new custom ratio as one
     * "Ratio" step. A step past a limit doesn't apply and explains why.
     */
    fun step(step: RatioStep, repeats: Int) {
        val panel = host.uiState.canvasPanel ?: return
        val terms = panel.customTerms ?: return
        val result = stepped(terms, step, repeats)
        host.updateUiState { it.copy(canvasPanel = panel.copy(customTerms = result.terms, heldTerms = null)) }
        val change = if (result.terms != terms) commitRatio(CanvasRatio.CUSTOM, result.terms, analyticsValue = CUSTOM_VALUE) else null
        when {
            change != null && change.movedLayers -> host.showToast(ToastMessage.LayersMoved)
            result.limit != null -> host.showToast(ToastMessage.Explain(result.limit))
            change != null -> host.showToast(ToastMessage.RatioStepped(result.terms.lowest().toString()), spokenOnly = true)
        }
    }

    /** Fit or Fill for every main clip; the selected one does nothing. */
    fun setFit(fit: FrameFit) {
        val edit = if (fit == FrameFit.FIT) VideoEdit.CANVAS_FIT else VideoEdit.CANVAS_FILL
        host.commit(edit, pausesPlayback = false, value = if (fit == FrameFit.FIT) "fit" else "fill") { it.setClipsFit(fit) }
    }

    /** A background swatch: [argb] as 0xAARRGGBB, or blur when it's null. The selected one does nothing. */
    fun setBackground(argb: Long?) {
        val background = if (argb == null) CanvasBackground.Blur else CanvasBackground.Solid(ArgbColor(argb))
        host.commit(VideoEdit.CANVAS_BACKGROUND, pausesPlayback = false, value = if (argb == null) "blur" else "color") {
            it.setCanvasBackground(background)
        }
    }

    /**
     * After an edit, undo or redo: the steppers follow the canvas. They go when it's no longer custom (spec → Interactions);
     * they show its terms when an undo or redo brought another custom ratio back. They keep the numbers as stepped while
     * the canvas has their ratio (10 and 16 for 5:8).
     */
    fun onCanvasChanged(canvas: Canvas) {
        val panel = host.uiState.canvasPanel ?: return
        val terms = panel.customTerms ?: return
        val shown = when {
            canvas.ratio == CanvasRatio.CUSTOM -> terms.takeIf { it.lowest() == RatioTerms.of(canvas.size()) } ?: RatioTerms.of(canvas.size())
            // Custom picked but not stepped yet: the canvas is still the preset the steppers were filled from.
            terms.lowest() == RatioTerms.of(canvas.size()) -> terms
            else -> null
        }
        if (shown != terms) host.updateUiState { it.copy(canvasPanel = panel.copy(customTerms = shown, heldTerms = null)) }
    }

    // One "Ratio" step; null when it changed nothing.
    private fun commitRatio(ratio: CanvasRatio, customTerms: RatioTerms?, analyticsValue: String): CanvasRatioChange? {
        val project = host.currentProject() ?: return null
        val change = project.setCanvasRatio(ratio, customTerms, measure)
        return change.takeIf { host.commit(VideoEdit.CANVAS_RATIO, pausesPlayback = false, value = analyticsValue) { change.project } }
    }

    // "Text moved to stay inside the frame." when the change pulled a caption in.
    private fun showMoved(change: CanvasRatioChange) {
        if (change.movedLayers) host.showToast(ToastMessage.LayersMoved)
    }

    private fun customTermsOf(canvas: Canvas): RatioTerms? = if (canvas.ratio == CanvasRatio.CUSTOM) RatioTerms.of(canvas.size()) else null

    private data class Stepped(val terms: RatioTerms, val limit: Explanation?)

    // [terms] after up to [repeats] steps; the first step that would pass a limit stops it, and names the limit.
    private fun stepped(terms: RatioTerms, step: RatioStep, repeats: Int): Stepped {
        var current = terms
        repeat(repeats) {
            val next = when (step) {
                RatioStep.WIDTH_DOWN -> current.copy(width = current.width - 1)
                RatioStep.WIDTH_UP -> current.copy(width = current.width + 1)
                RatioStep.HEIGHT_DOWN -> current.copy(height = current.height - 1)
                RatioStep.HEIGHT_UP -> current.copy(height = current.height + 1)
            }
            limitOf(next)?.let { return Stepped(current, it) }
            current = next
        }
        return Stepped(current, null)
    }

    private fun limitOf(terms: RatioTerms): Explanation? = when {
        terms.width < CanvasCatalog.CUSTOM_TERMS.first || terms.height < CanvasCatalog.CUSTOM_TERMS.first -> Explanation.CANVAS_MIN
        terms.width > CanvasCatalog.CUSTOM_TERMS.last || terms.height > CanvasCatalog.CUSTOM_TERMS.last -> Explanation.CANVAS_MAX
        CanvasCatalog.allowsCustom(terms) -> null
        terms.width > terms.height -> Explanation.CANVAS_WIDEST
        else -> Explanation.CANVAS_TALLEST
    }

    private companion object {
        /** `tool_use.value` for a custom ratio: never its numbers (spec → Analytics). */
        const val CUSTOM_VALUE = "custom"
    }
}

/** One step of a custom ratio's Width or Height stepper. */
enum class RatioStep { WIDTH_DOWN, WIDTH_UP, HEIGHT_DOWN, HEIGHT_UP }

internal fun Canvas.size(): PixelSize = PixelSize(widthPx, heightPx)
