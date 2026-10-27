package app.memix.core.domain.sound

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.media.MediaInspector
import app.memix.core.model.catalog.CatalogSound
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef

/**
 * Gets [sound] ready for the timeline or a preview: its file on the phone, checked to play and measured. Fails with
 * [AppError.NotFound] when the file isn't there or won't decode, which the editor explains as "That sound won't play"
 * (spec P1-06 → Meme sounds → Landing).
 */
class PrepareSoundUseCase(private val library: SoundLibrary, private val inspector: MediaInspector) {
    suspend operator fun invoke(sound: CatalogSound): Outcome<MediaRef> {
        val file = when (val found = library.fileOf(sound)) {
            is Outcome.Success -> found.value
            is Outcome.Failure -> return found
        }
        val path = file.cachedCopyPath ?: return Outcome.Failure(AppError.NotFound)
        val facts = inspector.inspect(path, MediaKind.AUDIO) ?: return Outcome.Failure(AppError.NotFound)
        // The measured length, not the catalog's: trims and the end rule must match the file that plays.
        return Outcome.Success(file.copy(durationUs = facts.durationUs, hasAudio = facts.hasAudio))
    }
}
