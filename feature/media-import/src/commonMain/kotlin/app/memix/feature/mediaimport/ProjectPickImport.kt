package app.memix.feature.mediaimport

import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A pick for a project that already exists, what both editors do after a picker returns: copying, the import sheet and its
 * results are P1-02's ([MediaImportFlow]); what the copies become depends on the [request] that opened the picker (the
 * video editor's Add media and Replace, a canvas background picture, the photo editor's Add photo), which the owner's
 * [Landing] decides.
 *
 * A pick's life: the picker returns ([start], [nothingPicked] or [pickerUnavailable]), the copies land, the import sheet goes
 * (what landed is shown through [Landing.showLanding]), and [Landing.onPickEnded] runs once, whatever the outcome. Lives in
 * the owner's [scope]; main thread only.
 */
class ProjectPickImport<R : Any>(
    importMedia: ImportMediaUseCase,
    private val scope: CoroutineScope,
    private val projectId: String,
    private val landing: Landing<R>,
) {
    /** What the owner does for a pick of request type [R]. */
    interface Landing<R : Any> {
        /** Puts the copies [added] into the project for [request]; [LandingResult.Declined] when nothing changed (the copies are then deleted). */
        suspend fun land(request: R, added: List<MediaRef>): LandingResult

        /** Shows what landed once the import sheet is done; [afterSheet]: the sheet was up and slides away first. */
        fun showLanding(request: R, afterSheet: Boolean)

        /** The pick that [request] started is over, whatever happened. */
        fun onPickEnded(request: R)
    }

    private val flow = MediaImportFlow(importMedia, scope)

    /** The import sheet's state. */
    val state: StateFlow<ImportUiState> = flow.state

    // The request of the pick under way, from the picker's result until the sheet is gone.
    private var current: R? = null
    private var landed = false
    // "Pick again": the sheet closes and the same picker opens, so the pick isn't over.
    private var pickingAgain = false

    init {
        scope.launch { flow.state.collect(::onImportStateChanged) }
    }

    /** The picker returned [picked] (at least one item) for [request]: copy them, then land them. */
    fun start(request: R, picked: List<MediaOrigin>) {
        if (isBusy()) return
        current = request
        pickingAgain = false
        landed = false
        flow.start(picked, projectId) { added ->
            val result = landing.land(request, added)
            if (result == LandingResult.Landed) landed = true
            result
        }
    }

    /** The picker couldn't open for [request]: the sheet says so, and closing it ends the pick. */
    fun pickerUnavailable(request: R) {
        if (isBusy()) return
        current = request
        pickingAgain = false
        flow.onIntent(ImportIntent.PickerUnavailable)
    }

    /** The picker came back empty for [request] (cancelled, back, nothing picked): nothing changes. */
    fun nothingPicked(request: R) {
        if (isBusy()) return
        current = request
        pickingAgain = false
        endPick()
    }

    fun onIntent(intent: ImportIntent) = flow.onIntent(intent)

    /** "Pick again": closes the sheet and returns the request to open the picker for again, if a pick is under way. */
    fun pickAgain(): R? {
        val request = current ?: return null
        pickingAgain = true
        flow.onIntent(ImportIntent.Close)
        return request
    }

    // One pick at a time: a result that arrives while another import runs is dropped (an editor never asks for two).
    private fun isBusy(): Boolean = flow.state.value != ImportUiState.Idle

    private fun onImportStateChanged(state: ImportUiState) {
        when (state) {
            is ImportUiState.Done -> {
                // Nothing waits on the editor, unlike Create → Video meme, which opens it first.
                flow.onIntent(ImportIntent.Finish)
                val request = current
                if (landed && request != null) landing.showLanding(request, afterSheet = state.sheetShown)
                landed = false
            }
            ImportUiState.Idle -> endPick()
            is ImportUiState.Copying, is ImportUiState.NotEnoughSpace, is ImportUiState.SomeNotAdded,
            is ImportUiState.NoneAdded, ImportUiState.NoPicker,
            -> Unit
        }
    }

    private fun endPick() {
        val request = current ?: return
        if (pickingAgain) return
        current = null
        landed = false
        landing.onPickEnded(request)
    }
}
