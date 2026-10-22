package app.memix.feature.videoeditor

import app.memix.core.domain.project.hasNoClips
import app.memix.core.domain.project.videoLengthUs
import app.memix.core.domain.video.ChoiceResult
import app.memix.core.domain.video.ExportCapabilities
import app.memix.core.domain.video.EXPORT_FRAME_RATES
import app.memix.core.domain.video.ExportChoice
import app.memix.core.domain.video.ExportResolution
import app.memix.core.domain.video.ExportSheetFactsUseCase
import app.memix.core.domain.video.ExportVideoUseCase
import app.memix.core.domain.video.GetExportCapabilitiesUseCase
import app.memix.core.domain.video.VideoCodec
import app.memix.core.domain.video.estimateExport
import app.memix.core.domain.video.exportSpaceNeededBytes
import app.memix.core.domain.video.tapFrameRate
import app.memix.core.domain.video.tapResolution
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The export use cases the video editor needs (P1-12), handed over together. */
class EditorExport(
    val capabilities: GetExportCapabilitiesUseCase,
    val facts: ExportSheetFactsUseCase,
    val export: ExportVideoUseCase,
)

/**
 * The Export sheet (spec docs/ux/specs/P1-12-export.md → Export sheet, Settings rules): the remembered resolution and
 * frame rate, stepped to what this phone can do for the canvas, the facts line's estimate, and the notices. Export
 * video starts the export and hands over to the Exporting screen. Nothing here changes the draft.
 */
internal class ExportSheetControl(private val host: EditorHost, private val export: EditorExport) {
    private var capabilities = ExportCapabilities.Unknown
    private var refreshing: Job? = null

    /** Warms the capability check when the editor opens, so the sheet is ready on its first tap (spec → Not ready yet). */
    fun warmUp(project: Project) {
        host.scope.launch { capabilities = export.capabilities(canvasOf(project)) }
    }

    /** The top bar's Export: typing commits first (the caller), reorder mode and an empty draft explain. */
    fun open() {
        val project = host.currentProject() ?: return
        if (host.uiState.reorder != null) return host.showToast(ToastMessage.Explain(Explanation.CANVAS_REORDER))
        if (project.hasNoClips()) return host.showToast(ToastMessage.Explain(Explanation.EXPORT_EMPTY))
        host.preview?.pause()
        host.updateUiState { it.copy(exportSheet = ExportSheetUi(ExportSheetFactsUseCase.DEFAULT_CHOICE)) }
        refreshing?.cancel()
        refreshing = host.scope.launch {
            val last = export.facts.lastChoice()
            capabilities = export.capabilities(canvasOf(project))
            val shown = capabilities.nearestAtOrBelow(last)
            val lowered = shown != null && shown != last && capabilities != ExportCapabilities.Unknown
            show(shown ?: last, loweredTo = if (lowered) shown else null, nothingPossible = shown == null)
        }
    }

    fun close() {
        refreshing?.cancel()
        host.updateUiState { it.copy(exportSheet = null) }
    }

    /** Back from the storage screen or another app: the free space may have changed. */
    fun recheckSpace() {
        val sheet = host.uiState.exportSheet ?: return
        host.scope.launch { show(sheet.choice, sheet.loweredTo, sheet.nothingPossible) }
    }

    fun tapResolution(index: Int) {
        val sheet = host.uiState.exportSheet ?: return
        val resolution = ExportResolution.entries.getOrNull(index) ?: return
        apply(capabilities.tapResolution(sheet.choice, resolution), ToastMessage.ExportCantDo(resolution.label, frameRate = null), resolution)
    }

    fun tapFrameRate(index: Int) {
        val sheet = host.uiState.exportSheet ?: return
        val frameRate = EXPORT_FRAME_RATES.getOrNull(index) ?: return
        apply(capabilities.tapFrameRate(sheet.choice, frameRate), ToastMessage.ExportCantDo(resolutionLabel = null, frameRate), tappedResolution = null)
    }

    /** Export video: explains when it can't start; else starts the export and opens the Exporting screen. */
    fun exportVideo() {
        val sheet = host.uiState.exportSheet ?: return
        val project = host.currentProject() ?: return
        if (sheet.nothingPossible) return host.showToast(ToastMessage.Explain(Explanation.EXPORT_NO_SHAPE))
        if (sheet.spaceShortBytes != null) return host.showToast(ToastMessage.Explain(Explanation.EXPORT_NO_SPACE))
        refreshing?.cancel()
        // A refused start still opens the Exporting screen, which shows "Android stopped the export".
        export.export.start(project, sheet.choice)
        host.updateUiState { it.copy(exportSheet = null, openExportScreen = true) }
    }

    // A tap's result (spec → What the phone can do, the table): selected, maybe with the other setting stepped down, or
    // an explanation that keeps the selection.
    private fun apply(result: ChoiceResult, phoneCant: ToastMessage, tappedResolution: ExportResolution?) {
        when (result) {
            is ChoiceResult.Selected -> {
                if (result.steppedDown) {
                    val toast = if (tappedResolution != null) {
                        ToastMessage.ExportFrameRateLowered(result.choice.resolution.label, result.choice.frameRate)
                    } else {
                        ToastMessage.ExportResolutionLowered(result.choice.frameRate, result.choice.resolution.label)
                    }
                    host.showToast(toast)
                }
                // Any pick ends the "lowered" notice (spec → Remembered setting not possible here).
                host.scope.launch { show(result.choice, loweredTo = null, nothingPossible = false) }
            }
            ChoiceResult.PhoneCant -> host.showToast(phoneCant)
            is ChoiceResult.ShapeCant ->
                host.showToast(ToastMessage.ExportShapeCantDo(tappedResolution?.label.orEmpty(), result.highest?.label.orEmpty()))
        }
    }

    // The facts line and notices for [choice]: the estimate, and the space it needs against what's free.
    private suspend fun show(choice: ExportChoice, loweredTo: ExportChoice?, nothingPossible: Boolean) {
        val project = host.currentProject() ?: return
        val option = capabilities.optionFor(choice)
        val estimate = option?.let { estimateExport(it, choice.resolution, project.videoLengthUs(), export.facts.secondsPerMegapixelSecond(choice.resolution)) }
        val shortBytes = estimate?.let {
            val needed = exportSpaceNeededBytes(it, capabilities.writesStraightToGallery)
            (needed - export.facts.freeBytes()).takeIf { missing -> missing > 0 }?.let(::roundedUpToTenMegabytes)
        }
        host.updateUiState { state ->
            if (state.exportSheet == null) return@updateUiState state
            state.copy(
                exportSheet = ExportSheetUi(
                    choice = choice,
                    codec = option?.codec ?: VideoCodec.H264,
                    estimate = estimate,
                    loweredTo = loweredTo,
                    spaceShortBytes = shortBytes,
                    nothingPossible = nothingPossible,
                ),
            )
        }
    }

    private fun canvasOf(project: Project) = PixelSize(project.canvas.widthPx, project.canvas.heightPx)

    // "Free up about 240 MB": needed minus free, rounded up to the next 10 MB (spec → Free space).
    private fun roundedUpToTenMegabytes(bytes: Long): Long = (bytes + TEN_MEGABYTES - 1) / TEN_MEGABYTES * TEN_MEGABYTES

    private companion object {
        const val TEN_MEGABYTES = 10_000_000L
    }
}
