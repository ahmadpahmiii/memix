package app.memix.feature.videoeditor.importmedia

import kotlinx.coroutines.flow.StateFlow

/** The first import of a new meme project (Create → Video meme or Photo meme): one state out, intents in, as the app root drives it. */
interface MemeImportViewModel {
    val state: StateFlow<ImportUiState>

    fun onIntent(intent: ImportIntent)
}

/** What the import sheet's words are about: a pick of videos and photos, or one photo (spec P2-01 → Starting a photo meme). */
enum class ImportWording { MEDIA, PHOTO }
