package app.memix.core.domain.region

import app.memix.core.domain.DeviceRegion
import app.memix.core.model.Region
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The region the Local tab, Trending and Home use: the one confirmed in the Region sheet, else the phone's own (so the
 * screens behind the first-launch sheet already show something), else [Region.Global]. A country with no local sounds
 * stays a [Region.Country]. The name follows the app language, so a language change re-emits.
 */
class ObserveRegionUseCase(
    private val preferences: RegionPreferences,
    private val deviceRegion: DeviceRegion,
    private val languageSwitch: AppLanguageSwitch,
) {
    operator fun invoke(): Flow<Region> =
        combine(preferences.observeRegionCode(), languageSwitch.current) { saved, language ->
            val code = (saved ?: deviceRegion.regionCode())?.uppercase()
            if (code == null) Region.Global else Region.Country(code, deviceRegion.displayName(code, language))
        }.distinctUntilChanged()
}
