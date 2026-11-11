package app.memix.feature.sounds

import androidx.compose.runtime.Immutable
import app.memix.core.model.catalog.LocalizedText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** The five tabs, in the order they are drawn (spec P3-02 → Layout). */
enum class SoundsTab {
    TRENDING,
    LOCAL,
    GLOBAL,
    FAVORITES,
    MINE,
    ;

    /** Trending, Local and Global list the catalog, so they show the category chips and search the whole catalog. */
    val listsCatalog: Boolean get() = this == TRENDING || this == LOCAL || this == GLOBAL
}

/** One sound row, reduced to what is drawn. The name is chosen in the screen, because the language is known there. */
@Immutable
data class SoundItemUi(
    val id: String,
    val title: LocalizedText,
    /** "0:02". */
    val length: String,
    /** The license credit exactly as the catalog has it; never translated and never cut. */
    val credit: String,
)

@Immutable
data class CategoryChipUi(val id: String, val name: LocalizedText)

/** What fills the list under the tabs. */
sealed interface SoundsContent {
    /** The first load has nothing saved yet. The placeholder rows appear only after 300 ms, so a fast load never flashes them. */
    data class Loading(val showPlaceholders: Boolean) : SoundsContent

    /** Nothing to show and the catalog (or the search) can't be reached right now. */
    data object LoadError : SoundsContent

    data class Empty(val reason: EmptyReason) : SoundsContent

    data class Rows(val rows: ImmutableList<SoundItemUi>, val footer: ListFooter) : SoundsContent

    /**
     * The Favorites tab (spec P3-02 → Tab contents): up to five recently used sounds, then every favorite. A sound
     * can be in both. An empty section is not drawn, except that [favorites] empty under a filled [recent] shows its hint.
     */
    data class Favorites(val recent: ImmutableList<SoundItemUi>, val favorites: ImmutableList<SoundItemUi>) : SoundsContent
}

enum class EmptyReason {
    TRENDING,
    LOCAL,

    /** The phone has no region; the Local tab can't pick sounds (P3-11's Region sheet fixes this later). */
    LOCAL_UNKNOWN,
    GLOBAL,
    CATEGORY,
    FAVORITES,
    MINE,
    SEARCH_NONE,
    SEARCH_NONE_OFFLINE,
}

/** The last item under the rows while more pages load or when one failed. */
enum class ListFooter { NONE, LOADING_MORE, MORE_FAILED }

/** The one status strip above the rows. Searching beats the offline notes, and the search note beats the plain one. */
enum class SoundsNotice { OFFLINE, SEARCH_OFFLINE, SEARCHING }

enum class SoundsToast { PLAY_FAILED, REFRESH_FAILED }

@Immutable
data class SoundsUiState(
    val tab: SoundsTab = SoundsTab.TRENDING,
    val query: String = "",
    /** The catalog's categories in its order; the chip row is "All" plus these. */
    val categories: ImmutableList<CategoryChipUi> = persistentListOf(),
    /** The selected chip, or null for "All". Reset when the tab changes. */
    val categoryId: String? = null,
    /** The phone's region name, or null when it has none. */
    val regionName: String? = null,
    val content: SoundsContent = SoundsContent.Loading(showPlaceholders = false),
    val notice: SoundsNotice? = null,
    val playingId: String? = null,
    /** The sounds with a filled heart, on every tab. */
    val favoriteIds: ImmutableSet<String> = persistentSetOf(),
    /** How many sounds the last settled search found; read out as a status message, never drawn. */
    val spokenResultCount: Int? = null,
    val toast: SoundsToast? = null,
) {
    /** The chips show on the catalog tabs while the search box is empty (spec P3-02 → Layout 4). */
    val showsChips: Boolean get() = tab.listsCatalog && query.isEmpty() && categories.isNotEmpty()
}

sealed interface SoundsIntent {
    data class SelectTab(val tab: SoundsTab) : SoundsIntent
    data class SelectCategory(val categoryId: String?) : SoundsIntent
    data class QueryChanged(val text: String) : SoundsIntent
    data object ClearQuery : SoundsIntent
    /** [fromRecent]: the tap was in the Recently used section, which `sound_preview` names as its own `tab`. */
    data class TogglePlay(val soundId: String, val fromRecent: Boolean = false) : SoundsIntent

    /** The heart: favorite the sound, or take it out of the favorites. */
    data class ToggleFavorite(val soundId: String) : SoundsIntent

    /** The list is near its end: bring in the next page. */
    data object LoadMore : SoundsIntent
    data object TryAgain : SoundsIntent

    /** Pulled down at the top of the list. */
    data object Refresh : SoundsIntent

    /** The app went to the background: a preview would keep playing. */
    data object AppStopped : SoundsIntent

    /** The user moved to another bottom-nav item: the search text clears and the preview stops (tab and chip are kept). */
    data object LeftScreen : SoundsIntent
}
