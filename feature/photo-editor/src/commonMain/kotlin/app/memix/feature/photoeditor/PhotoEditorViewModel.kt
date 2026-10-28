package app.memix.feature.photoeditor

import androidx.lifecycle.viewModelScope
import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.ProjectSource
import app.memix.core.domain.drafts.UpdateDraftThumbnailUseCase
import app.memix.core.domain.project.CanvasRatioChange
import app.memix.core.domain.project.CreatePhotoProjectUseCase
import app.memix.core.domain.project.LayerEdit
import app.memix.core.domain.project.LayerRefusal
import app.memix.core.domain.project.OpenPhotoDraftUseCase
import app.memix.core.domain.project.ProjectEditSession
import app.memix.core.domain.project.ProjectEditState
import app.memix.core.domain.project.StartEditSessionUseCase
import app.memix.core.domain.project.deleteLayer
import app.memix.core.domain.project.duplicateLayer
import app.memix.core.domain.project.isEmptyDraft
import app.memix.core.domain.project.layers
import app.memix.core.domain.project.moveLayerDown
import app.memix.core.domain.project.moveLayerUp
import app.memix.core.domain.project.reorderLayer
import app.memix.core.domain.project.photoBackgroundShows
import app.memix.core.domain.project.setCanvasBackground
import app.memix.core.domain.project.setLayerHidden
import app.memix.core.domain.project.setLayerLocked
import app.memix.core.domain.project.setLayerOpacity
import app.memix.core.domain.project.setLayerTransform
import app.memix.core.domain.project.setPhotoCanvasRatio
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.CanvasCatalog
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.PhotoCatalog
import app.memix.core.model.project.Project
import app.memix.core.model.project.RatioTerms
import app.memix.core.model.project.SizeLadder
import app.memix.core.model.project.Transform
import app.memix.core.model.project.size
import app.memix.core.ui.MemixViewModel
import app.memix.core.ui.editor.SaveProblem
import app.memix.core.ui.panels.CanvasLimit
import app.memix.core.ui.panels.PositionLimit
import app.memix.core.ui.panels.PositionStep
import app.memix.core.ui.panels.RatioStep
import app.memix.core.ui.panels.changesPlace
import app.memix.core.ui.panels.customTermsAfterChange
import app.memix.core.ui.panels.limit
import app.memix.core.ui.panels.steppedBy
import app.memix.core.ui.panels.steppedTerms
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.launch

/**
 * The photo editor (spec docs/ux/specs/P2-01-photo-editor-shell.md): opens a draft (or starts a blank canvas, [newBlank]),
 * edits it through a [ProjectEditSession] (undo, redo, auto-save) and shows it as one [PhotoEditorUiState].
 *
 * The edits themselves are the domain's (LayerEdits, PhotoCanvasEdits); this class picks the layer, explains when a tool
 * can't act, and announces what happened. A gesture, a held arrow or the opacity slider keeps its in-between values in the
 * screen; the project changes once, on release, as one undo step.
 */
class PhotoEditorViewModel(
    private val projectId: String,
    private val newBlank: Boolean,
    private val openPhotoDraft: OpenPhotoDraftUseCase,
    private val createPhotoProject: CreatePhotoProjectUseCase,
    private val startEditSession: StartEditSessionUseCase,
    private val updateDraftThumbnail: UpdateDraftThumbnailUseCase,
) : MemixViewModel<PhotoEditorUiState, PhotoEditorIntent>(PhotoEditorUiState()) {
    private var editSession: ProjectEditSession? = null
    private var shownProject: Project? = null
    private var saveFailure: AppError? = null
    private var bannerDismissed = false
    private var missingMediaShown = false
    private var gestureActive = false
    private var nextToastId = 0L

    init {
        viewModelScope.launch {
            when (val opened = if (newBlank) Outcome.Success(createPhotoProject().copy(id = projectId)) else openPhotoDraft(projectId)) {
                is Outcome.Success -> if (opened.value.photo != null) startEditing(opened.value) else couldntOpen()
                // A damaged draft, one from a newer app, or one deleted meanwhile: the app says "This draft can't be opened".
                is Outcome.Failure -> couldntOpen()
            }
        }
    }

    override fun onIntent(intent: PhotoEditorIntent) {
        when (intent) {
            PhotoEditorIntent.Undo -> undo()
            PhotoEditorIntent.Redo -> redo()
            PhotoEditorIntent.Close -> requestClose()
            PhotoEditorIntent.Back -> goBack()
            PhotoEditorIntent.LeaveAnyway -> updateState { it.copy(unsavedSheetOpen = false, isClosing = true) }
            PhotoEditorIntent.StayInEditor -> updateState { it.copy(unsavedSheetOpen = false) }
            PhotoEditorIntent.DismissSaveBanner -> dismissSaveBanner()
            PhotoEditorIntent.FreeUpSpaceUnavailable -> updateState { it.copy(canFreeUpSpace = false) }
            PhotoEditorIntent.AppStarted -> if (saveFailure != null) editSession?.saveNow()
            PhotoEditorIntent.AppStopped -> goToBackground()
            is PhotoEditorIntent.ToastTimedOut -> updateState { if (it.toast?.id == intent.id) it.copy(toast = null) else it }
            is PhotoEditorIntent.MediaMissing -> showMissing(intent.paths)
            is PhotoEditorIntent.SelectLayer -> select(intent.layerId)
            PhotoEditorIntent.ClearSelection -> select(null)
            is PhotoEditorIntent.LayerGestureStarted -> startGesture(intent.layerId)
            is PhotoEditorIntent.LayerGestureEnded -> endGesture(intent.layerId, intent.transform)
            PhotoEditorIntent.LayerGestureCancelled -> gestureActive = false
            is PhotoEditorIntent.LockedLayerTouched -> explain(PhotoExplanation.LAYER_LOCKED)
            is PhotoEditorIntent.OpenPanel -> openPanel(intent.panel)
            PhotoEditorIntent.ClosePanel -> updateState { it.copy(panel = null, canvasPanel = null) }
            is PhotoEditorIntent.OpacityCommitted -> setOpacity(intent.layerId, intent.opacity)
            is PhotoEditorIntent.PositionStepped -> stepPosition(intent.layerId, intent.step, intent.repeats)
            is PhotoEditorIntent.PositionStepHeld -> holdPosition(intent.layerId, intent.step, intent.repeats)
            is PhotoEditorIntent.DeleteLayer -> deleteLayer(intent.layerId)
            is PhotoEditorIntent.DuplicateLayer -> duplicateLayer(intent.layerId)
            is PhotoEditorIntent.SetLocked -> setLocked(intent.layerId, intent.locked)
            is PhotoEditorIntent.SetHidden -> setHidden(intent.layerId, intent.hidden)
            is PhotoEditorIntent.MoveLayer -> moveLayer(intent.layerId, intent.towardFront)
            is PhotoEditorIntent.ReorderLayer -> reorder(intent.layerId, intent.indexFromFront)
            PhotoEditorIntent.RatioPill -> toggleCanvasPanel()
            is PhotoEditorIntent.SetCanvasRatio -> setRatio(intent.ratio)
            is PhotoEditorIntent.CanvasStepped -> stepRatio(intent.step, intent.repeats)
            is PhotoEditorIntent.CanvasStepHeld -> holdRatio(intent.step, intent.repeats)
            is PhotoEditorIntent.SetCanvasBackground -> setBackground(intent.argb)
        }
    }

    private fun startEditing(project: Project) {
        val session = startEditSession(project, if (newBlank) ProjectSource.BLANK else null)
        addCloseable(session)
        // The drafts list's cover follows the lowest image layer; it's checked as the editor goes, after the last save starts.
        addCloseable(AutoCloseable { currentProject()?.takeUnless { it.isEmptyDraft() }?.let { updateDraftThumbnail(it) } })
        editSession = session
        viewModelScope.launch { session.state.collect(::showEditState) }
    }

    private fun couldntOpen() = updateState { it.copy(isClosing = true, couldntOpen = true) }

    private fun showEditState(edit: ProjectEditState) {
        if (edit.project !== shownProject) showProject(edit.project)
        showSaveState(edit.saveFailure)
        updateState { it.copy(canUndo = edit.canUndo, canRedo = edit.canRedo) }
    }

    private fun showProject(project: Project) {
        shownProject = project
        val canvasSize = project.canvas.size()
        val layers = project.layers().map { it.toUi(canvasSize) }.toImmutableList()
        val ids = layers.map { it.id }.toSet()
        updateState { state ->
            // The selection outlives edits, undo and redo while its layer exists; a panel about a layer goes with it.
            val selected = state.selectedLayerId?.takeIf { it in ids }
            val panel = state.panel?.takeUnless { it.needsSelection && selected == null }
            state.copy(
                scene = SceneUi(project.canvas, project.layers().toImmutableList()),
                layers = layers,
                backgroundShows = project.photoBackgroundShows(),
                selectedLayerId = selected,
                panel = panel,
                canvasPanel = if (panel == PhotoPanel.CANVAS) state.canvasPanel else null,
                missingLayerIds = state.missingLayerIds.filter { it in ids }.toImmutableSet(),
            )
        }
        updateState { state ->
            val canvasPanel = state.canvasPanel ?: return@updateState state
            val terms = canvasPanel.customTerms ?: return@updateState state
            val shown = customTermsAfterChange(project.canvas, terms)
            if (shown == terms) state else state.copy(canvasPanel = canvasPanel.copy(customTerms = shown, heldTerms = null))
        }
    }

    // Selection --------------------------------------------------------------------------------------------------

    private fun select(layerId: String?) = updateState { state ->
        val selected = layerId?.takeIf { id -> state.layers.any { it.id == id } }
        state.copy(selectedLayerId = selected, panel = state.panel?.takeUnless { it.needsSelection && selected == null })
    }

    private fun startGesture(layerId: String) {
        gestureActive = true
        if (state.value.selectedLayerId != layerId) select(layerId)
    }

    private fun endGesture(layerId: String, transform: Transform) {
        gestureActive = false
        editLayer(PhotoEdit.TRANSFORM) { it.setLayerTransform(layerId, transform) }
        // Even when nothing changed (locked, same place), the stage lets go of the layer it held.
        updateState { it.copy(gestureEndCount = it.gestureEndCount + 1) }
    }

    // Layer tools -------------------------------------------------------------------------------------------------

    private fun setOpacity(layerId: String, opacity: Float) {
        val changed = editLayer(PhotoEdit.OPACITY) { it.setLayerOpacity(layerId, opacity) }
        updateState { it.copy(gestureEndCount = it.gestureEndCount + 1) }
        // A refusal has already explained itself; the spoken result would replace that toast.
        if (changed) showToast(PhotoToastMessage.OpacityChanged((opacity * PERCENT).roundToInt()), spokenOnly = true)
    }

    /** One Position tap, or a held arrow's release: one step, or an explanation at a limit (spec → Position panel). */
    private fun stepPosition(layerId: String, step: PositionStep, repeats: Int) {
        val layer = layerOf(layerId) ?: return
        if (layer.locked) return explain(PhotoExplanation.LAYER_LOCKED)
        val stepped = layer.transform.steppedBy(step, repeats, SIZE_LADDER)
        if (stepped == null) return explain(explanationOf(step.limit))
        if (!editLayer(PhotoEdit.POSITION) { it.setLayerTransform(layerId, stepped) }) {
            updateState { it.copy(held = null) }
            return
        }
        updateState { it.copy(held = null) }
        // The result is spoken, never only seen (P1-10 review P3).
        val result = if (step.changesPlace) {
            PhotoToastMessage.PositionMoved((stepped.centerX * PERCENT).roundToInt(), (stepped.centerY * PERCENT).roundToInt())
        } else {
            PhotoToastMessage.PositionTransformed((stepped.scale * PERCENT).roundToInt(), stepped.rotationDegrees.roundToInt())
        }
        showToast(result, spokenOnly = true)
    }

    /** A held Move arrow: the layer shows [repeats] steps along; the release commits. Nothing is committed here. */
    private fun holdPosition(layerId: String, step: PositionStep, repeats: Int) {
        val layer = layerOf(layerId)?.takeUnless { it.locked } ?: return
        val stepped = layer.transform.steppedBy(step, repeats, SIZE_LADDER) ?: return
        updateState { it.copy(held = HeldTransform(layerId, stepped)) }
    }

    private fun deleteLayer(layerId: String) {
        val position = positionOf(layerId)
        if (editLayer(PhotoEdit.DELETE) { it.deleteLayer(layerId) }) announce(PhotoEdit.DELETE, position.first, position.second)
    }

    private fun duplicateLayer(layerId: String) {
        val layer = layerOf(layerId) ?: return
        if (layer.id in state.value.missingLayerIds) return explain(PhotoExplanation.LAYER_MISSING)
        val newId = Uuid.random().toString()
        if (!commit(PhotoEdit.DUPLICATE) { it.duplicateLayer(layerId, newId) }) return
        select(newId)
        val position = positionOf(newId)
        announce(PhotoEdit.DUPLICATE, position.first, position.second)
    }

    private fun setLocked(layerId: String, locked: Boolean) {
        val edit = if (locked) PhotoEdit.LOCK else PhotoEdit.UNLOCK
        if (commit(edit) { it.setLayerLocked(layerId, locked) }) positionOf(layerId).let { announce(edit, it.first, it.second) }
    }

    private fun setHidden(layerId: String, hidden: Boolean) {
        val edit = if (hidden) PhotoEdit.HIDE else PhotoEdit.SHOW
        if (commit(edit) { it.setLayerHidden(layerId, hidden) }) positionOf(layerId).let { announce(edit, it.first, it.second) }
    }

    private fun moveLayer(layerId: String, towardFront: Boolean) {
        val moved = editLayer(PhotoEdit.REORDER) { if (towardFront) it.moveLayerUp(layerId) else it.moveLayerDown(layerId) }
        if (moved) positionOf(layerId).let { showToast(PhotoToastMessage.LayerOrder(it.first, it.second), spokenOnly = true) }
    }

    private fun reorder(layerId: String, indexFromFront: Int) {
        if (editLayer(PhotoEdit.REORDER) { it.reorderLayer(layerId, indexFromFront) }) {
            positionOf(layerId).let { showToast(PhotoToastMessage.LayerOrder(it.first, it.second), spokenOnly = true) }
        }
    }

    // Panels and the canvas ---------------------------------------------------------------------------------------

    private fun openPanel(panel: PhotoPanel) {
        // Panels never stack: another one closes, its changes already applied. A tool on an empty canvas explains.
        if (panel == PhotoPanel.LAYERS && state.value.layers.isEmpty()) return explain(PhotoExplanation.LAYERS_EMPTY)
        if (panel.needsSelection && state.value.selectedLayerId == null) return
        updateState { it.copy(panel = panel, canvasPanel = null) }
    }

    private fun toggleCanvasPanel() {
        if (state.value.panel == PhotoPanel.CANVAS) return updateState { it.copy(panel = null, canvasPanel = null) }
        val canvas = currentProject()?.canvas ?: return
        val terms = if (canvas.ratio == CanvasRatio.CUSTOM) RatioTerms.of(canvas.size()) else null
        updateState { it.copy(panel = PhotoPanel.CANVAS, canvasPanel = PhotoCanvasPanelUi(customTerms = terms)) }
    }

    private fun setRatio(ratio: CanvasRatio) {
        if (ratio == CanvasRatio.CUSTOM) return selectCustom()
        val label = CanvasCatalog.presetSize(ratio)?.let(RatioTerms::of)?.toString() ?: return
        updateState { it.copy(canvasPanel = it.canvasPanel?.copy(customTerms = null, heldTerms = null)) }
        commitRatio(ratio, null, label)?.let { if (it.movedLayers) showToast(PhotoToastMessage.LayersMoved) }
    }

    // The Custom tile: the steppers show, filled with the current ratio's terms. Nothing changes until the first step.
    private fun selectCustom() {
        val canvas = currentProject()?.canvas ?: return
        val panel = state.value.canvasPanel ?: return
        if (panel.customTerms != null) return
        updateState { it.copy(canvasPanel = panel.copy(customTerms = RatioTerms.of(canvas.size()))) }
    }

    private fun holdRatio(step: RatioStep, repeats: Int) {
        val panel = state.value.canvasPanel ?: return
        val terms = panel.customTerms ?: return
        updateState { it.copy(canvasPanel = panel.copy(heldTerms = steppedTerms(terms, step, repeats).terms)) }
    }

    private fun stepRatio(step: RatioStep, repeats: Int) {
        val panel = state.value.canvasPanel ?: return
        val terms = panel.customTerms ?: return
        val result = steppedTerms(terms, step, repeats)
        updateState { it.copy(canvasPanel = panel.copy(customTerms = result.terms, heldTerms = null)) }
        val change = if (result.terms != terms) commitRatio(CanvasRatio.CUSTOM, result.terms, CUSTOM_VALUE) else null
        val limit = result.limit
        when {
            change != null && change.movedLayers -> showToast(PhotoToastMessage.LayersMoved)
            limit != null -> explain(explanationOf(limit))
            change != null -> showToast(PhotoToastMessage.RatioStepped(result.terms.lowest().toString()), spokenOnly = true)
        }
    }

    // One "Ratio" step; null when it changed nothing.
    private fun commitRatio(ratio: CanvasRatio, customTerms: RatioTerms?, analyticsValue: String): CanvasRatioChange? {
        val change = currentProject()?.setPhotoCanvasRatio(ratio, customTerms) ?: return null
        return change.takeIf { commit(PhotoEdit.CANVAS_RATIO, analyticsValue) { change.project } }
    }

    private fun setBackground(argb: Long) {
        commit(PhotoEdit.CANVAS_BACKGROUND, "color") { it.setCanvasBackground(CanvasBackground.Solid(ArgbColor(argb))) }
    }

    // Editing -----------------------------------------------------------------------------------------------------

    /** Applies [change] as one [edit] step; false when it changed nothing (no step, no `tool_use`). */
    private fun commit(edit: PhotoEdit, value: String? = null, change: (Project) -> Project): Boolean {
        val session = editSession ?: return false
        val recorded = session.commit(edit.tool, value = value, edit = change)
        if (recorded) showEditState(session.state.value)
        return recorded
    }

    /** Commits a layer edit, or explains why it was refused. True when a step was recorded. */
    private fun editLayer(edit: PhotoEdit, change: (Project) -> LayerEdit): Boolean {
        val project = currentProject() ?: return false
        return when (val result = change(project)) {
            is LayerEdit.Refused -> {
                explain(explanationOf(result.reason))
                false
            }
            is LayerEdit.Edited -> commit(edit) { result.project }
        }
    }

    private fun undo() {
        val session = editSession ?: return
        if (gestureActive) return
        val tool = session.state.value.takeIf { it.canUndo }?.undoTool ?: return
        session.undo()
        PhotoEdit.of(tool)?.let { showToast(PhotoToastMessage.Undone(it)) }
    }

    private fun redo() {
        val session = editSession ?: return
        if (gestureActive) return
        val tool = session.state.value.takeIf { it.canRedo }?.redoTool ?: return
        session.redo()
        PhotoEdit.of(tool)?.let { showToast(PhotoToastMessage.Redone(it)) }
    }

    // The innermost thing first (spec → System back order): a panel, then the selection, then the editor. A gesture in
    // progress is cancelled by the stage itself, before this runs.
    private fun goBack() {
        val current = state.value
        when {
            current.panel != null -> updateState { it.copy(panel = null, canvasPanel = null) }
            current.selectedLayerId != null -> select(null)
            else -> requestClose()
        }
    }

    // Leaving saves by itself; only changes that can't be saved make it ask first (spec P1-04 → Saving).
    private fun requestClose() {
        if (saveFailure != null) updateState { it.copy(unsavedSheetOpen = true) } else updateState { it.copy(isClosing = true) }
    }

    private fun goToBackground() {
        // Android may end a background app without warning, so the newest change is written now.
        editSession?.saveNow()
        currentProject()?.takeUnless { it.isEmptyDraft() }?.let { updateDraftThumbnail(it) }
    }

    // Messages ----------------------------------------------------------------------------------------------------

    private fun showMissing(paths: Set<String>) {
        val layerIds = state.value.layers.filter { it.path in paths }.map { it.id }
        if (layerIds.isEmpty()) return
        updateState { it.copy(missingLayerIds = (it.missingLayerIds + layerIds).toImmutableSet()) }
        if (!missingMediaShown) {
            missingMediaShown = true
            showToast(PhotoToastMessage.MissingMedia)
        }
    }

    private fun showSaveState(failure: AppError?) {
        val failedBefore = saveFailure != null
        saveFailure = failure
        when {
            failure != null -> updateState { it.copy(isSaveFailing = true, saveBanner = if (bannerDismissed) null else saveProblemOf(failure)) }
            failedBefore -> {
                // The banner can come back if a later save fails again.
                bannerDismissed = false
                updateState { it.copy(isSaveFailing = false, saveBanner = null, unsavedSheetOpen = false, savedAgainCount = it.savedAgainCount + 1) }
            }
        }
    }

    private fun dismissSaveBanner() {
        bannerDismissed = true
        updateState { it.copy(saveBanner = null) }
    }

    private fun explain(explanation: PhotoExplanation) = showToast(PhotoToastMessage.Explain(explanation))

    // The save banner owns the one message slot: a toast that arrives under it is spoken, not shown or queued.
    private fun showToast(message: PhotoToastMessage, spokenOnly: Boolean = false) {
        val id = nextToastId++
        updateState { it.copy(toast = PhotoToast(id, message, isSpokenOnly = spokenOnly || it.saveBanner != null)) }
    }

    private fun announce(edit: PhotoEdit, position: Int, count: Int) {
        if (count > 0) showToast(PhotoToastMessage.Edited(edit, position, count), spokenOnly = true)
    }

    private fun saveProblemOf(failure: AppError): SaveProblem =
        if (failure == AppError.StorageFull) SaveProblem.STORAGE_FULL else SaveProblem.OTHER

    private fun currentProject(): Project? = editSession?.state?.value?.project

    private fun layerOf(layerId: String): SceneLayerUi? = state.value.layers.firstOrNull { it.id == layerId }

    // A layer's place from the front (1 is the front) and how many there are, for what is spoken.
    private fun positionOf(layerId: String): Pair<Int, Int> {
        val layers = state.value.layers
        val index = layers.indexOfFirst { it.id == layerId }
        return if (index < 0) 0 to layers.size else layers.size - index to layers.size
    }

    private fun explanationOf(reason: LayerRefusal): PhotoExplanation = when (reason) {
        LayerRefusal.LOCKED -> PhotoExplanation.LAYER_LOCKED
        LayerRefusal.ONLY_LAYER -> PhotoExplanation.LAYER_ONE
        LayerRefusal.ALREADY_FRONT -> PhotoExplanation.LAYER_FRONT
        LayerRefusal.ALREADY_BACK -> PhotoExplanation.LAYER_BACK
    }

    private fun explanationOf(limit: PositionLimit): PhotoExplanation = when (limit) {
        PositionLimit.SMALLEST -> PhotoExplanation.SIZE_SMALLEST
        PositionLimit.BIGGEST -> PhotoExplanation.SIZE_BIGGEST
        PositionLimit.EDGE -> PhotoExplanation.LAYER_EDGE
    }

    private fun explanationOf(limit: CanvasLimit): PhotoExplanation = when (limit) {
        CanvasLimit.WIDEST -> PhotoExplanation.CANVAS_WIDEST
        CanvasLimit.TALLEST -> PhotoExplanation.CANVAS_TALLEST
        CanvasLimit.MIN -> PhotoExplanation.CANVAS_MIN
        CanvasLimit.MAX -> PhotoExplanation.CANVAS_MAX
    }

    private val PhotoPanel.needsSelection: Boolean get() = this == PhotoPanel.OPACITY || this == PhotoPanel.POSITION

    private companion object {
        const val PERCENT = 100
        /** `tool_use.value` for a custom ratio: never its numbers (spec → Analytics). */
        const val CUSTOM_VALUE = "custom"
        val SIZE_LADDER = SizeLadder(PhotoCatalog.IMAGE_SIZE_STEPS)
    }
}
