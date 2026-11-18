package app.memix.feature.videoeditor

import app.memix.core.domain.Outcome
import app.memix.core.domain.project.freezeFrame
import app.memix.core.domain.project.freezePlan
import app.memix.core.domain.project.mainMediaClip
import app.memix.core.domain.video.ReadFreezePictureUseCase
import kotlinx.coroutines.launch

/**
 * The Freeze frame tool of a main video clip (spec docs/ux/specs/P4-10-reverse-and-freeze.md): reads the frame under the playhead into a picture, then, in one undo
 * step, splits the clip there and puts a 2 s still between the pieces (the edit is [freezeFrame]). Playback pauses, the new still is selected and the playhead sits on
 * its start. A playhead off the selected clip explains instead.
 */
internal class FreezeControl(private val host: EditorHost, private val readPicture: ReadFreezePictureUseCase) {
    private var reading = false

    fun freeze() {
        val clipId = host.uiState.selectedItemId ?: return
        val project = host.currentProject() ?: return
        val clip = project.mainMediaClip(clipId) ?: return
        val atUs = host.currentPositionUs()
        val plan = project.freezePlan(clipId, atUs) ?: return host.showToast(ToastMessage.Explain(Explanation.FREEZE_OUTSIDE))
        if (reading) return
        reading = true
        host.preview?.pause()
        host.scope.launch {
            try {
                when (val picture = readPicture(project.id, clip.source, plan.sourceTimeUs)) {
                    is Outcome.Success -> land(clipId, atUs, picture.value)
                    is Outcome.Failure -> host.showToast(ToastMessage.Explain(Explanation.FREEZE_FAILED))
                }
            } finally {
                reading = false
            }
        }
    }

    // The project may have changed while the frame was read (an edit, an undo): the plan is made again on what it is now, and a clip that moved on cancels the freeze.
    private fun land(clipId: String, atUs: Long, picture: app.memix.core.model.project.MediaRef) {
        val freezeId = host.newId()
        val rightPieceId = host.newId()
        val recorded = host.commit(VideoEdit.FREEZE_FRAME, change = { current ->
            val plan = current.freezePlan(clipId, atUs) ?: return@commit current
            current.freezeFrame(plan, picture, freezeId, rightPieceId)
        })
        if (!recorded) return host.showToast(ToastMessage.Explain(Explanation.FREEZE_OUTSIDE))
        val landed = host.currentProject()?.mainMediaClip(freezeId) ?: return
        host.select(freezeId)
        host.preview?.seekTo(landed.startUs)
        host.showToast(ToastMessage.FreezeAdded, spokenOnly = true)
    }
}
