package app.memix.feature.videoeditor.importmedia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.domain.media.SaveGalleryPhotoProjectUseCase
import app.memix.core.domain.project.CreatePhotoProjectUseCase
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import kotlinx.coroutines.flow.StateFlow

/**
 * The first import of a new photo project (Create → Photo meme, spec P2-01 → Starting a photo meme): copies the one photo
 * the user picked through a [MediaImportFlow], saves the new project once it has landed (the canvas takes the photo's own
 * shape) and hands it to the editor ([ImportUiState.Done]; the app opens the editor, then sends [ImportIntent.Finish]).
 * Lives as long as the app's screen, like [MediaImportViewModel]; the picker is the app's.
 */
class PhotoMemeImportViewModel(
    private val createPhotoProject: CreatePhotoProjectUseCase,
    importMedia: ImportMediaUseCase,
    private val saveGalleryPhotoProject: SaveGalleryPhotoProjectUseCase,
) : ViewModel(), MemeImportViewModel {
    private val flow = MediaImportFlow(importMedia, viewModelScope)

    override val state: StateFlow<ImportUiState> = flow.state

    override fun onIntent(intent: ImportIntent) {
        when (intent) {
            is ImportIntent.Picked -> startImport(intent.items)
            ImportIntent.PickerUnavailable, ImportIntent.Cancel, ImportIntent.Continue, ImportIntent.Close,
            ImportIntent.AppResumed, ImportIntent.FreeUpSpaceUnavailable, ImportIntent.Finish,
            -> flow.onIntent(intent)
        }
    }

    // One photo only: a phone's file chooser ignores the limit, so anything past the first pick is left out.
    private fun startImport(picked: List<MediaOrigin>) {
        if (picked.isEmpty() || state.value != ImportUiState.Idle) return
        val project = createPhotoProject()
        flow.start(picked.take(1), project.id) { added ->
            val photo = added.firstOrNull { it.kind == MediaKind.IMAGE }
            if (photo == null) return@start LandingResult.Failed(AppError.Unexpected(IllegalStateException("The picked file is not a photo")))
            when (val saved = saveGalleryPhotoProject(project, photo)) {
                is Outcome.Success -> LandingResult.Landed
                is Outcome.Failure -> LandingResult.Failed(saved.error)
            }
        }
    }
}
