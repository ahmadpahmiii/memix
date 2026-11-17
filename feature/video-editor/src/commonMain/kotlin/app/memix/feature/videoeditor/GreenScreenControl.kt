package app.memix.feature.videoeditor

import androidx.compose.ui.graphics.ImageBitmap
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.ChromaKeyMath
import app.memix.core.domain.project.chromaKeyOf
import app.memix.core.domain.project.keyableClip
import app.memix.core.domain.project.keyedAtDefaults
import app.memix.core.domain.project.picturePointAt
import app.memix.core.domain.project.setClipChromaKey
import app.memix.core.domain.project.sourceTimeOf
import app.memix.core.domain.video.StartThumbnailsUseCase
import app.memix.core.domain.video.ThumbnailReader
import app.memix.core.model.project.ChromaKey
import app.memix.core.model.project.ChromaKeyCatalog
import app.memix.core.model.project.MediaClip
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The Green screen panel and its eyedropper (spec P4-04). Opening it on an unkeyed clip turns keying on at the defaults as one undo
 * step; a swatch, a pick, a slider release and Turn off are one step each. The sliders move the matte live
 * ([app.memix.core.domain.video.PreviewSession.previewChromaKey]); the project changes on release.
 *
 * The pick and the "nothing found" banner read the clip's own picture, not the preview: one frame at the playhead, asked for from
 * the engine's reader and held as pixels ([SourceFrame]), then read with the same arithmetic the shader draws with
 * ([ChromaKeyMath]). The reader is opened on first use and closed with the editor.
 */
internal class GreenScreenControl(
    private val host: EditorHost,
    private val startThumbnails: StartThumbnailsUseCase,
    private val decodeDispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private var reader: ThumbnailReader? = null
    private var frame: SourceFrame? = null
    private var frameLoad: Job? = null
    private var nothingCheck: Job? = null
    private var hintTimer: Job? = null

    /** The Green screen tool: keying on at the defaults when it isn't on, the panel open, playback left as it was. */
    fun open() {
        val itemId = host.uiState.selectedItemId ?: return
        val clip = host.currentProject()?.keyableClip(itemId) ?: return
        showAtPlayhead(clip)
        host.commit(VideoEdit.GREEN_SCREEN, pausesPlayback = false) { it.keyedAtDefaults(itemId) }
        val key = host.currentProject()?.chromaKeyOf(itemId) ?: return
        host.updateUiState { it.copy(greenScreen = GreenScreenUi(itemId, key)) }
        checkNothingFound(OPEN_CHECK_DELAY_MS)
    }

    /** Done, back or Escape: pick mode ends first, then the panel closes; focus goes back to the tool. */
    fun closePanel() {
        val panel = host.uiState.greenScreen ?: return
        if (panel.picking) return endPick()
        stopWork()
        releaseFrame()
        host.updateUiState { it.copy(greenScreen = null) }
        host.returnFocusTo(FocusTarget.GREEN_SCREEN_TOOL)
    }

    /** Turn off: keying is removed as one step and the panel closes (the project sync closes it; a pick in flight ends with it). */
    fun turnOff() {
        val panel = host.uiState.greenScreen ?: return
        stopWork()
        releaseFrame()
        if (host.commit(VideoEdit.GREEN_SCREEN_OFF, pausesPlayback = false) { it.setClipChromaKey(panel.itemId, null) }) {
            host.updateUiState { it.copy(greenScreen = null) }
            host.returnFocusTo(FocusTarget.GREEN_SCREEN_TOOL)
        }
    }

    /** A Green or Blue swatch: the color changes, strength and softness stay. */
    fun setColor(argb: Int) {
        val panel = host.uiState.greenScreen ?: return
        val key = ChromaKeyCatalog.keyOf(argb, panel.key.strength, panel.key.softness)
        commitKey(panel, key)
    }

    fun strengthChanged(strength: Float) = moveKey { it.copy(strength = strength.coerceIn(0f, 1f)) }

    fun softnessChanged(softness: Float) = moveKey { it.copy(softness = softness.coerceIn(0f, 1f)) }

    /** The slider's last change is already in the panel, whichever way it came (drag, tap, key, TalkBack). */
    fun released() {
        val panel = host.uiState.greenScreen ?: return
        commitKey(panel, panel.key, checksNothing = false)
    }

    /** The eyedropper swatch: starts the mode (playback pauses, the frame is asked for), or ends it. */
    fun togglePick() {
        val panel = host.uiState.greenScreen ?: return
        if (panel.picking) return endPick()
        host.preview?.pause()
        hintTimer?.cancel()
        host.updateUiState { it.copy(greenScreen = panel.copy(picking = true, colorAt = pickColor, hint = GreenScreenHint.PICK)) }
        host.currentProject()?.keyableClip(panel.itemId)?.let(::loadFrame)
    }

    /** The finger was lifted: the color under it becomes the key (one step) and the mode ends, or the banner says it missed. */
    fun pickReleased(canvasFx: Float, canvasFy: Float) {
        val panel = host.uiState.greenScreen?.takeIf { it.picking } ?: return
        // The frame is still being read: nothing can be said about the color yet, so the mode simply stays.
        if (frame == null) return
        val argb = colorAt(panel.itemId, canvasFx, canvasFy)
        if (argb == null) {
            host.updateUiState { it.copy(greenScreen = panel.copy(hint = GreenScreenHint.PICK_MISS)) }
            showPickHintAfterMiss()
            return
        }
        host.updateUiState { it.copy(greenScreen = panel.copy(picking = false, hint = null)) }
        commitKey(panel.copy(picking = false), ChromaKeyCatalog.keyOf(argb, panel.key.strength, panel.key.softness))
        host.showToast(ToastMessage.GreenScreenPicked, spokenOnly = true)
    }

    // Handed to the pick layer once, so it reads colors under the finger itself and the editor doesn't recompose at pointer rate.
    private val pickColor: (Float, Float) -> Int? = { fx, fy -> host.uiState.greenScreen?.let { colorAt(it.itemId, fx, fy) } }

    /** The project changed (an edit, undo or redo): the panel shows the clip's key, and closes when keying is off or the clip is gone. */
    fun syncedWith(panel: GreenScreenUi?, project: app.memix.core.model.project.Project): GreenScreenUi? {
        val shown = panel ?: return null
        val key = project.chromaKeyOf(shown.itemId) ?: return null
        return if (shown.key == key) shown else shown.copy(key = key)
    }

    override fun close() {
        stopWork()
        releaseFrame()
    }

    // The picture and the reader's open file go with the panel; the next use reads them again.
    private fun releaseFrame() {
        reader?.close()
        reader = null
        frame = null
    }

    private fun moveKey(change: (ChromaKey) -> ChromaKey) {
        val panel = host.uiState.greenScreen ?: return
        val key = change(panel.key)
        if (key == panel.key) return
        host.preview?.previewChromaKey(panel.itemId, key)
        host.updateUiState { it.copy(greenScreen = panel.copy(key = key)) }
    }

    private fun commitKey(panel: GreenScreenUi, key: ChromaKey, checksNothing: Boolean = true) {
        val recorded = host.commit(VideoEdit.GREEN_SCREEN, pausesPlayback = false) { it.setClipChromaKey(panel.itemId, key) }
        if (recorded && checksNothing) checkNothingFound(0L)
    }

    private fun endPick() {
        val panel = host.uiState.greenScreen ?: return
        hintTimer?.cancel()
        host.updateUiState { it.copy(greenScreen = panel.copy(picking = false, hint = null)) }
    }

    // A miss says so for 2 s, then the mode's own hint is back.
    private fun showPickHintAfterMiss() {
        hintTimer?.cancel()
        hintTimer = host.scope.launch {
            delay(MISS_BANNER_MS)
            val panel = host.uiState.greenScreen?.takeIf { it.picking } ?: return@launch
            host.updateUiState { it.copy(greenScreen = panel.copy(hint = GreenScreenHint.PICK)) }
        }
    }

    private fun colorAt(itemId: String, canvasFx: Float, canvasFy: Float): Int? {
        val project = host.currentProject() ?: return null
        val held = frame ?: return null
        val point = project.picturePointAt(itemId, host.currentPositionUs(), canvasFx, canvasFy) ?: return null
        return held.colorAt(point.u, point.v)
    }

    // The frame at the playhead, once; the pick pauses playback, so it is the picture the user sees.
    private fun loadFrame(clip: MediaClip) {
        val timeUs = sourceTimeOf(clip, host.currentPositionUs())
        if (frame?.matches(clip, timeUs) == true) return
        frameLoad?.cancel()
        frameLoad = host.scope.launch { frame = readFrame(clip, timeUs) }
    }

    private suspend fun readFrame(clip: MediaClip, timeUs: Long): SourceFrame? {
        val frameReader = reader ?: startThumbnails().also { reader = it }
        val read = frameReader.frame(clip.source, timeUs, FRAME_LONG_SIDE_PX) as? Outcome.Success ?: return null
        return try {
            withContext(decodeDispatcher) { SourceFrame.of(clip, timeUs, read.value.jpegBytes.decodeToImageBitmap()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The engine wrote these bytes itself, so this is unexpected; the pick finds no color.
            null
        }
    }

    // After the panel opens or a color changes: when less than 1% of the picture is keyed, the banner says so for 4 s (spec → States).
    private fun checkNothingFound(afterMs: Long) {
        nothingCheck?.cancel()
        nothingCheck = host.scope.launch {
            delay(afterMs)
            val panel = host.uiState.greenScreen ?: return@launch
            val clip = host.currentProject()?.keyableClip(panel.itemId) ?: return@launch
            val timeUs = sourceTimeOf(clip, host.currentPositionUs())
            val held = frame?.takeIf { it.matches(clip, timeUs) } ?: readFrame(clip, timeUs)?.also { frame = it } ?: return@launch
            val key = host.currentProject()?.chromaKeyOf(panel.itemId) ?: return@launch
            val share = withContext(decodeDispatcher) { ChromaKeyMath.removedShare(held.pixels, held.width, held.height, key) }
            val shown = host.uiState.greenScreen ?: return@launch
            if (share >= ChromaKeyCatalog.NOTHING_FOUND_SHARE || shown.picking) return@launch
            host.updateUiState { it.copy(greenScreen = shown.copy(hint = GreenScreenHint.NOTHING)) }
            delay(NOTHING_BANNER_MS)
            val after = host.uiState.greenScreen ?: return@launch
            if (after.hint == GreenScreenHint.NOTHING) host.updateUiState { it.copy(greenScreen = after.copy(hint = null)) }
        }
    }

    // Opening with the playhead outside the clip moves it to the clip's start, so the matte can be seen (the Opacity panel's rule).
    private fun showAtPlayhead(clip: MediaClip) {
        val atUs = host.currentPositionUs()
        if (atUs >= clip.startUs && atUs < clip.startUs + clip.durationUs) return
        host.preview?.seekTo(clip.startUs)
    }

    private fun stopWork() {
        frameLoad?.cancel()
        nothingCheck?.cancel()
        hintTimer?.cancel()
    }

    /** One decoded picture of a clip, in its own shape: what the pick and the "nothing found" test read. */
    private class SourceFrame(val itemId: String, val timeUs: Long, val width: Int, val height: Int, val pixels: IntArray) {
        fun matches(clip: MediaClip, atUs: Long): Boolean = itemId == clip.id && timeUs == atUs

        /** The average of the 9 x 9 pixels at the picture point ([u], [v]) (spec → What is sampled), opaque. */
        fun colorAt(u: Float, v: Float): Int? {
            val x = (u * width).toInt().coerceIn(0, width - 1)
            val y = (v * height).toInt().coerceIn(0, height - 1)
            return ChromaKeyMath.averageColor(pixels, width, height, x, y)
        }

        companion object {
            fun of(clip: MediaClip, timeUs: Long, image: ImageBitmap): SourceFrame {
                val pixels = IntArray(image.width * image.height)
                image.readPixels(pixels)
                return SourceFrame(clip.id, timeUs, image.width, image.height, pixels)
            }
        }
    }

    private companion object {
        /** The frame the pick reads: enough pixels that a 9 x 9 window is a small part of the picture, few enough to hold as an IntArray. */
        const val FRAME_LONG_SIDE_PX = 960
        const val OPEN_CHECK_DELAY_MS = 600L
        const val MISS_BANNER_MS = 2_000L
        const val NOTHING_BANNER_MS = 4_000L
    }
}
