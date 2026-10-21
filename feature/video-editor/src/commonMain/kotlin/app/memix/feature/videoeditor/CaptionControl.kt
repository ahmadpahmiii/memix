package app.memix.feature.videoeditor

import app.memix.core.domain.project.TEXT_MOVE_STEP
import app.memix.core.domain.project.addText
import app.memix.core.domain.project.clampedForText
import app.memix.core.domain.project.editText
import app.memix.core.domain.project.nextTextRotation
import app.memix.core.domain.project.nextTextSizeDown
import app.memix.core.domain.project.nextTextSizeUp
import app.memix.core.domain.project.setCaptionStyle
import app.memix.core.domain.project.setTextTransform
import app.memix.core.domain.project.textItem
import app.memix.core.domain.project.textLandingCenterY
import app.memix.core.domain.project.textLandingSpan
import app.memix.core.domain.project.videoLengthUs
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.Transform
import app.memix.feature.videoeditor.timeline.nameOf
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The captions (spec P1-10): typing, the Style and Position panels, gestures on the preview and the stand-in that
 * draws a caption while it's edited. Each committed change is one undo step and one preview update; while typing,
 * dragging or holding, only the stand-in changes, never the project.
 */
internal class CaptionControl(private val host: EditorHost) {
    private var nextEntrySerial = 0L

    // The words in the field now; read once, when typing commits.
    private var entryText = ""

    // The last style picked in this editor visit: a new caption takes it (spec → Landing → Style).
    private var lastStyle: CaptionStyle = CaptionCatalog.defaultStyle

    // The stand-in waits for the preview to start and then finish catching up with its commit.
    private var standInSawCatchUp = false
    private var standInTimeout: Job? = null

    /** The Text tool: typing a new caption, in the last style, at the first free slot over the playhead. */
    fun startNew() {
        val project = host.currentProject() ?: return
        host.preview?.pause()
        val atUs = host.currentPositionUs()
        val transform = Transform(centerX = 0.5f, centerY = project.textLandingCenterY(atUs))
        entryText = ""
        host.updateUiState { it.copy(textEntry = TextEntryUi(nextEntrySerial++, itemId = null, initialText = "", lastStyle, transform)) }
    }

    /** Edit text, or a tap on the selected caption: typing its words, with the preview leaving it to the stand-in. */
    fun editSelected() {
        val item = selectedText() ?: return
        host.preview?.pause()
        showAtPlayhead(item)
        host.preview?.hideCaption(item.id)
        entryText = item.text
        host.updateUiState { it.copy(textEntry = TextEntryUi(nextEntrySerial++, item.id, item.text, item.style, item.transform), textPanel = null) }
    }

    fun changeEntry(text: String) {
        entryText = text
    }

    /** Every way out of typing keeps the words (spec → Typing → commit). Returns false when nothing was being typed. */
    fun commitEntry(): Boolean {
        val entry = host.uiState.textEntry ?: return false
        host.updateUiState { it.copy(textEntry = null) }
        val text = entryText
        val itemId = entry.itemId
        if (itemId == null) addNew(text, entry) else commitEdit(itemId, text)
        return true
    }

    fun openPanel(panel: TextPanel) {
        val item = selectedText() ?: return
        host.preview?.pause()
        showAtPlayhead(item)
        host.updateUiState { it.copy(textPanel = panel) }
    }

    fun closePanel() {
        val panel = host.uiState.textPanel ?: return
        host.updateUiState { it.copy(textPanel = null) }
        host.returnFocusTo(if (panel == TextPanel.STYLE) FocusTarget.STYLE_TOOL else FocusTarget.POSITION_TOOL)
    }

    fun setFont(fontId: String) = restyle(VideoEdit.TEXT_FONT, font = fontId) { it.copy(fontId = fontId) }

    fun setColor(argb: Long) = restyle(VideoEdit.TEXT_COLOR) { it.copy(color = ArgbColor(argb)) }

    fun setOutline(argb: Long?) = restyle(VideoEdit.TEXT_OUTLINE) { it.copy(outlineColor = argb?.let(::ArgbColor)) }

    /** A Position panel tap, or a held arrow's release ([repeats] steps): one step, or an explanation at a limit. */
    fun step(step: PositionStep, repeats: Int) {
        val item = selectedText() ?: return
        val stepped = stepped(item.transform, step, repeats)
        if (stepped == null) return host.showToast(ToastMessage.Explain(limitOf(step)))
        val edit = if (step.changesPlace) VideoEdit.POSITION_TEXT else VideoEdit.TRANSFORM_TEXT
        commitTransform(item, stepped, edit)
        if (!step.changesPlace) host.showToast(ToastMessage.TextTransformed(stepped.scalePercent, stepped.rotationDegrees.roundToInt()), spokenOnly = true)
    }

    /** A held Move arrow: the caption shows [repeats] steps along in the stand-in; the release commits. */
    fun stepHeld(step: PositionStep, repeats: Int) {
        val item = selectedText() ?: return
        val stepped = stepped(item.transform, step, repeats) ?: return
        host.preview?.hideCaption(item.id)
        showStandIn(item.copy(transform = stepped), awaitingCommit = false)
    }

    /** A tap on caption [itemId] on the preview: selects it; on the selected caption, typing starts. */
    fun tap(itemId: String) {
        host.preview?.pause()
        if (host.uiState.selectedItemId == itemId) return editSelected()
        host.select(itemId)
    }

    /** A gesture on caption [itemId] began: it's selected, and the preview leaves it to the stand-in until the release. */
    fun startGesture(itemId: String) {
        host.preview?.pause()
        if (host.uiState.selectedItemId != itemId) host.select(itemId)
        host.preview?.hideCaption(itemId)
    }

    /** The gesture ended at [transform]: "Position" when only the place changed, else "Size and rotation". */
    fun endGesture(itemId: String, transform: Transform) {
        val item = host.currentProject()?.textItem(itemId) ?: return
        val clamped = transform.clampedForText()
        val onlyMoved = clamped.scale == item.transform.scale && clamped.rotationDegrees == item.transform.rotationDegrees
        commitTransform(item, clamped, if (onlyMoved) VideoEdit.POSITION_TEXT else VideoEdit.TRANSFORM_TEXT)
    }

    fun cancelGesture() {
        host.preview?.hideCaption(null)
    }

    /** The preview's catching-up state changed; once it has shown the stand-in's commit, the stand-in goes. */
    fun onPreviewCatchingUp(catchingUp: Boolean) {
        if (host.uiState.captionStandIn == null) return
        if (catchingUp) standInSawCatchUp = true else if (standInSawCatchUp) clearStandIn()
    }

    private fun addNew(text: String, entry: TextEntryUi) {
        if (text.isBlank()) return host.returnFocusTo(FocusTarget.TEXT_TOOL)
        val project = host.currentProject() ?: return
        val atUs = host.currentPositionUs()
        val itemId = host.newId()
        val added = host.commit(VideoEdit.ADD_TEXT, font = entry.style.fontId) {
            it.addText(text, entry.style, atUs, itemId, newTrackId = host.newId())
        }
        if (!added) return
        val item = host.currentProject()?.textItem(itemId) ?: return
        val landed = host.newLanding(persistentSetOf(itemId))
        host.updateUiState { it.withSelection(itemId, host.currentProject()).copy(landed = landed) }
        showCommittedStandIn(item)
        host.showToast(ToastMessage.TextAdded(textLandingSpan(atUs, project.videoLengthUs()).first), spokenOnly = true)
        host.returnFocusTo(FocusTarget.SELECTED_CAPTION)
    }

    private fun commitEdit(itemId: String, text: String) {
        val before = host.currentProject()?.textItem(itemId) ?: return host.preview?.hideCaption(null) ?: Unit
        val name = host.uiState.timeline.nameOf(itemId)
        // Cleared to blank, the caption goes, as one Delete step (undo brings it back).
        val edit = if (text.isBlank()) VideoEdit.DELETE else VideoEdit.EDIT_TEXT
        if (!host.commit(edit) { it.editText(itemId, text) }) {
            host.preview?.hideCaption(null)
            return
        }
        if (edit == VideoEdit.DELETE) {
            host.select(null)
            host.announce(VideoEdit.DELETE, name)
            return
        }
        showCommittedStandIn(before.copy(text = text))
        host.returnFocusTo(FocusTarget.SELECTED_CAPTION)
    }

    private fun restyle(edit: VideoEdit, font: String? = null, change: (CaptionStyle) -> CaptionStyle) {
        val item = selectedText() ?: return
        val style = change(item.style)
        lastStyle = style
        // Tapping the selected option changes nothing and records nothing (spec → Style panel).
        if (style == item.style) return
        host.preview?.hideCaption(item.id)
        if (!host.commit(edit, pausesPlayback = true, font = font) { it.setCaptionStyle(item.id, style) }) return
        showCommittedStandIn(item.copy(style = style))
    }

    private fun commitTransform(item: TextItem, transform: Transform, edit: VideoEdit) {
        if (!host.commit(edit) { it.setTextTransform(item.id, transform) }) {
            clearStandIn()
            host.preview?.hideCaption(null)
            return
        }
        showCommittedStandIn(item.copy(transform = transform.clampedForText()))
    }

    // The committed caption stays drawn by the editor until the preview's new frame shows it.
    private fun showCommittedStandIn(item: TextItem) = showStandIn(item, awaitingCommit = true)

    private fun showStandIn(item: TextItem, awaitingCommit: Boolean) {
        val canvas = host.uiState.canvas ?: return
        val measure = host.uiState.captionMeasure ?: return
        val caption = CaptionUi(
            item.id, item.text, item.style, item.transform, item.startUs, item.startUs + item.durationUs,
            measure.blockOf(item.text, item.style, canvas.widthPx, canvas.heightPx),
        )
        standInSawCatchUp = false
        host.updateUiState { it.copy(captionStandIn = caption) }
        standInTimeout?.cancel()
        if (!awaitingCommit) return
        // Defensive: if the preview never reports catching up (it failed), the stand-in still goes.
        standInTimeout = host.scope.launch {
            delay(STAND_IN_TIMEOUT_MS)
            clearStandIn()
        }
    }

    private fun clearStandIn() {
        standInTimeout?.cancel()
        standInTimeout = null
        standInSawCatchUp = false
        if (host.uiState.captionStandIn != null) host.updateUiState { it.copy(captionStandIn = null) }
    }

    // Opening Edit text, Style or Position with the playhead outside the caption moves it to the caption's start, so the
    // change can be seen (spec → Entry and exit).
    private fun showAtPlayhead(item: TextItem) {
        val atUs = host.currentPositionUs()
        if (atUs >= item.startUs && atUs < item.startUs + item.durationUs) return
        host.preview?.seekTo(item.startUs)
    }

    private fun selectedText(): TextItem? {
        val itemId = host.uiState.selectedItemId ?: return null
        return host.currentProject()?.textItem(itemId)
    }

    // The transform after [repeats] taps of [step], or null when the first one already hits a limit.
    private fun stepped(transform: Transform, step: PositionStep, repeats: Int): Transform? {
        var current = transform
        repeat(repeats) { index ->
            val next = steppedOnce(current, step)
            if (next == null) return if (index == 0) null else current
            current = next
        }
        return current
    }

    private fun steppedOnce(transform: Transform, step: PositionStep): Transform? = when (step) {
        PositionStep.SMALLER -> nextTextSizeDown(transform.scale)?.let { transform.copy(scale = it) }
        PositionStep.BIGGER -> nextTextSizeUp(transform.scale)?.let { transform.copy(scale = it) }
        PositionStep.ROTATE_LEFT -> transform.copy(rotationDegrees = nextTextRotation(transform.rotationDegrees, clockwise = false))
        PositionStep.ROTATE_RIGHT -> transform.copy(rotationDegrees = nextTextRotation(transform.rotationDegrees, clockwise = true))
        PositionStep.MOVE_LEFT -> transform.movedBy(-TEXT_MOVE_STEP, 0f)
        PositionStep.MOVE_RIGHT -> transform.movedBy(TEXT_MOVE_STEP, 0f)
        PositionStep.MOVE_UP -> transform.movedBy(0f, -TEXT_MOVE_STEP)
        PositionStep.MOVE_DOWN -> transform.movedBy(0f, TEXT_MOVE_STEP)
        PositionStep.CENTER -> transform.copy(centerX = CENTER, centerY = CENTER)
    }

    // Null when the center is already at that edge: the text can't go further.
    private fun Transform.movedBy(dx: Float, dy: Float): Transform? {
        val moved = copy(centerX = (centerX + dx).coerceIn(0f, 1f), centerY = (centerY + dy).coerceIn(0f, 1f))
        return if (moved == this) null else moved
    }

    private fun limitOf(step: PositionStep): Explanation = when (step) {
        PositionStep.SMALLER -> Explanation.TEXT_SMALLEST
        PositionStep.BIGGER -> Explanation.TEXT_BIGGEST
        PositionStep.ROTATE_LEFT, PositionStep.ROTATE_RIGHT, PositionStep.MOVE_LEFT, PositionStep.MOVE_UP,
        PositionStep.MOVE_DOWN, PositionStep.MOVE_RIGHT, PositionStep.CENTER -> Explanation.TEXT_EDGE
    }

    private val PositionStep.changesPlace: Boolean
        get() = this != PositionStep.SMALLER && this != PositionStep.BIGGER && this != PositionStep.ROTATE_LEFT && this != PositionStep.ROTATE_RIGHT

    private val Transform.scalePercent: Int get() = (scale * PERCENT).roundToInt()

    private companion object {
        const val CENTER = 0.5f
        const val PERCENT = 100
        const val STAND_IN_TIMEOUT_MS = 2_000L
    }
}
