package app.memix.core.domain

/** Values the team changes without an app update (Firebase Remote Config). Implemented in :platform:services. */
interface AppConfig {
    /** Fetches and activates the latest values. On failure the cached or default values stay in use. */
    suspend fun refresh(): Outcome<Unit>

    /** Base URL of catalog media on R2, joined with each row's `file_path`. Empty until the media domain exists. */
    val mediaBaseUrl: String
}
