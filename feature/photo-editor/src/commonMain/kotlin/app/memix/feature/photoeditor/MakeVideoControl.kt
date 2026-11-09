package app.memix.feature.photoeditor

import app.memix.core.domain.photo.MakeVideoMemeOutcome
import app.memix.core.domain.photo.MakeVideoMemeUseCase
import app.memix.core.domain.photo.hasPhotoContent
import app.memix.core.model.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the Make it a video meme control needs from the editor: the draft, the state, a toast, and the typing commit. */
internal interface MakeVideoHost {
    fun currentProject(): Project?

    fun updateUiState(transform: (PhotoEditorUiState) -> PhotoEditorUiState)

    val uiState: PhotoEditorUiState

    fun showToast(message: PhotoToastMessage)

    /** Commits the words being typed as a step, so the video has them. */
    fun commitTyping()

    /** Writes the photo draft now, so it is on the Drafts screen whatever happens next. */
    fun saveNow()
}

/**
 * The Make it a video meme button's behavior (spec docs/ux/specs/P2-10-make-it-a-video-meme.md): explains when there is nothing to
 * convert or a cut-out is still being made, else hands the draft as it is at the tap to [MakeVideoMemeUseCase] and goes inert until the
 * video draft exists. The photo draft is never changed.
 */
internal class MakeVideoControl(
    private val scope: CoroutineScope,
    private val makeVideoMeme: MakeVideoMemeUseCase,
    private val host: MakeVideoHost,
) {
    /** The tap: typing commits first; an empty canvas and a cut-out in progress explain instead. */
    fun start() {
        if (host.uiState.makingVideo) return
        if (host.uiState.textEntry != null) host.commitTyping()
        val project = host.currentProject() ?: return
        if (isCuttingOut(host.uiState)) return host.showToast(PhotoToastMessage.Explain(PhotoExplanation.EXPORT_BUSY))
        if (!project.hasPhotoContent()) return host.showToast(PhotoToastMessage.Explain(PhotoExplanation.EXPORT_EMPTY))
        host.updateUiState { it.copy(makingVideo = true) }
        host.saveNow()
        scope.launch { finish(makeVideoMeme(project)) }
    }

    /** The app opened the video editor: nothing is waiting any more. */
    fun opened() {
        host.updateUiState { it.copy(openVideoMeme = null, makingVideo = false) }
    }

    // Made: the editor stays inert until the app has opened the video editor, so a second tap can't make a second draft.
    private fun finish(outcome: MakeVideoMemeOutcome) {
        when (outcome) {
            is MakeVideoMemeOutcome.Made -> host.updateUiState { it.copy(openVideoMeme = outcome.projectId) }
            MakeVideoMemeOutcome.NoSpace -> failWith(PhotoExplanation.MAKE_VIDEO_NO_SPACE)
            MakeVideoMemeOutcome.Failed -> failWith(PhotoExplanation.MAKE_VIDEO_FAILED)
        }
    }

    private fun failWith(explanation: PhotoExplanation) {
        host.updateUiState { it.copy(makingVideo = false) }
        host.showToast(PhotoToastMessage.Explain(explanation))
    }

    // A cut-out still being made: the picture would silently lack it (spec → When it can't run).
    private fun isCuttingOut(state: PhotoEditorUiState): Boolean =
        state.cutOut?.phase.let { it is CutOutPhase.Preparing || it == CutOutPhase.Working }
}
