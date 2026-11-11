package app.memix.core.domain.sound

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.LeftoverMediaCleaner
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaPaths
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef

/**
 * Gives a sound that is about to land on a timeline the file its project will own. A downloaded library sound is copied
 * into the project's own folder (`media/<project id>/<random id>.<extension>`), so the cache cleanup can never take a
 * sound out from under a draft, and a taken-down sound keeps playing in the drafts that use it (decision log, 11 Nov
 * 2026). The bundled starter pack stays one shared file. The copy is a new file with the same facts, origin and length.
 *
 * Fails with [AppError.StorageFull] when the phone has no room and [AppError.NotFound] when the cached file is gone.
 */
class PlaceSoundInProjectUseCase(
    private val mediaFiles: MediaFiles,
    private val leftoverCleaner: LeftoverMediaCleaner,
) {
    suspend operator fun invoke(projectId: String, sound: PickedSound): Outcome<MediaRef> {
        val source = sound.media.cachedCopyPath ?: return Outcome.Failure(AppError.NotFound)
        if (sound.bundled) return Outcome.Success(sound.media)
        // The launch cleanup deletes copies no saved draft uses, which a new one is until the draft is saved again.
        leftoverCleaner.awaitDone()
        val destination = MediaPaths.newCopy(projectId, MediaKind.AUDIO, source.substringAfterLast('.', ""))
        return when (mediaFiles.copyFile(source, destination) { }) {
            CopyResult.COPIED -> Outcome.Success(sound.media.copy(cachedCopyPath = destination))
            CopyResult.STORAGE_FULL -> Outcome.Failure(AppError.StorageFull)
            CopyResult.UNREADABLE -> Outcome.Failure(AppError.NotFound)
        }
    }
}
