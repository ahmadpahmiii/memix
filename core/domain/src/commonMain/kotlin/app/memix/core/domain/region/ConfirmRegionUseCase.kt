package app.memix.core.domain.region

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.model.AppLanguage

/**
 * Applies what the Region sheet confirmed: stores the region, switches the app language when it changed, and logs the event.
 * The first confirm ends the first-launch step and logs `onboarding_complete`; a later one logs `region_change` only when
 * the language or the region differs from before (PRD → analytics). Opening or dismissing the sheet logs nothing.
 */
class ConfirmRegionUseCase(
    private val preferences: RegionPreferences,
    private val languageSwitch: AppLanguageSwitch,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(choice: RegionChoice) {
        val firstRun = !preferences.firstRunDone()
        val previousRegion = preferences.savedRegionCode()
        val previousLanguage: AppLanguage = languageSwitch.current.value
        val regionCode = choice.regionCode.uppercase()
        preferences.confirm(regionCode)
        val event = when {
            firstRun -> AnalyticsEvent.OnboardingComplete(choice.language, regionCode)
            choice.language != previousLanguage || !regionCode.equals(previousRegion, ignoreCase = true) ->
                AnalyticsEvent.RegionChange(choice.language, regionCode)
            else -> null
        }
        event?.let(analytics::log)
        // The first confirm pins the language the sheet showed, so what the app says and what the sheet says cannot differ
        // (a phone with several languages may resolve to one the app labels another way).
        if (choice.language != previousLanguage || (firstRun && languageSwitch.canChange)) languageSwitch.apply(choice.language)
    }
}
