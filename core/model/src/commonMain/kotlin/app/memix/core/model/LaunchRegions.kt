package app.memix.core.model

/**
 * The five regions Memix launches with, one per app language, in the order the Region sheet lists them. Catalog rows use
 * the same ISO 3166-1 alpha-2 codes. A phone in another country is still kept as its own region (it has no local sounds
 * until the catalog adds some), so this list only decides which tiles the sheet always offers.
 */
object LaunchRegions {
    val codes: List<String> = listOf("ID", "US", "IN", "BR", "MX")

    fun contains(regionCode: String): Boolean = codes.any { it.equals(regionCode, ignoreCase = true) }

    /** The launch region whose people mostly read [language]; the default when the phone has no readable region. */
    fun closestTo(language: AppLanguage): String = when (language) {
        AppLanguage.INDONESIAN -> "ID"
        AppLanguage.ENGLISH -> "US"
        AppLanguage.HINDI -> "IN"
        AppLanguage.PORTUGUESE -> "BR"
        AppLanguage.SPANISH -> "MX"
    }
}
