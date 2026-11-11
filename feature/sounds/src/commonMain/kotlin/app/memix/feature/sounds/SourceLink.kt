package app.memix.feature.sounds

/**
 * The host of a web address as the details sheet shows it ("freesound.org"), or null when [url] is not an http or
 * https address (the catalog also holds `own:<where recorded>` for Memix's own recordings, which has no page to open).
 */
internal fun sourceHostOf(url: String): String? {
    val afterScheme = WEB_SCHEMES.firstNotNullOfOrNull { scheme ->
        if (url.startsWith(scheme, ignoreCase = true)) url.substring(scheme.length) else null
    } ?: return null
    val authority = afterScheme.takeWhile { it != '/' && it != '\\' && it != '?' && it != '#' }.substringAfterLast('@')
    // An IPv6 address is in brackets and holds colons of its own; otherwise the port starts at the first colon.
    val host = (if (authority.startsWith("[")) authority.substringBefore(']') + "]" else authority.substringBefore(':')).removePrefix("www.")
    return host.ifEmpty { null }
}

private val WEB_SCHEMES = listOf("https://", "http://")
