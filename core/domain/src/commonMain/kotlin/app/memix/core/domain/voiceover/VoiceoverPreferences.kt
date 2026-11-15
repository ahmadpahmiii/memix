package app.memix.core.domain.voiceover

/** What the voiceover tool remembers between runs (spec P3-08 → Permission): one flag, kept in the app's settings. */
interface VoiceoverPreferences {
    /** Whether Android's microphone dialog was shown and answered "No" (the app asks once; after that Settings is the way). */
    suspend fun microphoneWasDenied(): Boolean

    suspend fun markMicrophoneDenied()
}
