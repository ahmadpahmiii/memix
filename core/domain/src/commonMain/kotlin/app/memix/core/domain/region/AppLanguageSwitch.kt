package app.memix.core.domain.region

import app.memix.core.model.AppLanguage
import kotlinx.coroutines.flow.StateFlow

/** The language Memix shows, and the phone's own. Implemented in :platform:services; the platform switches the strings. */
interface AppLanguageSwitch {
    /** The language the app shows now: the per-app choice, else the phone's, else English when that is not one of the five. */
    val current: StateFlow<AppLanguage>

    /** False where the platform cannot change one app's language at runtime (the iOS stub until P7-02); the Region sheet then leaves the Language group out. True on every Android version. */
    val canChange: Boolean

    /** The phone's own language subtag ("fr"), whatever the app shows; null when the phone has none. */
    fun phoneLanguageCode(): String?

    /** Name of the language with [code] written in the app's current language ("French"). */
    fun languageName(code: String): String

    /**
     * Makes the app show [language]. [current] changes at once; on Android the screen is recreated (when it is not already in that language) so every string redraws.
     * Does nothing when [canChange] is false. Call from the main thread.
     */
    fun apply(language: AppLanguage)
}
