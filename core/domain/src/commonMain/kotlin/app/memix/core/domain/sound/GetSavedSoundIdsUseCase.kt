package app.memix.core.domain.sound

import app.memix.core.domain.mysound.MySoundsRepository
import kotlinx.coroutines.flow.first

/**
 * The ids of the sounds whose audio is on the phone, which is what the offline search covers (spec P3-02 → Search): the
 * bundled starter pack, the downloaded sounds (P3-04) and the user's own My sounds (P3-07). Favorites are not included: a
 * favorite's audio may not be on the phone.
 */
class GetSavedSoundIdsUseCase(
    private val library: SoundLibrary,
    private val downloads: SoundDownloads,
    private val mySounds: MySoundsRepository,
) {
    suspend operator fun invoke(): Set<String> {
        val ids = HashSet<String>()
        library.starterSounds().mapTo(ids) { it.id }
        ids += downloads.observeDownloadedIds().first()
        mySounds.observeAll().first().mapTo(ids) { it.id }
        return ids
    }
}
