package app.memix.core.domain.mysound

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.SoundTab
import app.memix.core.domain.media.MediaInspector
import app.memix.core.domain.sound.PickedSound
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** The My sounds list, newest first. */
class ObserveMySoundsUseCase(private val repository: MySoundsRepository) {
    operator fun invoke(): Flow<List<MySound>> = repository.observeAll()
}

/**
 * Renames a My sound. A rename writes the name only: it is not an edit to any draft that used the sound. The name is
 * cleaned first ([cleanMySoundName]); an empty one is refused ([RenameOutcome.Empty]).
 */
class RenameMySoundUseCase(private val repository: MySoundsRepository) {
    suspend operator fun invoke(id: String, typedName: String): RenameOutcome {
        val name = cleanMySoundName(typedName)
        if (name.isEmpty()) return RenameOutcome.Empty
        return if (repository.rename(id, name)) RenameOutcome.Renamed(name) else RenameOutcome.Missing
    }
}

sealed interface RenameOutcome {
    data class Renamed(val name: String) : RenameOutcome
    data object Empty : RenameOutcome
    data object Missing : RenameOutcome
}

/**
 * The offline search over My sounds (spec P3-02 → Offline): contains-match on the names. [savedSoundIds] are the sounds whose
 * audio is on the phone ([app.memix.core.domain.sound.GetSavedSoundIdsUseCase], which includes every My sound).
 */
class SearchMySoundsUseCase(private val repository: MySoundsRepository) {
    suspend operator fun invoke(query: String, savedSoundIds: Set<String>): List<MySound> =
        repository.observeAll().first().filter { it.id in savedSoundIds }.matching(query)
}

/**
 * Gets a My sound ready to land on a timeline or start a new video meme: the file is checked to play (a missing or damaged
 * file fails with [AppError.NotFound]) and the sound is described as a [PickedSound] with the origin [MediaOrigin.MySound],
 * which makes the project copy the file ([app.memix.core.domain.sound.PlaceSoundInProjectUseCase]) and labels the clip.
 */
class PrepareMySoundUseCase(private val repository: MySoundsRepository, private val inspector: MediaInspector) {
    suspend operator fun invoke(id: String, tab: SoundTab = SoundTab.MY_SOUNDS): Outcome<PickedSound> {
        val sound = repository.get(id) ?: return Outcome.Failure(AppError.NotFound)
        // The measured length, not the stored one: trims and the end rule must match the file that plays.
        val facts = inspector.inspect(sound.path, MediaKind.AUDIO) ?: return Outcome.Failure(AppError.NotFound)
        val media = MediaRef(MediaOrigin.MySound(sound.name), MediaKind.AUDIO, sound.path, durationUs = facts.durationUs, hasAudio = facts.hasAudio)
        return Outcome.Success(PickedSound(sound.id, LocalizedText(mapOf("en" to sound.name)), media, tab, bundled = false))
    }
}
