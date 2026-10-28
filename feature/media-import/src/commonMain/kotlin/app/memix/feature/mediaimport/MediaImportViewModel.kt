package app.memix.feature.mediaimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.memix.core.domain.Outcome
import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.domain.media.SaveGalleryProjectUseCase
import app.memix.core.domain.project.CreateVideoProjectUseCase
import app.memix.core.model.project.MediaOrigin
import kotlinx.coroutines.flow.StateFlow

/**
 * The first import of a new video project (Create → Video meme, spec P1-02): copies what the user picked through a
 * [MediaImportFlow], saves the new project once something went in, and hands it to the editor
 * ([ImportUiState.Done]; the app opens the editor, then sends [ImportIntent.Finish]).
 *
 * Lives as long as the app's screen (activity), so copying carries on across rotation and in the background.
 * The picker itself is opened by the app, which sends the result here as [ImportIntent.Picked]. One immutable
 * [state] out, intents in, like every MemixViewModel; the flow owns the state.
 */
class MediaImportViewModel(
    private val createVideoProject: CreateVideoProjectUseCase,
    importMedia: ImportMediaUseCase,
    private val saveGalleryProject: SaveGalleryProjectUseCase,
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

    private fun startImport(picked: List<MediaOrigin>) {
        if (picked.isEmpty() || state.value != ImportUiState.Idle) return
        val project = createVideoProject()
        flow.start(picked, project.id) { added ->
            when (val saved = saveGalleryProject(project, added)) {
                is Outcome.Success -> LandingResult.Landed
                is Outcome.Failure -> LandingResult.Failed(saved.error)
            }
        }
    }
}
