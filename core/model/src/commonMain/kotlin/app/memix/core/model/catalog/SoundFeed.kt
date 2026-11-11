package app.memix.core.model.catalog

/** One list on the Sounds screen. [categoryId] is the selected chip, or null for "All". */
sealed interface SoundFeed {
    val categoryId: String?

    /** This month's trending for [regionCode] (null: no region known), then global, without repeats. */
    data class Trending(val regionCode: String?, override val categoryId: String? = null) : SoundFeed

    /** Sounds tagged for [regionCode], newest first. */
    data class Local(val regionCode: String, override val categoryId: String? = null) : SoundFeed

    /** Sounds tagged `global`, newest first. */
    data class Global(override val categoryId: String? = null) : SoundFeed
}

/**
 * What a feed holds right now, as saved on the phone. [loadedPages] is 0 until the first page has ever arrived, which
 * is how the screen tells "nothing cached yet" from "loaded and empty". [endReached] is true once the last page is in.
 */
data class SoundFeedContent(
    val sounds: List<LibrarySound>,
    val loadedPages: Int,
    val endReached: Boolean,
) {
    companion object {
        val NotLoaded = SoundFeedContent(sounds = emptyList(), loadedPages = 0, endReached = false)
    }
}
