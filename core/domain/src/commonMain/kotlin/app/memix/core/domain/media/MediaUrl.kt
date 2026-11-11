package app.memix.core.domain.media

/**
 * The download address of a catalog file: `media_base_url` joined with the row's `file_path` (`sounds/2026/11/<id>.m4a`).
 * Returns null while no base URL is configured, so callers show "unavailable" instead of requesting a broken address.
 * The base never ends up in source code: it comes from Remote Config, or from `local.properties` when that is empty.
 */
fun mediaUrl(baseUrl: String, filePath: String): String? {
    val base = baseUrl.trim().trimEnd('/')
    val path = filePath.trim().trimStart('/')
    return if (base.isEmpty() || path.isEmpty()) null else "$base/$path"
}
