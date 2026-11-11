package app.memix.core.domain.media

import app.memix.core.domain.Analytics
import app.memix.core.domain.AppError
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.Outcome
import app.memix.core.domain.ProjectSource
import app.memix.core.domain.drafts.UpdateDraftThumbnailUseCase
import app.memix.core.domain.project.SaveProjectUseCase
import app.memix.core.domain.project.addSound
import app.memix.core.domain.sound.LogSoundEventsUseCase
import app.memix.core.domain.sound.PickedSound
import app.memix.core.domain.sound.PlaceSoundInProjectUseCase
import app.memix.core.domain.sound.RecordRecentSoundUseCase
import app.memix.core.domain.sound.StartingSound
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import app.memix.core.model.project.TrackKind
import kotlin.uuid.Uuid

/**
 * The first save of a new video project made from picked media, once something has landed: [media] goes on the
 * main video track from 0 µs in pick order. Nothing is saved before this, so backing out or cancelling leaves no
 * empty draft. Logs `project_create` when the save works, and starts the draft's thumbnail for the drafts list
 * (P1-14) without waiting for it. The editor then opens the saved draft.
 *
 * With a [sound] (P3-15, a library sound chosen with Use) the project starts with it: copied into the project's own
 * folder and put whole at 0:00 on the first meme-sound lane, in the same save as the clips, so it is part of the
 * project's first state and Undo starts empty. `sound_add` follows `project_create`, and the sound counts as recently
 * used. Returns what became of the sound ([StartingSound]); a sound whose cache file is gone ([AppError.NotFound]) is left
 * out and the project is saved without it, but a full phone ([AppError.StorageFull]) fails the save, so the import's "Not
 * enough space" and its retry apply. A copy made for a save that then fails is deleted.
 */
class SaveGalleryProjectUseCase(
    private val saveProject: SaveProjectUseCase,
    private val analytics: Analytics,
    private val updateThumbnail: UpdateDraftThumbnailUseCase,
    private val placeSound: PlaceSoundInProjectUseCase,
    private val soundEvents: LogSoundEventsUseCase,
    private val recordRecent: RecordRecentSoundUseCase,
    private val mediaFiles: MediaFiles,
) {
    suspend operator fun invoke(newProject: Project, media: List<MediaRef>, sound: PickedSound? = null): Outcome<StartingSound> {
        require(media.isNotEmpty()) { "A gallery project is saved only once something went in" }
        val withMedia = newProject.withMediaAppended(media)
        val soundFile = when (val placed = sound?.let { placeSound(newProject.id, it) }) {
            is Outcome.Failure -> if (placed.error == AppError.StorageFull) return placed else null
            is Outcome.Success -> placed.value
            null -> null
        }
        val soundClipId = Uuid.random().toString()
        val project = if (soundFile == null) withMedia else withMedia.addSound(soundFile, soundClipId, atUs = 0L, newTrackId = Uuid.random().toString())
        // addSound leaves the project as it is when the file has no measured length.
        val soundAdded = project.video?.tracks.orEmpty().any { track -> track.kind == TrackKind.MEME_SOUND && track.items.any { it.id == soundClipId } }
        return when (val saved = saveProject(project)) {
            is Outcome.Failure -> {
                deleteOwnCopy(sound, soundFile)
                saved
            }
            is Outcome.Success -> {
                analytics.log(AnalyticsEvent.ProjectCreate(editor = ProjectType.VIDEO, source = ProjectSource.GALLERY))
                if (soundAdded && sound != null) {
                    soundEvents.added(sound.soundId, sound.tab, ProjectType.VIDEO)
                    recordRecent(sound.soundId)
                }
                updateThumbnail(project)
                Outcome.Success(if (sound == null) StartingSound.NONE else if (soundAdded) StartingSound.ADDED else StartingSound.MISSING)
            }
        }
    }

    // The project's own copy of a library sound; the bundled starter file is shared and never deleted.
    private suspend fun deleteOwnCopy(sound: PickedSound?, file: MediaRef?) {
        val path = file?.cachedCopyPath ?: return
        if (path != sound?.media?.cachedCopyPath) mediaFiles.delete(listOf(path))
    }
}
