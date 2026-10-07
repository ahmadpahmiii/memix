package app.memix.core.domain

/** Region settings of the phone. Implemented in :platform:services. */
interface DeviceRegion {
    /** ISO 3166-1 alpha-2 code, or null when the phone has no region set. */
    fun regionCode(): String?

    /** Name of [regionCode] in the phone's language, e.g. "Indonesia". */
    fun displayName(regionCode: String): String
}
