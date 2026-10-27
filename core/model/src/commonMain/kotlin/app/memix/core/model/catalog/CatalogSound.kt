package app.memix.core.model.catalog

/**
 * A meme sound the user can put on the timeline. In P1 these come from the starter pack bundled with the app; the
 * online catalog (P3) uses the same ids, so a project keeps working when its sounds later come from there.
 */
data class CatalogSound(
    /** Permanent catalog id; projects refer to the sound by it (`MediaOrigin.CatalogItem`). */
    val id: String,
    val title: LocalizedText,
    /** How long the whole sound plays, in microseconds, as the catalog lists it. */
    val durationUs: Long,
    /**
     * What the sound's meta line credits, exactly as the license record has it: an attribution the license asks
     * for, else the license type ("CC0"). Never translated.
     */
    val credit: String,
)

/** Text the catalog carries in each app language, keyed by language code ("en", "id", "es", "pt", "hi"). */
data class LocalizedText(val byLanguage: Map<String, String>) {
    /** The text in [languageCode], else in English, else in any language there is. */
    fun inLanguage(languageCode: String): String =
        byLanguage[languageCode] ?: byLanguage[FALLBACK_LANGUAGE] ?: byLanguage.values.firstOrNull().orEmpty()

    private companion object {
        const val FALLBACK_LANGUAGE = "en"
    }
}
