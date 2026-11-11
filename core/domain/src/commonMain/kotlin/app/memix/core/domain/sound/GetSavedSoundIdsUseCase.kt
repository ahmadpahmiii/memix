package app.memix.core.domain.sound

import kotlinx.coroutines.flow.first

/**
 * The ids of the sounds whose audio is on the phone, which is what the offline search covers (spec P3-02 → Search):
 * the bundled starter pack and the downloaded sounds (P3-04). P3-07 adds the user's own. Favorites are not included: a
 * favorite's audio may not be on the phone.
 */
class GetSavedSoundIdsUseCase(private val library: SoundLibrary, private val downloads: SoundDownloads) {
    suspend operator fun invoke(): Set<String> {
        val ids = HashSet<String>()
        library.starterSounds().mapTo(ids) { it.id }
        ids += downloads.observeDownloadedIds().first()
        return ids
    }
}
