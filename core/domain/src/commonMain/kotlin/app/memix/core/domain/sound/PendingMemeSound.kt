package app.memix.core.domain.sound

import app.memix.core.domain.Outcome
import app.memix.core.domain.SoundTab
import app.memix.core.domain.catalog.SoundCatalogRepository
import app.memix.core.domain.mysound.PrepareMySoundUseCase
import app.memix.core.model.catalog.CatalogSound

/** How a new video meme's starting sound went, which decides what the editor does when it opens (spec P3-15 → Arriving). */
enum class StartingSound {
    /** The project was made without a library sound. */
    NONE,

    /** The sound is on the timeline at 0:00. */
    ADDED,

    /** A sound was chosen but did not come along (it was lost with the process, or its file is gone). */
    MISSING,
}

/** The library sound a new video meme is waiting for: just its id and the `sound_add.tab` it was chosen from, small enough to save. */
data class PendingSound(val soundId: String, val tab: SoundTab)

/**
 * The library sound the next new video meme starts with (P3-15): from the "Use" tap, through the system picker and the
 * copy of the picked media, to the project's first save. One at a time. While it is held, its file is pinned against the
 * cache cleanup, so a download that finishes meanwhile can never evict it.
 *
 * Owned by the activity-scoped import ViewModel, so it survives a rotation and is dropped with the ViewModel. If Android
 * stopped the app behind the picker, the caller [adopt]s the id it saved and [ready] finds the sound again on the phone.
 * Main thread only.
 */
class PendingMemeSound(
    private val onPhone: SoundsOnPhone,
    private val library: SoundLibrary,
    private val catalog: SoundCatalogRepository,
    private val prepareSound: PrepareSoundUseCase,
    private val prepareMySound: PrepareMySoundUseCase,
) {
    private var sound: PickedSound? = null
    private var restored: PendingSound? = null
    private var pinnedId: String? = null

    /** What is waiting, or null. */
    val pending: PendingSound? get() = sound?.let { PendingSound(it.soundId, it.tab) } ?: restored

    /** Starts waiting for [chosen], whose file is ready; replaces any sound that was waiting. */
    fun hold(chosen: PickedSound) {
        drop()
        sound = chosen
        pin(chosen.soundId)
    }

    /** After a restart of the process: waits for the sound [saved] names, unless one is already waiting. */
    fun adopt(saved: PendingSound) {
        if (pending != null) return
        restored = saved
        // Pinned at once, so nothing evicts the file before the media has copied.
        pin(saved.soundId)
    }

    /**
     * The waiting sound with its file ready, or null when nothing waits or the sound is gone (not in the bundled pack, not
     * downloaded any more, or the file won't play). A restored sound is looked up on the phone, with no network.
     */
    suspend fun ready(): PickedSound? {
        sound?.let { return it }
        val saved = restored ?: return null
        val found = findOnPhone(saved) ?: return null
        pin(found.soundId)
        sound = found
        return found
    }

    /** The sound landed or the pick was given up: nothing waits, and the file may be cleaned up again. */
    fun drop() {
        pinnedId?.let(onPhone::release)
        pinnedId = null
        sound = null
        restored = null
    }

    private fun pin(soundId: String) {
        pinnedId?.let(onPhone::release)
        onPhone.hold(soundId)
        pinnedId = soundId
    }

    private suspend fun findOnPhone(saved: PendingSound): PickedSound? {
        // A My sound (P3-07) is the user's own file: no catalog, no download.
        if (saved.tab == SoundTab.MY_SOUNDS) return (prepareMySound(saved.soundId) as? Outcome.Success)?.value
        val starter = library.starterSounds().firstOrNull { it.id == saved.soundId }
        val title = starter?.title ?: catalog.titlesOf(setOf(saved.soundId))[saved.soundId] ?: return null
        // Only the id matters to the lookup: the file's own length is measured, not the catalog's.
        val catalogSound = starter ?: CatalogSound(saved.soundId, title, durationUs = 0, credit = "")
        val prepared = (prepareSound(catalogSound) as? Outcome.Success)?.value ?: return null
        return PickedSound(saved.soundId, title, prepared.media, saved.tab, prepared.bundled)
    }
}
