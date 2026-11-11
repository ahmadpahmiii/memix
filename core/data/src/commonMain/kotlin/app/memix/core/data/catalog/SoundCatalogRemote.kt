package app.memix.core.data.catalog

import app.memix.core.model.catalog.LibrarySound
import app.memix.core.model.catalog.SoundCategory

/** The catalog's server side, as the repository needs it. [SupabaseSoundCatalogRemote] is the real one. */
internal interface SoundCatalogRemote {
    suspend fun categories(): List<SoundCategory>

    /** Active sounds whose regions include [regionTag] (`global` or a country code), newest first. */
    suspend fun sounds(regionTag: String, categoryId: String?, offset: Int, limit: Int): List<RemoteSound>

    /** The trending ranks of sounds for [month] (`2026-11-01`) in any of [regionTags]. */
    suspend fun trendingRanks(month: String, regionTags: List<String>): List<TrendingRank>

    /** The active sounds among [ids], in any order; sounds that were switched off are missing. */
    suspend fun soundsByIds(ids: List<String>): List<RemoteSound>

    /** The typo-tolerant `search_sounds` function. */
    suspend fun search(query: String, regionTag: String, offset: Int, limit: Int): List<RemoteSound>

    /** The text at [url], or null when the file is not there. Fails with [CatalogUnreachableException] with no network. */
    suspend fun fetchText(url: String): String?
}

/** A sound row plus the tags only search uses. */
internal class RemoteSound(val sound: LibrarySound, val tags: List<String>)

internal class TrendingRank(val regionTag: String, val rank: Int, val soundId: String)

/** The catalog could not be reached: no network, a timeout, or no Supabase configuration in this build. */
internal class CatalogUnreachableException(message: String, cause: Throwable? = null) : Exception(message, cause)
