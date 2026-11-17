package app.memix.feature.videoeditor

import app.memix.core.domain.Outcome
import app.memix.core.domain.project.cutAfter
import app.memix.core.domain.project.mainMediaClip
import app.memix.core.domain.project.maxTransitionDurationUs
import app.memix.core.domain.project.nextMainClip
import app.memix.core.domain.project.removeTransition
import app.memix.core.domain.project.setTransition
import app.memix.core.domain.project.setTransitionDuration
import app.memix.core.domain.project.transitionBlock
import app.memix.core.domain.project.videoLengthUs
import app.memix.core.domain.project.TransitionBlock
import app.memix.core.domain.video.StartThumbnailsUseCase
import app.memix.core.domain.video.ThumbnailReader
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.TransitionCatalog
import app.memix.core.ui.scene.SceneBitmap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The Transition tool of a main clip (spec docs/ux/specs/P4-08-transitions.md): the panel that sets how the clip leads into the next one. A tile tap,
 * a Duration release and None are one undo step each ("Transition", "Transition length", "Remove transition"); every one of them plays the cut once.
 * What fits, and what a length or order edit does to a transition, is decided in `:core:domain` (`TransitionEdits`): this only asks.
 *
 * The tiles are made from the last frame of the clip and the first of the next one, read from the engine's reader when the panel opens and held
 * until it closes.
 */
internal class TransitionControl(
    private val host: EditorHost,
    private val startThumbnails: StartThumbnailsUseCase,
    private val decodeDispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private var reader: ThumbnailReader? = null
    private var frameLoad: Job? = null
    private var cutPlay: Job? = null

    /** The Transition tool of the selected main clip: the panel opens with the playhead on the cut, or the tool says why it can't. */
    fun open() {
        val clipId = host.uiState.selectedItemId ?: return
        val project = host.currentProject() ?: return
        val clip = project.mainMediaClip(clipId) ?: return
        when (project.transitionBlock(clipId)) {
            TransitionBlock.NO_NEXT_CLIP -> return host.showToast(ToastMessage.Explain(Explanation.TRANSITION_LAST))
            TransitionBlock.TOO_SHORT -> return host.showToast(ToastMessage.Explain(Explanation.TRANSITION_SHORT))
            null -> Unit
        }
        host.preview?.pause()
        project.cutAfter(clipId)?.let { host.preview?.seekTo(it) }
        host.updateUiState { it.copy(transitionPanel = panelOf(project, clip)) }
        loadFrames(project, clip)
    }

    /** Done, system back or Escape: the panel and the tiles go, the transition stays; focus goes back to the tool. */
    fun closePanel() {
        if (host.uiState.transitionPanel == null) return
        release()
        host.updateUiState { it.copy(transitionPanel = null) }
        host.returnFocusTo(FocusTarget.TRANSITION_TOOL)
    }

    /** A tile (null is None): one step, then the cut plays once; the tile that is already chosen only replays it. */
    fun pick(id: String?) {
        val panel = host.uiState.transitionPanel ?: return
        if (id == panel.transitionId) return playCut(panel.clipId)
        val changed = if (id == null) {
            host.commit(VideoEdit.TRANSITION_REMOVE) { it.removeTransition(panel.clipId) }
        } else {
            host.commit(VideoEdit.TRANSITION, value = id) { it.setTransition(panel.clipId, id) }
        }
        if (changed && id != null) playCut(panel.clipId)
        if (!changed && id != null) explainIfTooFlashy()
    }

    // Nothing changed though the tile or length differs, so the flash-rate rule (`:core:domain`) refused it; the same toast as the effects says why.
    private fun explainIfTooFlashy() {
        host.showToast(ToastMessage.Explain(Explanation.EFFECT_FLASH))
    }

    /** The Duration slider moved: the readout follows and the thumb stops at the longest that fits; nothing is committed yet. */
    fun durationChanged(durationUs: Long) {
        val panel = host.uiState.transitionPanel ?: return
        val step = TransitionCatalog.DURATION_STEP_US
        val clamped = (((durationUs + step / 2) / step) * step).coerceIn(TransitionCatalog.MIN_DURATION_US, panel.maxDurationUs)
        host.updateUiState { it.copy(transitionPanel = panel.copy(durationUs = clamped)) }
    }

    /** The slider was let go (or stepped): one "Transition length" step, none if it is where it began; the cut plays once. */
    fun durationReleased() {
        val panel = host.uiState.transitionPanel ?: return
        if (panel.transitionId == null) return
        val changed = host.commit(VideoEdit.TRANSITION_DURATION) { it.setTransitionDuration(panel.clipId, panel.durationUs) }
        if (changed) playCut(panel.clipId) else if (panel.durationUs != host.currentProject()?.mainMediaClip(panel.clipId)?.transitionOut?.durationUs) explainIfTooFlashy()
    }

    /** The project changed (an edit, undo or redo): the panel follows its clip's cut, and closes when the clip or its next clip is gone. */
    fun syncedWith(panel: TransitionPanelUi?, project: Project): TransitionPanelUi? {
        val shown = panel ?: return null
        val clip = project.mainMediaClip(shown.clipId) ?: return null
        if (project.nextMainClip(shown.clipId) == null || project.maxTransitionDurationUs(shown.clipId) == 0L) return null
        return panelOf(project, clip).copy(frames = shown.frames)
    }

    override fun close(): Unit = release()

    private fun panelOf(project: Project, clip: MediaClip): TransitionPanelUi {
        val transition = clip.transitionOut?.takeIf { TransitionCatalog.isBuilt(it.id) }
        return TransitionPanelUi(
            clipId = clip.id,
            transitionId = transition?.id,
            durationUs = transition?.durationUs ?: TransitionCatalog.DEFAULT_DURATION_US,
            maxDurationUs = project.maxTransitionDurationUs(clip.id),
        )
    }

    // From 0.8 s before the window to 0.4 s after it, then pause; a stage tap (pause) or the limit ends it sooner (spec → The Transition panel).
    private fun playCut(clipId: String) {
        val session = host.preview ?: return
        val project = host.currentProject() ?: return
        val cutUs = project.cutAfter(clipId) ?: return
        val durationUs = project.mainMediaClip(clipId)?.transitionOut?.durationUs ?: return
        val lengthUs = project.videoLengthUs()
        val fromUs = (cutUs - durationUs / 2 - TransitionCatalog.PLAY_LEAD_US).coerceAtLeast(0L)
        val toUs = (cutUs + durationUs / 2 + TransitionCatalog.PLAY_TRAIL_US).coerceAtMost(maxOf(lengthUs, cutUs))
        cutPlay?.cancel()
        cutPlay = host.scope.launch {
            // The preview builds the new composition first (spec P4-08 → States, "Preview catching up"); the cut plays on it.
            // The rebuild starts a moment after the commit: wait for it to show (a replay has none), then for it to end.
            val waiting = TimeSource.Monotonic.markNow()
            while (isActive && !session.playback.value.isCatchingUp && waiting.elapsedNow() < REBUILD_START_LIMIT) delay(POLL)
            while (isActive && session.playback.value.isCatchingUp && waiting.elapsedNow() < REBUILD_LIMIT) delay(POLL)
            session.seekTo(fromUs)
            session.play()
            val started = TimeSource.Monotonic.markNow()
            while (isActive && session.positionUs.value < toUs && started.elapsedNow() < PLAY_LIMIT) {
                delay(POLL)
                // Playback takes a moment to start; after that, not playing means the user stopped it.
                if (started.elapsedNow() > GRACE && !session.playback.value.isPlaying) return@launch
            }
            session.pause()
        }
    }

    // One frame of each clip at the cut, asked for once.
    private fun loadFrames(project: Project, clip: MediaClip) {
        val next = project.nextMainClip(clip.id) ?: return
        frameLoad?.cancel()
        frameLoad = host.scope.launch {
            val outgoing = readFrame(clip, lastFrameUs(clip)) ?: return@launch
            val incoming = readFrame(next, firstFrameUs(next)) ?: return@launch
            val panel = host.uiState.transitionPanel ?: return@launch
            if (panel.clipId != clip.id) return@launch
            host.updateUiState { it.copy(transitionPanel = panel.copy(frames = TransitionFrames("${clip.id}>${next.id}@${clip.trimOutUs}", outgoing, incoming))) }
        }
    }

    private fun lastFrameUs(clip: MediaClip): Long = if (clip.source.kind == MediaKind.IMAGE) 0L else (clip.trimOutUs - FRAME_US).coerceAtLeast(clip.trimInUs)

    private fun firstFrameUs(clip: MediaClip): Long = if (clip.source.kind == MediaKind.IMAGE) 0L else clip.trimInUs

    private suspend fun readFrame(clip: MediaClip, timeUs: Long): SceneBitmap? {
        val frameReader = reader ?: startThumbnails().also { reader = it }
        val read = frameReader.frame(clip.source, timeUs, FRAME_LONG_SIDE_PX) as? Outcome.Success ?: return null
        return try {
            val image = withContext(decodeDispatcher) { read.value.jpegBytes.decodeToImageBitmap() }
            SceneBitmap(image, PixelSize(image.width, image.height))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The engine wrote these bytes itself, so this is unexpected; the tiles stay plain squares.
            null
        }
    }

    // Lets go of the reader and stops the cut's play, whenever the panel is gone, by whatever route.
    private fun release() {
        frameLoad?.cancel()
        cutPlay?.cancel()
        reader?.close()
        reader = null
    }

    private companion object {
        /** The tiles are 72 dp wide; a frame this big gives a sharp tile at 3x and is small enough to hold. */
        const val FRAME_LONG_SIDE_PX = 384
        const val FRAME_US = 33_333L
        val PLAY_LIMIT = 6_000.milliseconds
        val REBUILD_START_LIMIT = 400.milliseconds
        val REBUILD_LIMIT = 3_000.milliseconds
        val GRACE = 300.milliseconds
        val POLL = 16.milliseconds
    }
}
