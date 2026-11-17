package app.memix.feature.videoeditor

import app.memix.core.designsystem.MemixMotion
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.addEffect
import app.memix.core.domain.project.changeEffect
import app.memix.core.domain.project.effectItem
import app.memix.core.domain.project.mainLookClips
import app.memix.core.domain.project.setEffectIntensity
import app.memix.core.domain.project.sourceTimeOf
import app.memix.core.domain.video.StartThumbnailsUseCase
import app.memix.core.domain.video.ThumbnailReader
import app.memix.core.model.project.EffectCatalog
import app.memix.core.model.project.EffectTab
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.ui.scene.SceneBitmap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The Effects tool (spec docs/ux/specs/P4-06-video-effects.md): the sheet that adds or replaces an effect, and the Intensity panel of the selected effect
 * item. A tile tap, a Change effect and an Intensity release are one undo step each ("Add effect", "Change effect", "Intensity"); the slider moves the
 * picture live ([app.memix.core.domain.video.PreviewSession.previewEffectIntensity]) and changes the project on release. The three-at-once rule is
 * checked where every edit commits (the ViewModel), which says why a fourth is refused.
 *
 * The sheet's tiles are made from one frame of the clip under the playhead, read from the engine's reader when the sheet opens and held until it closes.
 */
internal class EffectControl(
    private val host: EditorHost,
    private val startThumbnails: StartThumbnailsUseCase,
    private val decodeDispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private var reader: ThumbnailReader? = null
    private var frameLoad: Job? = null

    // The last tab, kept for the visit: the sheet opens on it again (spec → The Effects sheet).
    private var lastTab = EffectCatalog.shownTabs.first()

    /** The main bar's Effects: the sheet opens to add an effect at the playhead. */
    fun openSheet() {
        val project = host.currentProject() ?: return
        if (project.mainLookClips().isEmpty()) return
        show(project, replaceItemId = null)
    }

    /** Change effect on the selected effect item: the same sheet, in replace mode. */
    fun openChange() {
        val itemId = host.uiState.selectedItemId ?: return
        val project = host.currentProject() ?: return
        project.effectItem(itemId) ?: return
        show(project, replaceItemId = itemId)
    }

    fun selectTab(tab: EffectTab) {
        val sheet = host.uiState.effectsSheet ?: return
        lastTab = tab
        host.updateUiState { it.copy(effectsSheet = sheet.copy(tab = tab)) }
    }

    /** Close, the scrim, back or Escape: nothing changes; focus goes back to the tool that opened it. */
    fun closeSheet() {
        val sheet = host.uiState.effectsSheet ?: return
        release()
        host.updateUiState { it.copy(effectsSheet = null) }
        host.returnFocusTo(if (sheet.replaceItemId == null) FocusTarget.EFFECTS_TOOL else FocusTarget.CHANGE_EFFECT_TOOL)
    }

    /** A tile tap: lands the effect (or replaces the selected item's), closes the sheet, and says what happened. */
    fun pick(effectId: String) {
        val sheet = host.uiState.effectsSheet ?: return
        if (sheet.replaceItemId != null) return replace(sheet.replaceItemId, sheet.currentEffectId, effectId)
        val itemId = host.newId()
        val atUs = host.currentPositionUs()
        val landed = host.commit(VideoEdit.EFFECT_ADD, value = effectId) { it.addEffect(effectId, atUs, itemId, newTrackId = host.newId()) }
        dismissAfterTap()
        if (landed) land(itemId)
    }

    private fun replace(itemId: String, currentId: String?, effectId: String) {
        // The current effect's tile does nothing.
        if (effectId == currentId) return
        host.commit(VideoEdit.EFFECT_CHANGE, value = effectId) { it.changeEffect(itemId, effectId) }
        dismissAfterTap()
    }

    // The sheet slides away (200 ms) before the item bonks, so the user sees it land.
    private fun land(itemId: String) {
        host.scope.launch {
            delay(MemixMotion.durationSheet.milliseconds)
            if (host.uiState.timeline.item(itemId) == null) return@launch
            val landing = host.newLanding(persistentSetOf(itemId))
            host.updateUiState { it.withSelection(itemId, host.currentProject()).copy(landed = landing, revealed = landing) }
            host.showToast(ToastMessage.EffectAdded, spokenOnly = true)
        }
    }

    private fun dismissAfterTap() {
        release()
        host.updateUiState { it.copy(effectsSheet = null) }
    }

    /** Intensity on the selected effect item: the panel opens; the playhead jumps to the item's start when it is outside it (spec → The Intensity panel). */
    fun openPanel() {
        val itemId = host.uiState.selectedItemId ?: return
        val item = host.currentProject()?.effectItem(itemId) ?: return
        val atUs = host.currentPositionUs()
        if (atUs < item.startUs || atUs >= item.startUs + item.durationUs) host.preview?.seekTo(item.startUs)
        host.updateUiState { it.copy(effectPanel = EffectPanelUi(itemId, item.effectId, item.intensity)) }
    }

    fun closePanel() {
        if (host.uiState.effectPanel == null) return
        host.updateUiState { it.copy(effectPanel = null) }
        host.returnFocusTo(FocusTarget.INTENSITY_TOOL)
    }

    /** The slider moved: the picture follows, nothing is committed yet. */
    fun intensityChanged(intensity: Float) {
        val panel = host.uiState.effectPanel ?: return
        val clamped = intensity.coerceIn(0f, 1f)
        host.preview?.previewEffectIntensity(panel.itemId, clamped)
        host.updateUiState { it.copy(effectPanel = panel.copy(intensity = clamped)) }
    }

    /** The slider was let go (or stepped): one "Intensity" step, none if it is where it began. */
    fun intensityReleased() {
        val panel = host.uiState.effectPanel ?: return
        host.commit(VideoEdit.EFFECT_INTENSITY, pausesPlayback = false) { it.setEffectIntensity(panel.itemId, panel.intensity) }
    }

    /** The project changed (an edit, undo or redo): the panel follows its item, and closes when the item is gone. */
    fun syncedWith(panel: EffectPanelUi?, project: Project): EffectPanelUi? {
        val shown = panel ?: return null
        val item = project.effectItem(shown.itemId) ?: return null
        return shown.copy(effectId = item.effectId, intensity = item.intensity)
    }

    /** The sheet in replace mode follows its item: it closes with it, and rings the effect the item has now. */
    fun sheetSyncedWith(sheet: EffectsSheetUi?, project: Project): EffectsSheetUi? {
        val shown = sheet ?: return null
        val itemId = shown.replaceItemId ?: return shown
        val item = project.effectItem(itemId)
        if (item == null) {
            release()
            return null
        }
        return shown.copy(currentEffectId = item.effectId)
    }

    override fun close() = release()

    private fun show(project: Project, replaceItemId: String?) {
        val current = replaceItemId?.let { project.effectItem(it)?.effectId }
        val tab = current?.let { EffectCatalog.of(it)?.tab }?.takeIf { it in EffectCatalog.shownTabs } ?: lastTab
        host.updateUiState { it.copy(effectsSheet = EffectsSheetUi(tab, replaceItemId, current)) }
        loadFrame(project)
    }

    // One frame of the clip under the playhead (the first clip's first frame when the playhead is past the clips), asked for once.
    private fun loadFrame(project: Project) {
        val atUs = host.currentPositionUs()
        val clips = project.mainLookClips()
        val clip = clips.firstOrNull { atUs >= it.startUs && atUs < it.startUs + it.durationUs } ?: clips.firstOrNull() ?: return
        val timeUs = sourceTimeOf(clip, atUs.coerceIn(clip.startUs, clip.startUs + clip.durationUs))
        frameLoad?.cancel()
        frameLoad = host.scope.launch {
            val frame = readFrame(clip, timeUs) ?: return@launch
            val sheet = host.uiState.effectsSheet ?: return@launch
            host.updateUiState { it.copy(effectsSheet = sheet.copy(frame = frame)) }
        }
    }

    private suspend fun readFrame(clip: MediaClip, timeUs: Long): EffectFrame? {
        val frameReader = reader ?: startThumbnails().also { reader = it }
        val read = frameReader.frame(clip.source, timeUs, FRAME_LONG_SIDE_PX) as? Outcome.Success ?: return null
        return try {
            val image = withContext(decodeDispatcher) { read.value.jpegBytes.decodeToImageBitmap() }
            EffectFrame("${clip.id}@$timeUs", SceneBitmap(image, PixelSize(image.width, image.height)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The engine wrote these bytes itself, so this is unexpected; the tiles stay plain squares.
            null
        }
    }

    // Lets go of the reader whenever the sheet is gone, by whatever route.
    private fun release() {
        frameLoad?.cancel()
        reader?.close()
        reader = null
    }

    private companion object {
        /** The tiles are 104 dp wide; a frame this big gives a sharp tile on the densest phones and is small enough to hold. */
        const val FRAME_LONG_SIDE_PX = 512
    }
}
