package app.memix.core.domain.sound

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.media.MediaInspector
import app.memix.core.model.catalog.CatalogSound
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef

/**
 * Gets [sound] ready for the timeline or a preview from what is already on the phone, with no network: the bundled
 * starter pack, else the downloaded copy (P3-04). The file is checked to play and measured. Fails with
 * [AppError.NotFound] when the sound is not on the phone or the file won't decode, which the editor explains as "That
 * sound won't play" (spec P1-06 → Meme sounds → Landing). A downloaded file that won't decode is deleted, so the next
 * play downloads it again.
 */
class PrepareSoundUseCase(
    private val library: SoundLibrary,
    private val downloads: SoundDownloads,
    private val inspector: MediaInspector,
) {
    suspend operator fun invoke(sound: CatalogSound): Outcome<MediaRef> {
        val file = when (val found = library.fileOf(sound)) {
            is Outcome.Success -> found.value
            is Outcome.Failure -> {
                // Not in the starter pack: a downloaded copy may be there. Any other failure (a full phone) is final.
                if (found.error != AppError.NotFound) return found
                downloadedFile(sound) ?: return found
            }
        }
        val path = file.cachedCopyPath ?: return Outcome.Failure(AppError.NotFound)
        val facts = inspector.inspect(path, MediaKind.AUDIO)
        if (facts == null) {
            downloads.discard(sound.id)
            return Outcome.Failure(AppError.NotFound)
        }
        // The measured length, not the catalog's: trims and the end rule must match the file that plays.
        return Outcome.Success(file.copy(durationUs = facts.durationUs, hasAudio = facts.hasAudio))
    }

    private suspend fun downloadedFile(sound: CatalogSound): MediaRef? {
        val path = downloads.downloadedFile(sound.id) ?: return null
        return MediaRef(MediaOrigin.CatalogItem(sound.id), MediaKind.AUDIO, cachedCopyPath = path)
    }
}
