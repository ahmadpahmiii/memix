package app.memix.feature.videoeditor

import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.domain.project.MediaPlace
import app.memix.core.domain.project.ReplaceFit
import app.memix.core.domain.project.insertMedia
import app.memix.core.domain.project.mainClip
import app.memix.core.domain.project.replaceClipMedia
import app.memix.core.domain.project.replaceFit
import app.memix.core.domain.project.setBackgroundImage
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.feature.mediaimport.ImportIntent
import app.memix.feature.mediaimport.LandingResult
import app.memix.feature.mediaimport.ProjectPickImport
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The editor's Add media, Replace and canvas background picture (spec P1-06 → Add media, Replace; P2-12). The pick itself
 * (copying, the import sheet and its results) is [ProjectPickImport], shared with the photo editor; this decides what the
 * copies become:
 * - **Add media:** the pick goes on the main track at the request's [MediaPlace], one "Add media" step.
 * - **Replace:** the clip takes the new media when it fits (a photo, or a video at least as long as the clip), one
 *   "Replace" step. A shorter video is refused with a toast and its copy deleted; a clip that went meanwhile (an undo)
 *   takes nothing.
 * - **Background:** the first picture becomes the canvas background, one "Background" step.
 *
 * What landed is shown once the import sheet is done ([Editor.showLanding]); when the pick is over, whatever the
 * outcome, [Editor.onPickEnded] runs. Lives in the editor's [scope]; main thread only.
 */
internal class EditorMediaImport(
    importMedia: ImportMediaUseCase,
    scope: CoroutineScope,
    projectId: String,
    private val editor: Editor,
) {
    /** What the editor does for an import: its draft, its undo steps, and how it shows things. */
    interface Editor {
        /** The draft as it is now, once it has opened (a pick that comes back after a restart waits for it). */
        suspend fun openedProject(): Project

        /** Applies [change] as one [edit] step; false when it changed nothing. */
        fun commit(edit: VideoEdit, value: String? = null, change: (Project) -> Project): Boolean

        fun showToast(message: ToastMessage)

        /** Shows what landed; [afterSheet]: the import sheet was up and slides away first. */
        fun showLanding(landing: MediaLanding, afterSheet: Boolean)

        /** The pick that [request] started is over, whatever happened. */
        fun onPickEnded(request: MediaPickRequest)
    }

    private val pick = ProjectPickImport(importMedia, scope, projectId, PickLanding())

    /** The import sheet's state, for [VideoEditorUiState.mediaImport]. */
    val ui = EditorMediaImportUi(pick.state)

    private var landed: MediaLanding? = null

    /** The picker returned [picked] (at least one item) for [request]: copy them, then land them. */
    fun start(request: MediaPickRequest, picked: List<MediaOrigin>) = pick.start(request, picked)

    /** The picker couldn't open for [request]: the sheet says so, and closing it ends the pick. */
    fun pickerUnavailable(request: MediaPickRequest) = pick.pickerUnavailable(request)

    /** The picker came back empty for [request] (cancelled, back, nothing picked): nothing changes. */
    fun nothingPicked(request: MediaPickRequest) = pick.nothingPicked(request)

    fun onIntent(intent: ImportIntent) = pick.onIntent(intent)

    /** "Pick again": closes the sheet and returns the request to open the picker for again, if a pick is under way. */
    fun pickAgain(): MediaPickRequest? = pick.pickAgain()

    private inner class PickLanding : ProjectPickImport.Landing<MediaPickRequest> {
        override suspend fun land(request: MediaPickRequest, added: List<MediaRef>): LandingResult {
            val project = editor.openedProject()
            return when (request) {
                is MediaPickRequest.Add -> addMedia(request.place, added)
                is MediaPickRequest.Replace -> replaceClip(project, request.itemId, added.first())
                MediaPickRequest.Background -> setBackground(added.first())
            }
        }

        override fun showLanding(request: MediaPickRequest, afterSheet: Boolean) {
            landed?.let { editor.showLanding(it, afterSheet) }
            landed = null
        }

        override fun onPickEnded(request: MediaPickRequest) {
            landed = null
            editor.onPickEnded(request)
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

    // The picker only offers images; a file that isn't one is declined. The Canvas panel is still open under the pick.
    private fun setBackground(media: MediaRef): LandingResult {
        if (media.kind != MediaKind.IMAGE) return LandingResult.Declined
        if (!editor.commit(VideoEdit.CANVAS_BACKGROUND, value = BACKGROUND_IMAGE_VALUE) { it.setBackgroundImage(media) }) return LandingResult.Declined
        landed = MediaLanding.Background
        return LandingResult.Landed
    }
}

/** `tool_use.value` of a canvas background that is a picture (spec P2-12 → Analytics): never anything about the picture. */
private const val BACKGROUND_IMAGE_VALUE = "image"

/** What an import put in the project, shown once its sheet is gone. */
internal sealed interface MediaLanding {
    /** New main clips [clipIds], in play order. */
    data class Added(val clipIds: List<String>) : MediaLanding

    /** Main clip [itemId] has new media; [newSoundMuted]: its sound was detached, so the new one starts at 0%. */
    data class Replaced(val itemId: String, val newSoundMuted: Boolean) : MediaLanding

    /** The canvas background is a picture now (P2-12). */
    data object Background : MediaLanding
}
