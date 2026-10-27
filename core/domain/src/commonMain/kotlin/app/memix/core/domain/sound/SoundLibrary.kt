package app.memix.core.domain.sound

import app.memix.core.domain.Outcome
import app.memix.core.model.catalog.CatalogSound
import app.memix.core.model.project.MediaRef

/**
 * The meme sounds this build can add: the starter pack bundled with the app (P1-08). Implemented in
 * :platform:services; the online catalog joins it in P3.
 *
 * Every function runs its work off the main thread, so it's safe to call from it.
 */
interface SoundLibrary {
    /** The starter pack in its curated order; empty when the build bundles none. */
    suspend fun starterSounds(): List<CatalogSound>

    /**
     * The sound's file on this phone, ready to play and to put on the timeline: copied out of the app the first time
     * it's needed in a run, then shared by every project (it isn't a project's own copy). The [MediaRef] carries the
     * catalog id as its origin and no measured facts; [PrepareSoundUseCase] measures it. Fails with
     * [app.memix.core.domain.AppError.NotFound] when the bundle doesn't hold the file, and StorageFull or Unexpected
     * when it can't be written.
     */
    suspend fun fileOf(sound: CatalogSound): Outcome<MediaRef>
}
