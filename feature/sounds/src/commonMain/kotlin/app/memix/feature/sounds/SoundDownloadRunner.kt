package app.memix.feature.sounds

import app.memix.core.domain.sound.DownloadFailure
import app.memix.core.domain.sound.DownloadState
import app.memix.core.model.catalog.LibrarySound
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Runs one download for a preview or a Use (spec P3-04) and tells the screen what to show: nothing for the first 300 ms
 * (a sound is about 46 KB, so most finish before that), then the Downloading look with its rail at most ten times a
 * second, then the Failed look if it fails. A cancelled download leaves nothing behind, not even a look.
 *
 * Runs on the ViewModel's main-thread scope, so [show] is called from one thread. [show] gets null to clear the look.
 */
internal class SoundDownloadRunner(
    private val downloadSound: (LibrarySound) -> Flow<DownloadState>,
    private val show: (soundId: String, look: SoundDownloadUi?) -> Unit,
) {
    /**
     * Downloads [sound] and returns null once its file is on the phone, or why it could not be downloaded. Cancelling the
     * caller cancels the download (when nobody else is waiting for it).
     */
    suspend fun download(sound: LibrarySound): DownloadFailure? = coroutineScope {
        val id = sound.id
        show(id, null)
        var visible = false
        var progress: Float? = 0f
        val reveal = launch {
            delay(LOOK_DELAY_MILLIS.milliseconds)
            visible = true
            show(id, SoundDownloadUi.Downloading(progress))
        }
        var failure: DownloadFailure? = null
        try {
            var lastShown = TimeSource.Monotonic.markNow()
            val end = downloadSound(sound).onEach { state ->
                if (state !is DownloadState.Progress) return@onEach
                progress = state.fraction
                if (visible && lastShown.elapsedNow() >= PROGRESS_STEP_MILLIS.milliseconds) {
                    lastShown = TimeSource.Monotonic.markNow()
                    show(id, SoundDownloadUi.Downloading(progress))
                }
            }.last()
            failure = when (end) {
                is DownloadState.Done -> null
                is DownloadState.Failed -> end.reason
                // The flow always ends with Done or Failed; if it ever did not, the sound is not on the phone.
                is DownloadState.Progress -> DownloadFailure.NETWORK
            }
            return@coroutineScope failure
        } finally {
            reveal.cancel()
            show(id, failure?.let { SoundDownloadUi.Failed(storageFull = it == DownloadFailure.STORAGE_FULL) })
        }
    }

    private companion object {
        /** Nothing shows for a download that finishes within this (spec P3-04 → Under 300 ms). */
        const val LOOK_DELAY_MILLIS = 300L

        /** The rail moves at most ten times a second (spec P3-04 → Motion). */
        const val PROGRESS_STEP_MILLIS = 100L
    }
}
