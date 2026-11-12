package app.memix.core.data.supabase

private const val SECRET_KEY_PREFIX = "sb_secret_"

/**
 * Where the catalog lives: the Supabase project URL and its publishable key (the only key the app ships; row level
 * security lets it read active rows and file reports). Both come from the build (`local.properties`), never from
 * source. Empty on a checkout that has not set them: the catalog then serves from the phone's copy only.
 */
internal class SupabaseConfig(val url: String, val publishableKey: String) {
    /**
     * A secret key is refused here as well as by the build (androidApp checks the key at build time): the app never
     * sends one, whatever ended up in the manifest.
     */
    val isComplete: Boolean
        get() = url.isNotBlank() && publishableKey.isNotBlank() && !publishableKey.startsWith(SECRET_KEY_PREFIX)
}
