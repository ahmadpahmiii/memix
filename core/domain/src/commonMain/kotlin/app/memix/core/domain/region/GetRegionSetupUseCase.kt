package app.memix.core.domain.region

import app.memix.core.domain.DeviceRegion
import app.memix.core.model.AppLanguage
import app.memix.core.model.LaunchRegions

/** A tile of the sheet's Region group: [name] is the country written in the app's current language. */
data class RegionOption(val code: String, val name: String)

/** Why the sheet pre-selected a region the user might not expect (spec P3-11 → States). */
sealed interface RegionNote {
    /** The selected country has no local sounds yet; [countryName] is in the app's language. */
    data class Outside(val countryName: String) : RegionNote

    /** The phone gave no region, so the one closest to the language was selected (first launch only). */
    data object Unknown : RegionNote
}

/**
 * What the Region sheet opens with. [regions] are the tiles in order: the phone's own country and the confirmed one when
 * they are outside the five launch regions, then the five. [languageNote] is the phone's language when it is not one of
 * the five (first launch only; English is then selected).
 */
data class RegionSetup(
    val firstRun: Boolean,
    val regions: List<RegionOption>,
    val selectedRegionCode: String,
    val regionNote: RegionNote?,
    val language: AppLanguage,
    val canChangeLanguage: Boolean,
    val languageNote: String?,
)

class GetRegionSetupUseCase(
    private val preferences: RegionPreferences,
    private val deviceRegion: DeviceRegion,
    private val languageSwitch: AppLanguageSwitch,
) {
    suspend operator fun invoke(): RegionSetup {
        val firstRun = !preferences.firstRunDone()
        val language = languageSwitch.current.value
        val saved = preferences.savedRegionCode()?.uppercase()
        val phone = deviceRegion.regionCode()?.uppercase()
        val selected = saved ?: phone ?: LaunchRegions.closestTo(language)
        val unreadable = saved == null && phone == null
        val outside = listOfNotNull(selected, phone).distinct().filterNot { LaunchRegions.contains(it) }
        return RegionSetup(
            firstRun = firstRun,
            regions = (outside + LaunchRegions.codes).map { RegionOption(it, deviceRegion.displayName(it, language)) },
            selectedRegionCode = selected,
            regionNote = when {
                !LaunchRegions.contains(selected) -> RegionNote.Outside(deviceRegion.displayName(selected, language))
                unreadable -> RegionNote.Unknown
                else -> null
            },
            language = language,
            canChangeLanguage = languageSwitch.canChange,
            languageNote = if (firstRun) unsupportedPhoneLanguageName() else null,
        )
    }

    private fun unsupportedPhoneLanguageName(): String? {
        val code = languageSwitch.phoneLanguageCode() ?: return null
        return if (AppLanguage.fromCode(code) == null) languageSwitch.languageName(code) else null
    }
}
