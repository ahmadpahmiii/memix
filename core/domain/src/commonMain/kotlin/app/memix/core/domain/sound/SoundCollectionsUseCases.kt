package app.memix.core.domain.sound

import app.memix.core.domain.Logger
import app.memix.core.model.catalog.LibrarySound
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/** Which sounds are hearted, for the heart on every row. */
class ObserveFavoriteSoundIdsUseCase(private val repository: SoundCollectionsRepository) {
    operator fun invoke(): Flow<Set<String>> = repository.observeFavoriteIds()
}

/** The Favorites section: every favorite, last favorited first. */
class ObserveFavoriteSoundsUseCase(private val repository: SoundCollectionsRepository) {
    operator fun invoke(): Flow<List<LibrarySound>> = repository.observeFavorites()
}

/** The Recently used section: the last [RECENT_SOUNDS_SHOWN] sounds that landed on a timeline, newest first. */
class ObserveRecentSoundsUseCase(private val repository: SoundCollectionsRepository) {
    operator fun invoke(): Flow<List<LibrarySound>> = repository.observeRecents(RECENT_SOUNDS_SHOWN)
}

/**
 * The heart: favorites the sound, or un-favorites it. Callers launch it and don't wait, so a storage failure (disk
 * full, locked database) is logged and dropped here instead of crashing the app; the heart then simply doesn't change.
 */
class ToggleFavoriteSoundUseCase(private val repository: SoundCollectionsRepository, private val logger: Logger) {
    suspend operator fun invoke(soundId: String) {
        try {
            repository.toggleFavorite(soundId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "Could not toggle favorite $soundId", e)
        }
    }
}

/**
 * A sound landed on a timeline (the editor's Meme sounds sheet today, the Use flow in P3-15), so it counts as recently
 * used. A preview does not count.
 */
class RecordRecentSoundUseCase(private val repository: SoundCollectionsRepository, private val logger: Logger) {
    /**
     * Never throws: callers launch it and don't wait, so a storage failure is logged and dropped. A recent is also lost
     * when the editor closes before the write runs; that is acceptable for a convenience list.
     */
    suspend operator fun invoke(soundId: String) {
        try {
            repository.recordRecent(soundId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "Could not record recent $soundId", e)
        }
    }
}

private const val TAG = "SoundCollections"
