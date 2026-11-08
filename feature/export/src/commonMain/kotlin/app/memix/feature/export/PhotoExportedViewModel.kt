package app.memix.feature.export

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import app.memix.core.domain.photo.ExportPhotoUseCase
import app.memix.core.domain.photo.PhotoExportState
import app.memix.core.domain.share.SharePictureUseCase
import app.memix.core.model.project.PhotoExportFormat
import app.memix.core.ui.MemixViewModel
import kotlinx.coroutines.launch

/**
 * The photo Exported screen (spec docs/ux/specs/P2-08-photo-export.md → Exported screen): shows the picture the export saved,
 * with Share, Make another and Done. It follows the app-scope [ExportPhotoUseCase] and never changes the draft.
 */
class PhotoExportedViewModel(
    private val export: ExportPhotoUseCase,
    private val share: SharePictureUseCase,
) : MemixViewModel<PhotoExportedUiState, PhotoExportedIntent>(PhotoExportedUiState()) {
    init {
        viewModelScope.launch { export.state.collect(::show) }
    }

    override fun onIntent(intent: PhotoExportedIntent) {
        when (intent) {
            PhotoExportedIntent.BackToEditor -> leave(ExportExit.EDITOR)
            PhotoExportedIntent.Back -> leave(ExportExit.EDITOR)
            PhotoExportedIntent.Done -> leave(ExportExit.HOME)
            PhotoExportedIntent.MakeAnother -> leave(ExportExit.MAKE_ANOTHER)
            is PhotoExportedIntent.Share -> shareResult(intent.chooserTitle)
        }
    }

    private fun show(exportState: PhotoExportState) {
        when (exportState) {
            is PhotoExportState.Saved -> updateState {
                val photo = exportState.photo
                it.copy(picture = SavedPictureUi(photo.location, photo.format, photo.pixelSize.widthPx, photo.pixelSize.heightPx))
            }
            // Nothing saved to show (the app came back with no export): back to where it was.
            PhotoExportState.Idle -> if (this.state.value.exit == null && this.state.value.picture == null) updateState { it.copy(exit = ExportExit.EDITOR) }
            is PhotoExportState.Running, is PhotoExportState.Copied, is PhotoExportState.Failed, is PhotoExportState.Cancelled -> Unit
        }
    }

    private fun leave(exit: ExportExit) {
        if (state.value.exit != null) return
        export.dismiss()
        // The picture stays, so the screen doesn't change its look while it slides away.
        updateState { it.copy(exit = exit) }
    }

    private fun shareResult(chooserTitle: String) {
        val picture = state.value.picture ?: return
        share(picture.location, picture.format.mimeType, chooserTitle)
    }
}

/** What the photo Exported screen shows. */
@Immutable
data class PhotoExportedUiState(
    val picture: SavedPictureUi? = null,
    /** Where the app goes now; the screen sends it to its caller once. Null while the screen stays. */
    val exit: ExportExit? = null,
)

/** The saved picture: where it is, its [format] and its size in pixels. */
@Immutable
data class SavedPictureUi(val location: String, val format: PhotoExportFormat, val widthPx: Int, val heightPx: Int) {
    val aspectRatio: Float get() = widthPx.toFloat() / heightPx
}

sealed interface PhotoExportedIntent {
    /** × on the screen. */
    data object BackToEditor : PhotoExportedIntent

    /** System back or Escape: the file is already saved, so back to the editor. */
    data object Back : PhotoExportedIntent

    data object Done : PhotoExportedIntent

    data object MakeAnother : PhotoExportedIntent

    /** Share opens the phone's share sheet titled [chooserTitle]. */
    data class Share(val chooserTitle: String) : PhotoExportedIntent
}
