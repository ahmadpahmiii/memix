package app.memix.core.domain.photo

/** Whether the one-time note after "Make it a video meme" has been shown on this phone (spec P2-10 → Arriving). Main-safe. */
interface VideoMemeNotePreferences {
    suspend fun wasShown(): Boolean

    suspend fun markShown()
}

/**
 * The note "Your layers are now one picture, except text on top, which stays editable.", once per install. [isDue] says whether to
 * show it; [dismissed] (Got it, or its time ran out) is what counts it as seen, so a note the app was killed under comes back.
 */
class VideoMemeNoteUseCase(private val preferences: VideoMemeNotePreferences) {
    suspend fun isDue(): Boolean = !preferences.wasShown()

    suspend fun dismissed() = preferences.markShown()
}
