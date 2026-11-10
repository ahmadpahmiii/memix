package app.memix.feature.photoeditor

import app.memix.core.domain.photo.ExportPhotoUseCase
import app.memix.core.domain.photo.PhotoExportAction
import app.memix.core.domain.photo.PhotoExportSettings
import app.memix.core.domain.photo.PhotoExportSheetFactsUseCase
import app.memix.core.domain.photo.PhotoExportState
import app.memix.core.domain.photo.estimatePhotoBytes
import app.memix.core.domain.photo.hasPhotoContent
import app.memix.core.domain.photo.photoCopySpaceNeededBytes
import app.memix.core.domain.photo.photoSaveSpaceNeededBytes
import app.memix.core.model.project.PhotoExportCatalog
import app.memix.core.model.project.PhotoExportFormat
import app.memix.core.model.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the export control needs from the editor: the draft, the state, a toast, and the typing commit. */
internal interface PhotoExportHost {
    fun currentProject(): Project?

    fun updateUiState(transform: (PhotoEditorUiState) -> PhotoEditorUiState)

    val uiState: PhotoEditorUiState

    fun showToast(message: PhotoToastMessage, spokenOnly: Boolean = false)

    /** Commits the words being typed as a step, so the export has them. */
    fun commitTyping()
}

/**
 * The Export sheet's behavior (spec docs/ux/specs/P2-08-photo-export.md): opens only when there is something to export, keeps the
 * remembered format, quality and size, shows the size guess and the free-space notice, and starts Save or Copy in the app-scope
 * [ExportPhotoUseCase]. It follows that use case's state, so a rotation or leaving Memix mid-work loses nothing.
 */
internal class PhotoExportControl(
    private val scope: CoroutineScope,
    private val export: ExportPhotoUseCase,
    private val facts: PhotoExportSheetFactsUseCase,
    private val host: PhotoExportHost,
) {
    private var freeBytes = Long.MAX_VALUE
    private var refreshing: Job? = null

    /** Follows the use case for the life of the editor. */
    fun follow() {
        scope.launch { export.state.collect(::show) }
    }

    /** The top bar's Export: typing commits first; an empty canvas explains. */
    fun open() {
        if (host.uiState.textEntry != null) host.commitTyping()
        val project = host.currentProject() ?: return
        if (isCuttingOut(host.uiState)) return host.showToast(PhotoToastMessage.Explain(PhotoExplanation.EXPORT_BUSY))
        if (!project.hasPhotoContent()) return host.showToast(PhotoToastMessage.Explain(PhotoExplanation.EXPORT_EMPTY))
        refreshing?.cancel()
        refreshing = scope.launch { refresh(facts.lastSettings(), rereadFreeSpace = true) }
    }

    fun onIntent(intent: PhotoExportIntent) {
        val sheet = host.uiState.exportSheet ?: return
        // While working, only closing does anything: every control is inert.
        if (sheet.working != null && intent != PhotoExportIntent.Close) return
        when (intent) {
            is PhotoExportIntent.Format -> change(sheet.settings.copy(format = PhotoExportFormat.entries.getOrNull(intent.index) ?: return), rereadFreeSpace = true)
            is PhotoExportIntent.Quality -> change(sheet.settings.copy(quality = PhotoExportCatalog.snappedQuality(intent.quality)), rereadFreeSpace = false)
            PhotoExportIntent.QualityReleased -> change(sheet.settings, rereadFreeSpace = true)
            is PhotoExportIntent.Size -> change(sheet.settings.copy(shortSidePx = PhotoExportCatalog.SHORT_SIDES_PX.getOrNull(intent.index) ?: return), rereadFreeSpace = true)
            PhotoExportIntent.Save -> start(sheet, PhotoExportAction.SAVE)
            PhotoExportIntent.Copy -> start(sheet, PhotoExportAction.COPY)
            PhotoExportIntent.Close -> close()
            PhotoExportIntent.RecheckSpace -> change(sheet.settings, rereadFreeSpace = true)
        }
    }

    private fun change(settings: PhotoExportSettings, rereadFreeSpace: Boolean) {
        refreshing?.cancel()
        refreshing = scope.launch { refresh(settings, rereadFreeSpace) }
    }

    // Tapping Save or Copy: explains when the room isn't there, else hands the draft to the use case.
    private fun start(sheet: PhotoExportSheetUi, action: PhotoExportAction) {
        val project = host.currentProject() ?: return
        val short = if (action == PhotoExportAction.SAVE) sheet.saveShortBytes != null else sheet.copyShort
        if (short) return host.showToast(PhotoToastMessage.Explain(PhotoExplanation.EXPORT_NO_SPACE))
        host.updateUiState { it.copy(exportSheet = sheet.copy(working = action, failure = null)) }
        if (!export.start(project, sheet.settings, action)) host.updateUiState { it.copy(exportSheet = sheet) }
    }

    private fun close() {
        refreshing?.cancel()
        // Cancelling shows "Export stopped." from the use case's state; the sheet is gone either way.
        export.cancel()
        host.updateUiState { it.copy(exportSheet = null) }
    }

    // The sheet for [settings]: the output size, the size guess, and the room it needs against what is free.
    private suspend fun refresh(settings: PhotoExportSettings, rereadFreeSpace: Boolean) {
        val project = host.currentProject() ?: return
        if (rereadFreeSpace) freeBytes = facts.freeBytes()
        val output = PhotoExportCatalog.outputSize(project.canvas, settings.shortSidePx)
        val estimate = estimatePhotoBytes(settings.format, settings.quality, output, facts.measuredMbPerMegapixel(settings))
        val saveNeeded = photoSaveSpaceNeededBytes(estimate)
        val copyNeeded = photoCopySpaceNeededBytes(estimate)
        host.updateUiState { state ->
            val previous = state.exportSheet
            state.copy(
                exportSheet = PhotoExportSheetUi(
                    settings = settings,
                    output = output,
                    estimateBytes = estimate,
                    saveShortBytes = (saveNeeded - freeBytes).takeIf { it > 0 }?.let(::roundedUpToTenMegabytes),
                    saveNeededBytes = saveNeeded,
                    copyShort = copyNeeded > freeBytes,
                    missingPicture = state.hasMissingPicture,
                    working = previous?.working,
                    // A changed setting ends the last failure's notice.
                    failure = previous?.failure?.takeIf { previous.settings == settings },
                ),
            )
        }
    }

    // What the use case reports: the sheet follows it, and the editor hears how it ended.
    private fun show(state: PhotoExportState) {
        when (state) {
            PhotoExportState.Idle -> Unit
            is PhotoExportState.Running -> host.updateUiState { ui -> ui.exportSheet?.let { ui.copy(exportSheet = it.copy(working = state.job.action, failure = null)) } ?: ui }
            is PhotoExportState.Saved -> handOver()
            is PhotoExportState.Copied -> {
                host.updateUiState { it.copy(exportSheet = null) }
                // Android 13 and up shows its own preview, so the toast is for older phones; every version announces it.
                host.showToast(PhotoToastMessage.PictureCopied, spokenOnly = export.clipboardConfirmsItself)
                export.dismiss()
            }
            is PhotoExportState.Failed -> {
                val shown = host.uiState.exportSheet != null
                host.updateUiState { ui -> ui.exportSheet?.let { ui.copy(exportSheet = it.copy(working = null, failure = state.error)) } ?: ui }
                if (!shown) export.dismiss()
            }
            is PhotoExportState.Cancelled -> {
                host.showToast(PhotoToastMessage.ExportStopped)
                export.dismiss()
            }
        }
    }

    // Saved: the Exported screen opens over the editor, once per saved picture.
    private fun handOver() {
        host.updateUiState { it.copy(exportSheet = null, openExportedScreen = true) }
    }

    // A cut-out still being made: the file would silently lack it (spec → Export button).
    private fun isCuttingOut(state: PhotoEditorUiState): Boolean =
        state.cutOut?.phase.let { it is CutOutPhase.Preparing || it == CutOutPhase.Working }

    private fun roundedUpToTenMegabytes(bytes: Long): Long = (bytes + TEN_MEGABYTES - 1) / TEN_MEGABYTES * TEN_MEGABYTES

    private companion object {
        const val TEN_MEGABYTES = 10_000_000L
    }
}
