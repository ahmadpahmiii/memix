package app.memix.core.data.settings

import app.memix.core.domain.voiceover.VoiceoverPreferences
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [VoiceoverPreferences] in the app's key-value settings (spec P3-08 → Permission). Every call runs on [ioDispatcher]. */
internal class SettingsVoiceoverPreferences(
    private val settings: Settings,
    private val ioDispatcher: CoroutineDispatcher,
) : VoiceoverPreferences {
    override suspend fun microphoneWasDenied(): Boolean = withContext(ioDispatcher) { settings.getBoolean(MICROPHONE_DENIED, false) }

    override suspend fun markMicrophoneDenied() = withContext(ioDispatcher) { settings.putBoolean(MICROPHONE_DENIED, true) }

    // Keys are saved on phones: never rename them.
    private companion object {
        const val MICROPHONE_DENIED = "voiceover.microphoneDenied"
    }
}
