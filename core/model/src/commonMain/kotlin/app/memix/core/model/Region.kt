package app.memix.core.model

/** Where trending lists and the Local sounds tab come from. Catalog rows use the same codes. */
sealed interface Region {
    data object Global : Region

    /** [code] is ISO 3166-1 alpha-2 ("ID"); [displayName] is in the phone's language. */
    data class Country(val code: String, val displayName: String) : Region
}
