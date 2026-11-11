package app.memix.core.domain

/** Values the team changes without an app update (Firebase Remote Config). Implemented in :platform:services. */
interface AppConfig {
    /** Fetches and activates the latest values. On failure the cached or default values stay in use. */
    suspend fun refresh(): Outcome<Unit>

    /**
     * Base URL of catalog media (Supabase Storage until P6-07, then the R2 media domain), joined with each row's
     * `file_path` by [app.memix.core.domain.media.mediaUrl]. The Remote Config value `media_base_url`; when that is empty
     * a build-time fallback (docs/TECHNICAL_DESIGN.md); empty when neither is set.
     */
    val mediaBaseUrl: String

    /** The Supabase project URL of the sound catalog (P3-01), from the build; empty when the build has none. */
    val supabaseUrl: String

    /** The Supabase publishable key, the only key the app ships (docs/CREDENTIALS.md); empty when the build has none. */
    val supabasePublishableKey: String
}
