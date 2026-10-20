package app.memix.feature.videoeditor

import app.memix.core.domain.video.PreviewSession
import app.memix.core.model.project.Project
import app.memix.feature.videoeditor.timeline.ItemName
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.CoroutineScope

/**
 * What the video editor's tool helpers ([EditorSoundTools], [VolumePanelControl], [MoveControl]) use of the editor:
 * its state, its edit session and how it shows things. [VideoEditorViewModel] implements it once; main thread only.
 */
internal interface EditorHost {
    /** The editor's ViewModel scope; work started here ends with the editor. */
    val scope: CoroutineScope

    /** The UiState as it is now. */
    val uiState: VideoEditorUiState

    fun updateUiState(transform: (VideoEditorUiState) -> VideoEditorUiState)

    /** The live preview, once the draft has opened and has clips. */
    val preview: PreviewSession?

    /** The draft as edited so far; null until it has opened. */
    fun currentProject(): Project?

    /** The time under the playhead, µs. */
    fun currentPositionUs(): Long

    /** Applies [change] as one [edit] step and shows it at once; false when it changed nothing (no step, no `tool_use`). */
    fun commit(edit: VideoEdit, pausesPlayback: Boolean = true, change: (Project) -> Project): Boolean

    /** Selects [itemId] with its tools, or clears the selection when it's null or gone. */
    fun select(itemId: String?)

    /** Says what [edit] did to [item], for screen readers only ("Delete: clip 2"). */
    fun announce(edit: VideoEdit, item: ItemName?)

    fun showToast(message: ToastMessage, spokenOnly: Boolean = false)

    fun returnFocusTo(target: FocusTarget)

    /** A new landing of [itemIds] for [VideoEditorUiState.landed], so they bonk (with a new serial). */
    fun newLanding(itemIds: ImmutableSet<String>, isSound: Boolean = false): LandedItems

    fun newId(): String
}
