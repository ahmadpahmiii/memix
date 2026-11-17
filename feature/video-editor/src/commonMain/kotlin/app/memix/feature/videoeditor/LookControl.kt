package app.memix.feature.videoeditor

import app.memix.core.domain.Outcome
import app.memix.core.domain.project.AdjustPart
import app.memix.core.domain.project.EditPhotoTab
import app.memix.core.domain.project.changedLookTools
import app.memix.core.domain.project.clipLookOf
import app.memix.core.domain.project.keyableClip
import app.memix.core.domain.project.mainLookClips
import app.memix.core.domain.project.mainLooksMixed
import app.memix.core.domain.project.resetTab
import app.memix.core.domain.project.sharedMainLook
import app.memix.core.domain.project.sourceTimeOf
import app.memix.core.domain.project.updateClipLook
import app.memix.core.domain.project.updateMainLooks
import app.memix.core.domain.project.valueOf
import app.memix.core.domain.project.with
import app.memix.core.domain.video.StartThumbnailsUseCase
import app.memix.core.domain.video.ThumbnailReader
import app.memix.core.domain.EditorTool
import app.memix.core.model.project.ClipLook
import app.memix.core.model.project.FilterRef
import app.memix.core.model.project.ImageEdit
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.ui.scene.SceneBitmap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The Filters panel (spec docs/ux/specs/P4-07-video-filters-addendum.md): from the main bar it acts on every main clip, from a clip's own
 * bar on that clip. A tile tap, an Intensity or Adjust release and Reset are one undo step each; the sliders move the picture live
 * ([app.memix.core.domain.video.PreviewSession.previewLook]) and change the project on release. With main clips whose looks differ (the
 * mixed state) a change writes that one setting to every clip and the rest stays per clip.
 *
 * The tiles are made from one frame of the clip under the playhead, read from the engine's reader when the panel opens and held until it
 * closes; the reader is opened on first use and closed with the panel.
 */
internal class LookControl(
    private val host: EditorHost,
    private val startThumbnails: StartThumbnailsUseCase,
    private val decodeDispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private var reader: ThumbnailReader? = null
    private var frameLoad: Job? = null

    /** The main bar's Filters: every main clip, the playhead where it is. */
    fun openForAllClips() {
        val project = host.currentProject() ?: return
        if (project.mainLookClips().isEmpty()) return
        show(project, itemId = null)
    }

    /** A clip bar's Filters: the selected clip; the playhead jumps to the clip's start when it is outside it. */
    fun openForSelected() {
        val itemId = host.uiState.selectedItemId ?: return
        val project = host.currentProject() ?: return
        val clip = project.keyableClip(itemId) ?: return
        val atUs = host.currentPositionUs()
        if (atUs < clip.startUs || atUs >= clip.startUs + clip.durationUs) host.preview?.seekTo(clip.startUs)
        show(project, itemId)
    }

    /** Done, back or Escape: the panel and the tiles go, the looks stay; focus goes back to the tool. */
    fun closePanel() {
        if (host.uiState.lookPanel == null) return
        release()
        host.updateUiState { it.copy(lookPanel = null) }
        host.returnFocusTo(FocusTarget.FILTERS_TOOL)
    }

    fun selectTab(tab: EditPhotoTab) {
        val panel = host.uiState.lookPanel ?: return
        host.updateUiState { it.copy(lookPanel = panel.copy(tab = tab)) }
    }

    /** A tile (null is None): one "Filter" step, at full intensity; the adjustments stay. */
    fun pickFilter(id: String?) {
        val panel = host.uiState.lookPanel ?: return
        val filter = id?.let { FilterRef(it) }
        commit(panel, if (panel.isAllClips) VideoEdit.FILTER_ALL else VideoEdit.FILTER, filter = id ?: NO_FILTER) { it.copy(filter = filter) }
    }

    /** The Intensity slider moved: the picture follows, nothing is committed yet. */
    fun intensityChanged(intensity: Float) {
        val panel = host.uiState.lookPanel ?: return
        val filter = panel.filter ?: return
        moveLive(panel) { it.copy(filter = it.filter?.copy(intensity = intensity.coerceIn(0f, 1f))) }
        host.updateUiState { it.copy(lookPanel = panel.copy(filter = filter.copy(intensity = intensity.coerceIn(0f, 1f)))) }
    }

    /** The Intensity slider was let go (or stepped): one "Filter" step, none if it is where it began. */
    fun intensityReleased() {
        val panel = host.uiState.lookPanel ?: return
        val intensity = panel.filter?.intensity ?: return
        commit(panel, if (panel.isAllClips) VideoEdit.FILTER_ALL else VideoEdit.FILTER, filter = null) { it.copy(filter = it.filter?.copy(intensity = intensity)) }
    }

    /** An Adjust slider moved: the picture follows, nothing is committed yet. */
    fun adjustChanged(part: AdjustPart, value: Float) {
        val panel = host.uiState.lookPanel ?: return
        val adjust = panel.adjust.with(part, value)
        moveLive(panel) { it.copy(adjust = it.adjust.with(part, value)) }
        host.updateUiState { it.copy(lookPanel = panel.copy(adjust = adjust)) }
    }

    /** An Adjust slider was let go (or stepped): one step named for it, none if it is where it began. */
    fun adjustReleased(part: AdjustPart) {
        val panel = host.uiState.lookPanel ?: return
        val value = panel.adjust.valueOf(part)
        val edit = VideoEdit.of(part.tool) ?: return
        commit(panel, edit) { it.copy(adjust = it.adjust.with(part, value)) }
    }

    /** Reset in the header: the open tab's part goes back to normal for the panel's clips, as one step. */
    fun reset() {
        val panel = host.uiState.lookPanel ?: return
        val project = host.currentProject() ?: return
        val reset = scopedUpdate(panel, project) { it.resetTab(panel.tab) }
        val tools = changedLookTools(project, reset)
        if (tools.isEmpty()) return host.showToast(ToastMessage.NothingToReset)
        host.commit(VideoEdit.RESET_LOOK, pausesPlayback = false, filter = if (EditorTool.FILTER in tools) NO_FILTER else null, loggedAs = tools) { scopedUpdate(panel, it) { look -> look.resetTab(panel.tab) } }
    }

    /** The project changed (an edit, undo or redo): the panel shows the clips' look, and closes when its one clip is gone. */
    fun syncedWith(panel: LookUi?, project: Project): LookUi? {
        val shown = panel ?: return null
        val itemId = shown.itemId
        if (itemId == null) {
            if (project.mainLookClips().isEmpty()) return null
            return shown.copy(adjust = shownLook(project, null, host.currentPositionUs()).adjust, filter = shownFilter(project), mixed = project.mainLooksMixed())
        }
        val look = project.clipLookOf(itemId) ?: return null
        return shown.copy(adjust = look.adjust, filter = look.filter, mixed = false)
    }

    override fun close() = release()

    private fun show(project: Project, itemId: String?) {
        val adjust = shownLook(project, itemId, host.currentPositionUs()).adjust
        val panel = LookUi(itemId, EditPhotoTab.FILTERS, adjust, shownFilter(project, itemId), mixed = itemId == null && project.mainLooksMixed())
        host.updateUiState { it.copy(lookPanel = panel) }
        loadFrame(project, itemId)
    }

    // The look the sliders show: the clip's own, or what the main clips share, or (mixed) the clip under the playhead.
    private fun shownLook(project: Project, itemId: String?, atUs: Long): ClipLook {
        if (itemId != null) return project.clipLookOf(itemId) ?: ClipLook.NONE
        project.sharedMainLook()?.let { return it }
        val clips = project.mainLookClips()
        val under = clips.firstOrNull { atUs >= it.startUs && atUs < it.startUs + it.durationUs } ?: clips.firstOrNull()
        return project.clipLookOf(under?.id ?: return ClipLook.NONE) ?: ClipLook.NONE
    }

    // The filter the tiles show as chosen: the clip's, or the one every main clip has; none when they differ.
    private fun shownFilter(project: Project, itemId: String? = null): FilterRef? =
        if (itemId != null) project.clipLookOf(itemId)?.filter else project.sharedMainLook()?.filter

    // Every clip in the panel's scope with the look [change] gives it.
    private fun scopedUpdate(panel: LookUi, project: Project, change: (ClipLook) -> ClipLook): Project =
        panel.itemId?.let { project.updateClipLook(it, change) } ?: project.updateMainLooks(change)

    private fun moveLive(panel: LookUi, change: (ClipLook) -> ClipLook) {
        val project = host.currentProject() ?: return
        val clips = panel.itemId?.let { id -> listOfNotNull(project.keyableClip(id)) } ?: project.mainLookClips()
        for (clip in clips) host.preview?.previewLook(clip.id, change(project.clipLookOf(clip.id) ?: ClipLook.NONE))
    }

    private fun commit(panel: LookUi, edit: VideoEdit, filter: String? = null, change: (ClipLook) -> ClipLook) {
        val project = host.currentProject() ?: return
        val tools = changedLookTools(project, scopedUpdate(panel, project, change))
        if (tools.isEmpty()) return
        // The live preview already shows the final look; the step puts it in the project.
        host.commit(edit, pausesPlayback = false, filter = filter, loggedAs = tools) { scopedUpdate(panel, it, change) }
    }

    // One frame of the clip under the playhead (the clip itself from a clip bar), asked for once: the strip is not refreshed as the playhead moves.
    private fun loadFrame(project: Project, itemId: String?) {
        val atUs = host.currentPositionUs()
        val clip = frameClip(project, itemId, atUs) ?: return
        val timeUs = sourceTimeOf(clip, atUs.coerceIn(clip.startUs, clip.startUs + clip.durationUs))
        frameLoad?.cancel()
        frameLoad = host.scope.launch {
            val frame = readFrame(clip, timeUs) ?: return@launch
            val panel = host.uiState.lookPanel ?: return@launch
            host.updateUiState { it.copy(lookPanel = panel.copy(frame = frame)) }
        }
    }

    private fun frameClip(project: Project, itemId: String?, atUs: Long): MediaClip? {
        if (itemId != null) return project.keyableClip(itemId)
        val clips = project.mainLookClips()
        return clips.firstOrNull { atUs >= it.startUs && atUs < it.startUs + it.durationUs } ?: clips.firstOrNull()
    }

    private suspend fun readFrame(clip: MediaClip, timeUs: Long): LookFrame? {
        val frameReader = reader ?: startThumbnails().also { reader = it }
        val read = frameReader.frame(clip.source, timeUs, FRAME_LONG_SIDE_PX) as? Outcome.Success ?: return null
        return try {
            val image = withContext(decodeDispatcher) { read.value.jpegBytes.decodeToImageBitmap() }
            val geometry = clip.edit?.copy(adjust = ImageEdit.NONE.adjust, filter = null, deepFry = 0f)
            LookFrame("${clip.id}@$timeUs", SceneBitmap(image, PixelSize(image.width, image.height)), geometry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The engine wrote these bytes itself, so this is unexpected; the tiles stay empty squares.
            null
        }
    }

    /** Lets go of the tiles' frame reader; called whenever the panel is gone, by whatever route (Done, an undo of the opening step, a selection change). */
    fun release() {
        frameLoad?.cancel()
        reader?.close()
        reader = null
    }

    private companion object {
        /** The tiles are 72 dp; a picture this big gives a sharp tile on the densest phones and is small enough to hold. */
        const val FRAME_LONG_SIDE_PX = 512
        const val NO_FILTER = "none"
    }
}
