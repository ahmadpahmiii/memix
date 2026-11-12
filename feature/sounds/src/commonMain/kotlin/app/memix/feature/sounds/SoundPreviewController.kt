package app.memix.feature.sounds

import app.memix.core.domain.Outcome
import app.memix.core.domain.SoundTab
import app.memix.core.domain.catalog.LoadWaveformUseCase
import app.memix.core.domain.mysound.MY_SOUND_EVENT_ID
import app.memix.core.domain.mysound.PrepareMySoundUseCase
import app.memix.core.domain.sound.DownloadFailure
import app.memix.core.domain.sound.LogSoundEventsUseCase
import app.memix.core.domain.sound.LowVolumeWarningUseCase
import app.memix.core.domain.sound.OpenSoundPlayerUseCase
import app.memix.core.domain.sound.PickedSound
import app.memix.core.domain.sound.PrepareSoundUseCase
import app.memix.core.domain.sound.PreparedSound
import app.memix.core.domain.sound.SoundPlayer
import app.memix.core.domain.sound.SoundsOnPhone
import app.memix.core.model.catalog.LibrarySound
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the preview controller needs from the app, handed over together (a Koin factory) so ViewModel constructors stay readable. */
class SoundPreviewTools(
    val prepareSound: PrepareSoundUseCase,
    val openSoundPlayer: OpenSoundPlayerUseCase,
    val waveformOf: LoadWaveformUseCase,
    val lowVolumeWarning: LowVolumeWarningUseCase,
    val logEvents: LogSoundEventsUseCase,
    val onPhone: SoundsOnPhone,
    val prepareMySound: PrepareMySoundUseCase,
)

/** What a screen draws from its previews: who plays, who is paused, the downloads people can see, the peaks and the full-phone note. */
internal data class PreviewState(
    val playingId: String? = null,
    val pausedId: String? = null,
    val downloads: ImmutableMap<String, SoundDownloadUi> = persistentMapOf(),
    val waveforms: ImmutableMap<String, List<Float>> = persistentMapOf(),
    val storageFullNote: Boolean = false,
)

/**
 * Plays, downloads and prepares sounds for a screen: the Sounds tab (P3-02 to P3-04) and the editor's picker (P3-05)
 * each own one. It holds the player, the cache-cleanup pins for what plays or is being added, the download runner and the
 * checked-file cache, so both screens behave the same.
 *
 * Main thread only. [scope] is the screen's ViewModel scope. Every change of [state] is reported to [onChanged] at once;
 * a toast the user should see goes to [onNotice]. [isOnPhone] says whether a sound's audio is already on the phone (the
 * bundled pack or a download), which decides between "download first" and "this file is broken".
 */
internal class SoundPreviewController(
    private val scope: CoroutineScope,
    private val tools: SoundPreviewTools,
    private val isOnPhone: (soundId: String) -> Boolean,
    private val onNotice: (SoundsToast) -> Unit,
    private val onChanged: (PreviewState) -> Unit,
) {
    var state = PreviewState()
        private set

    /** Where the playing sound is; the screen reads it in draw code, 30 times a second at most. */
    fun positionUs(): Long = player?.positionUs() ?: 0L

    private var player: SoundPlayer? = null
    private var playerJobs: List<Job> = emptyList()
    private var previewJob: Job? = null

    // A sound's file once it has been checked, so the next tap starts without measuring it again (300 ms budget).
    private val preparedSounds = HashMap<String, PreparedSound>()
    private val waveformRequests = HashSet<String>()

    // The sound whose file is kept out of the cache cleanup because it plays (or is paused), and the one being added.
    private var heldSoundId: String? = null
    private var addHeldId: String? = null
    private val downloader = SoundDownloadRunner(tools.onPhone::download) { soundId, look -> showDownload(soundId, look) }

    // The row id of a My sound that plays, by its file: the player reports the media, which carries no row id (P3-07).
    private val mineIdByPath = HashMap<String, String>()

    // A sound played again right after it ended or stopped is a replay: `sound_preview` counts only a new sound (spec P3-03 → Analytics).
    private var lastPreviewedId: String? = null

    private fun update(change: (PreviewState) -> PreviewState) {
        state = change(state)
        onChanged(state)
    }

    /** Gets the player ready so the first tap starts at once. */
    fun warmUp() = openedPlayer().warmUp()

    // ---- Playing ----

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.resume()
    }

    /** The disc on a Downloading row (it is Cancel): stops the download that is running, keeps nothing. */
    fun cancelDownload() {
        previewJob?.cancel()
    }

    /**
     * Starts [sound]: downloads it first when it is not on the phone, then plays it from the start. One sound at a time:
     * another row's download stops here, and a tap on a Failed row is a fresh attempt. `sound_preview` is logged with
     * [tab] for a new sound only.
     */
    fun play(sound: LibrarySound, tab: SoundTab) {
        loadWaveform(sound)
        previewJob?.cancel()
        previewJob = scope.launch {
            val file = fileOnPhone(sound)?.media ?: return@launch
            // The sound plays anyway; the toast is a hint that a silent phone is not a broken app.
            if (tools.lowVolumeWarning.isDue()) onNotice(SoundsToast.VOLUME_LOW)
            openedPlayer().play(file)
            if (sound.id != lastPreviewedId) tools.logEvents.previewed(sound.id, tab)
            lastPreviewedId = sound.id
        }
    }

    /**
     * Starts the My sound [soundId] from the beginning (spec P3-07): its file is on the phone, so there is no download. Logs
     * `sound_preview` with the constant `my_sound` for a new sound only.
     */
    fun playMine(soundId: String) {
        previewJob?.cancel()
        previewJob = scope.launch {
            val picked = (tools.prepareMySound(soundId) as? Outcome.Success)?.value
            if (picked == null) {
                onNotice(SoundsToast.PLAY_FAILED)
                return@launch
            }
            picked.media.cachedCopyPath?.let { mineIdByPath[it] = soundId }
            if (tools.lowVolumeWarning.isDue()) onNotice(SoundsToast.VOLUME_LOW)
            openedPlayer().play(picked.media)
            if (soundId != lastPreviewedId) tools.logEvents.previewed(MY_SOUND_EVENT_ID, SoundTab.MY_SOUNDS)
            lastPreviewedId = soundId
        }
    }

    /** The user moved on: the sound stops and any download is cancelled. The player stays for the next visit. */
    fun stop() {
        previewJob?.cancel()
        player?.stop()
        lastPreviewedId = null
        // A failed look is about the visit that ended; a download still running was cancelled above.
        update { it.copy(downloads = persistentMapOf()) }
    }

    /** The app went to the background: the player is released; the next [warmUp] makes it again. */
    fun release() {
        previewJob?.cancel()
        closePlayer()
    }

    /** The screen is gone for good. */
    fun close() {
        previewJob?.cancel()
        endAdd()
        heldSoundId?.let(tools.onPhone::release)
        heldSoundId = null
        player?.close()
        player = null
    }

    // ---- Adding (the picker) ----

    /**
     * Gets [sound]'s file ready to land on a timeline without playing it: stops any preview, downloads the sound when it
     * is not on the phone (showing the Downloading look past 300 ms) and checks the file. [onReady] gets the checked file and whether it is the bundled one,
     * and the sound stays pinned against the cache cleanup until [endAdd]. [onAbort] runs when nothing is ready: the
     * download failed (the row shows why), the file would not play (a toast), or [cancelDownload] or another start ended it.
     */
    fun prepareForAdd(sound: LibrarySound, onReady: (PreparedSound) -> Unit, onAbort: () -> Unit) {
        player?.stop()
        previewJob?.cancel()
        previewJob = scope.launch {
            // Its own pin from the start, so a newer add that cancels this one never loses the pin it took.
            tools.onPhone.hold(sound.id)
            var handedOver = false
            try {
                val file = fileOnPhone(sound) ?: return@launch
                // An earlier add that was ready but never landed lets go.
                endAdd()
                addHeldId = sound.id
                handedOver = true
                onReady(file)
            } finally {
                if (!handedOver) {
                    tools.onPhone.release(sound.id)
                    onAbort()
                }
            }
        }
    }

    /**
     * Gets the My sound [soundId] ready to land without playing it (the picker's Add, the Sounds tab's Use). Its file needs no
     * download and no cache pin, so [onReady] gets the checked sound at once; [onAbort] runs when the file would not play.
     */
    fun prepareMineForAdd(soundId: String, onReady: (PickedSound) -> Unit, onAbort: () -> Unit) {
        player?.stop()
        previewJob?.cancel()
        previewJob = scope.launch {
            val picked = (tools.prepareMySound(soundId) as? Outcome.Success)?.value
            if (picked == null) {
                onNotice(SoundsToast.PLAY_FAILED)
                onAbort()
                return@launch
            }
            // An earlier add that was ready but never landed lets go.
            endAdd()
            onReady(picked)
        }
    }

    /** The sound has landed, or the add was given up: its file may be cleaned up again. */
    fun endAdd() {
        addHeldId?.let(tools.onPhone::release)
        addHeldId = null
    }

    // ---- Files, downloads, peaks ----

    /** Forgets prepared files that [stillThere] no longer vouches for (the cleanup removed them). */
    fun retainPrepared(stillThere: (soundId: String) -> Boolean) {
        preparedSounds.keys.retainAll(stillThere)
    }

    fun dismissStorageNote() = update { it.copy(storageFullNote = false) }

    // The sound's file, downloaded first when it is not on the phone. Null when there is nothing to play: the look or a
    // toast already says why.
    private suspend fun fileOnPhone(sound: LibrarySound): PreparedSound? {
        preparedFile(sound)?.let { return it }
        if (isOnPhone(sound.id)) return playFailed()
        val failure = downloader.download(sound)
        if (failure == DownloadFailure.STORAGE_FULL) update { it.copy(storageFullNote = true) }
        if (failure != null) return null
        update { it.copy(storageFullNote = false) }
        return preparedFile(sound) ?: playFailed()
    }

    private fun playFailed(): PreparedSound? {
        onNotice(SoundsToast.PLAY_FAILED)
        return null
    }

    private fun showDownload(soundId: String, look: SoundDownloadUi?) {
        update { current ->
            val downloads = if (look == null) current.downloads - soundId else current.downloads + (soundId to look)
            current.copy(downloads = downloads.toImmutableMap())
        }
    }

    private suspend fun preparedFile(sound: LibrarySound): PreparedSound? {
        preparedSounds[sound.id]?.let {
            // A repeat play counts as a use; a first one is counted when the file is read.
            scope.launch { tools.onPhone.markUsed(sound.id) }
            return it
        }
        return when (val prepared = tools.prepareSound(sound.toCatalogSound())) {
            is Outcome.Success -> prepared.value.also {
                if (preparedSounds.size >= MAX_PREPARED_SOUNDS) preparedSounds.clear()
                preparedSounds[sound.id] = it
            }
            is Outcome.Failure -> null
        }
    }

    /**
     * The peaks arrive after the sound starts when they have to be fetched; until then the row shows the rail. A failed
     * load saves nothing, so the next play asks again.
     */
    fun loadWaveform(sound: LibrarySound) {
        if (sound.waveformPath == null || sound.id in state.waveforms || !waveformRequests.add(sound.id)) return
        scope.launch {
            val waveform = tools.waveformOf(sound)
            waveformRequests.remove(sound.id)
            if (waveform == null) return@launch
            update { it.copy(waveforms = (it.waveforms + (sound.id to waveform.peaks)).toImmutableMap()) }
        }
    }

    // ---- The player ----

    private fun openedPlayer(): SoundPlayer = player ?: tools.openSoundPlayer().also { opened ->
        player = opened
        playerJobs = listOf(
            scope.launch {
                opened.playing.collect { media ->
                    update { it.copy(playingId = media?.rowId()) }
                    holdPlayingSound()
                }
            },
            scope.launch {
                opened.paused.collect { media ->
                    update { it.copy(pausedId = media?.rowId()) }
                    holdPlayingSound()
                }
            },
            // A file that would not play (deleted, damaged) is checked again on the next tap instead of being trusted.
            scope.launch {
                opened.failures.collect { media ->
                    // Not deleted here: the fault may be the player's. The next tap checks the file
                    // (PrepareSoundUseCase deletes one that will not decode).
                    media.rowId()?.let { preparedSounds.remove(it) }
                    onNotice(SoundsToast.PLAY_FAILED)
                }
            },
        )
    }

    // The row a playing file belongs to: a catalog sound by its id, a My sound by the file it was started from.
    private fun MediaRef.rowId(): String? = when (val origin = origin) {
        is MediaOrigin.CatalogItem -> origin.itemId
        is MediaOrigin.MySound -> cachedCopyPath?.let(mineIdByPath::get)
        is MediaOrigin.GalleryUri, is MediaOrigin.PhotoAsset -> null
    }

    // The player is opened on the first tap and lives only while the user is on the screen.
    private fun closePlayer() {
        playerJobs.forEach { it.cancel() }
        playerJobs = emptyList()
        player?.close()
        player = null
        update { it.copy(playingId = null, pausedId = null) }
        holdPlayingSound()
    }

    // The playing (or paused) sound's file is out of the cache cleanup until it stops.
    private fun holdPlayingSound() {
        val active = state.playingId ?: state.pausedId
        if (active == heldSoundId) return
        heldSoundId?.let(tools.onPhone::release)
        active?.let(tools.onPhone::hold)
        heldSoundId = active
    }

    private companion object {
        /** Prepared files kept for quick replays; far more than a visit touches, so the map stays small. */
        const val MAX_PREPARED_SOUNDS = 100
    }
}
