package app.memix.core.data.settings

import app.memix.core.domain.photo.VideoMemeNotePreferences
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [VideoMemeNotePreferences] in the app's key-value settings (spec P2-10 → Arriving). Every call runs on [ioDispatcher]. */
internal class SettingsVideoMemeNotePreferences(
    private val settings: Settings,
    private val ioDispatcher: CoroutineDispatcher,
) : VideoMemeNotePreferences {
    override suspend fun wasShown(): Boolean = withContext(ioDispatcher) { settings.getBoolean(NOTE_SHOWN, false) }

    override suspend fun markShown() = withContext(ioDispatcher) { settings.putBoolean(NOTE_SHOWN, true) }

    // Keys are saved on phones: never rename them.
    private companion object {
        const val NOTE_SHOWN = "videoMeme.noteShown"
    }
}
