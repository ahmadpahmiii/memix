package app.memix.feature.videoeditor

import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.domain.project.MediaPlace
import app.memix.core.domain.project.ReplaceFit
import app.memix.core.domain.project.insertMedia
import app.memix.core.domain.project.mainClip
import app.memix.core.domain.project.replaceClipMedia
import app.memix.core.domain.project.replaceFit
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.feature.videoeditor.importmedia.ImportIntent
import app.memix.feature.videoeditor.importmedia.ImportUiState
import app.memix.feature.videoeditor.importmedia.LandingResult
import app.memix.feature.videoeditor.importmedia.MediaImportFlow
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The editor's Add media and Replace (spec P1-06 → Add media, Replace). Copying, the import sheet and its results are
 * P1-02's ([MediaImportFlow]); this decides what the copies become:
 * - **Add media:** the pick goes on the main track at the request's [MediaPlace], one "Add media" step.
 * - **Replace:** the clip takes the new media when it fits (a photo, or a video at least as long as the clip), one
 *   "Replace" step. A shorter video is refused with a toast and its copy deleted; a clip that went meanwhile (an undo)
 *   takes nothing.
 *
 * What landed is shown once the import sheet is done ([Editor.showLanding]); when the pick is over, whatever the
 * outcome, [Editor.onPickEnded] runs. Lives in the editor's [scope]; main thread only.
 */
internal class EditorMediaImport(
    importMedia: ImportMediaUseCase,
    private val scope: CoroutineScope,
    private val projectId: String,
    private val editor: Editor,
) {
    /** What the editor does for an import: its draft, its undo steps, and how it shows things. */
    interface Editor {
        /** The draft as it is now, once it has opened (a pick that comes back after a restart waits for it). */
        suspend fun openedProject(): Project

        /** Applies [change] as one [edit] step; false when it changed nothing. */
        fun commit(edit: VideoEdit, change: (Project) -> Project): Boolean

        fun showToast(message: ToastMessage)

        /** Shows what landed; [afterSheet]: the import sheet was up and slides away first. */
        fun showLanding(landing: MediaLanding, afterSheet: Boolean)

        /** The pick that [request] started is over, whatever happened. */
        fun onPickEnded(request: MediaPickRequest)
    }

    private val flow = MediaImportFlow(importMedia, scope)

    /** The import sheet's state, for [VideoEditorUiState.mediaImport]. */
    val ui = EditorMediaImportUi(flow.state)

    // The request of the pick under way, from the picker's result until the sheet is gone.
    private var current: MediaPickRequest? = null
    private var landed: MediaLanding? = null
    // "Pick again": the sheet closes and the same picker opens, so the pick isn't over.
    private var pickingAgain = false

    init {
        scope.launch { flow.state.collect(::onImportStateChanged) }
    }

    /** The picker returned [picked] (at least one item) for [request]: copy them, then land them. */
    fun start(request: MediaPickRequest, picked: List<MediaOrigin>) {
        if (isBusy()) return
        current = request
        pickingAgain = false
        flow.start(picked, projectId) { added -> land(request, added) }
    }

    /** The picker couldn't open for [request]: the sheet says so, and closing it ends the pick. */
    fun pickerUnavailable(request: MediaPickRequest) {
        if (isBusy()) return
        current = request
        pickingAgain = false
        flow.onIntent(ImportIntent.PickerUnavailable)
    }

    /** The picker came back empty for [request] (cancelled, back, nothing picked): nothing changes. */
    fun nothingPicked(request: MediaPickRequest) {
        if (isBusy()) return
        current = request
        pickingAgain = false
        endPick()
    }

    fun onIntent(intent: ImportIntent) = flow.onIntent(intent)

    /** "Pick again": closes the sheet and returns the request to open the picker for again, if a pick is under way. */
    fun pickAgain(): MediaPickRequest? {
        val request = current ?: return null
        pickingAgain = true
        flow.onIntent(ImportIntent.Close)
        return request
    }

    // One pick at a time: a result that arrives while another import runs is dropped (the editor never asks for two).
    private fun isBusy(): Boolean = flow.state.value != ImportUiState.Idle

    private suspend fun land(request: MediaPickRequest, added: List<MediaRef>): LandingResult {
        val project = editor.openedProject()
        return when (request) {
            is MediaPickRequest.Add -> addMedia(request.place, added)
            is MediaPickRequest.Replace -> replaceClip(project, request.itemId, added.first())
        }
    }

    private fun addMedia(place: MediaPlace, added: List<MediaRef>): LandingResult {
        val clipIds = added.map { Uuid.random().toString() }
        if (!editor.commit(VideoEdit.ADD_MEDIA) { it.insertMedia(added, clipIds, place) }) return LandingResult.Declined
        landed = MediaLanding.Added(clipIds)
        return LandingResult.Landed
    }

    private fun replaceClip(project: Project, itemId: String, media: MediaRef): LandingResult {
        val clip = project.mainClip(itemId) ?: return LandingResult.Declined
        when (project.replaceFit(itemId, media)) {
            ReplaceFit.FITS -> Unit
            ReplaceFit.TOO_SHORT -> {
                editor.showToast(ToastMessage.ReplaceTooShort(clip.durationUs))
                return LandingResult.Declined
            }
            null -> return LandingResult.Declined
        }
        if (!editor.commit(VideoEdit.REPLACE) { it.replaceClipMedia(itemId, media) }) return LandingResult.Declined
        // The detached sound goes on playing, so the new sound starts at 0%; a video with sound says so.
        val newSoundMuted = clip.audioDetached && media.kind == MediaKind.VIDEO && media.hasAudio == true
        landed = MediaLanding.Replaced(itemId, newSoundMuted)
        return LandingResult.Landed
    }

    private fun onImportStateChanged(state: ImportUiState) {
        when (state) {
            is ImportUiState.Done -> {
                // Nothing waits on the editor, unlike Create → Video meme, which opens it first.
                flow.onIntent(ImportIntent.Finish)
                landed?.let { editor.showLanding(it, afterSheet = state.sheetShown) }
                landed = null
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
        landed = null
        editor.onPickEnded(request)
    }
}

/** What an import put in the project, shown once its sheet is gone. */
internal sealed interface MediaLanding {
    /** New main clips [clipIds], in play order. */
    data class Added(val clipIds: List<String>) : MediaLanding

    /** Main clip [itemId] has new media; [newSoundMuted]: its sound was detached, so the new one starts at 0%. */
    data class Replaced(val itemId: String, val newSoundMuted: Boolean) : MediaLanding
}
