package app.memix.core.domain.sound

import app.memix.core.model.catalog.CatalogSound
import app.memix.core.model.catalog.LibrarySound
import kotlinx.coroutines.flow.Flow

/**
 * Where sounds' audio is, for the Sounds screen and the picker (P3-04): the bundled starter pack, the downloaded sounds
 * and whether the phone is online. One class instead of a use case per call, because a screen needs all of them together.
 */
class SoundsOnPhone(
    private val library: SoundLibrary,
    private val downloads: SoundDownloads,
    private val network: NetworkStatus,
) {
    /** The bundled starter pack in its curated order: always on the phone, and the list the screen shows when the catalog can't be reached. */
    suspend fun starters(): List<CatalogSound> = library.starterSounds()

    /** The ids of the downloaded sounds; emits again after every download and removal. */
    fun observeDownloadedIds(): Flow<Set<String>> = downloads.observeDownloadedIds()

    /** Whether the phone has a connection: the current value first, then each change. */
    fun observeOnline(): Flow<Boolean> = network.observeOnline()

    /**
     * Downloads a sound that is not on the phone, or joins the download already running for it. Collect to run it, stop
     * collecting to cancel it; nothing is kept when it is cancelled. See [SoundDownloads.download].
     */
    fun download(sound: LibrarySound): Flow<DownloadState> = downloads.download(sound)

    /** Deletes a downloaded file that would not play, so the next play downloads it again (P3-03 review S5). */
    suspend fun discard(soundId: String) = downloads.discard(soundId)

    /** A play or a landing on a timeline: the sound moves to the back of the cleanup order. */
    suspend fun markUsed(soundId: String) = downloads.markUsed(soundId)

    /** Keeps a sound's file out of the cache cleanup while it plays or is being added (spec P3-15). */
    fun hold(soundId: String) = downloads.pin(soundId)

    /** Ends one [hold]. */
    fun release(soundId: String) = downloads.unpin(soundId)
}
