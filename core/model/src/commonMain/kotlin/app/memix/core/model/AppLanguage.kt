package app.memix.core.model

/**
 * The five languages Memix ships in (PRD). [code] is the BCP 47 language subtag and the value of the analytics `language`
 * parameter. [nativeName] is the language written in itself: it is never translated, so a person can always find theirs.
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    INDONESIAN("id", "Bahasa Indonesia"),
    SPANISH("es", "Español"),
    PORTUGUESE("pt", "Português"),
    HINDI("hi", "हिन्दी"),
    ;

    companion object {
        /** The language Memix falls back to when the phone's is not one of the five. */
        val Default = ENGLISH

        /** The supported language with this [code] (case-insensitive), or null for any other. */
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }

        /**
         * The supported language a locale tag ("hi-IN", "in_ID", "pt-BR") names, or null for any other. The one place that
         * knows Java's legacy codes: its `Locale.getLanguage()` can return "in" for Indonesian, so never compare a raw
         * `language` with [code].
         */
        fun fromTag(tag: String?): AppLanguage? = fromCode(languageSubtag(tag))

        /** The lowercase language subtag of [tag] with legacy codes mapped to current ones (in to id, iw to he, ji to yi); null when empty. */
        fun languageSubtag(tag: String?): String? {
            val subtag = tag?.substringBefore('-')?.substringBefore('_')?.lowercase()?.takeIf { it.isNotEmpty() && it != "und" } ?: return null
            return when (subtag) {
                "in" -> "id"
                "iw" -> "he"
                "ji" -> "yi"
                else -> subtag
            }
        }
    }
}
