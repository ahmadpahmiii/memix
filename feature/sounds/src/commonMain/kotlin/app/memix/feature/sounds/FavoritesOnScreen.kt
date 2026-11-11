package app.memix.feature.sounds

import app.memix.core.model.catalog.LibrarySound

/**
 * The Favorites section while the user is on the tab (spec P3-02 → Tab contents): a sound that was un-hearted stays
 * where it was, with its heart off, until the user leaves the tab or pulls to refresh, so a mis-tap is undone by the
 * same tap and the rows never shift under a finger. A sound hearted since [onScreen] was built goes on top, as the
 * newest favorite; one hearted again takes its old place back.
 */
internal fun keepUnfavoritedRows(latest: List<LibrarySound>, onScreen: List<LibrarySound>): List<LibrarySound> {
    val latestById = latest.associateBy { it.id }
    val onScreenIds = onScreen.mapTo(HashSet()) { it.id }
    val newlyFavorited = latest.filter { it.id !in onScreenIds }
    return newlyFavorited + onScreen.map { latestById[it.id] ?: it }
}
