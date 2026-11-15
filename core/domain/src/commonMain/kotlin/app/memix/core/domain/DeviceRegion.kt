package app.memix.core.domain

import app.memix.core.model.AppLanguage

/** Region settings of the phone. Implemented in :platform:services. */
interface DeviceRegion {
    /** ISO 3166-1 alpha-2 code from the phone's regional setting (no location permission), or null when the phone has none. */
    fun regionCode(): String?

    /** Name of [regionCode] written in [language], e.g. "Indonesia". Pure: the same input gives the same name. */
    fun displayName(regionCode: String, language: AppLanguage): String
}
