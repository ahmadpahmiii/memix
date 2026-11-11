package app.memix.core.data.catalog

import app.memix.core.model.catalog.SoundFeed

internal const val GLOBAL_REGION_TAG = "global"
private const val ALL_CATEGORIES = "all"

/** Whether the feed is one short list (trending) rather than pages of 30. */
internal val SoundFeed.isSinglePage: Boolean get() = this is SoundFeed.Trending

/**
 * The name of the feed's saved list. Trending keeps its category out of the name: it is one short list that the
 * repository filters when reading, so switching chips never needs the network.
 */
internal fun SoundFeed.cacheKey(): String = when (this) {
    is SoundFeed.Trending -> "trending:${regionCode?.uppercase() ?: GLOBAL_REGION_TAG}"
    is SoundFeed.Local -> "local:${regionCode.uppercase()}:${categoryId ?: ALL_CATEGORIES}"
    is SoundFeed.Global -> "global:${categoryId ?: ALL_CATEGORIES}"
}
