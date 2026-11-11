package app.memix.feature.sounds

import androidx.compose.runtime.Immutable
import app.memix.core.model.catalog.LocalizedText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
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
    /** The whole sound's length, for the playhead of a playing row. */
    val durationUs: Long,
)

/** Where a sound's audio is (spec P3-03 → Facts, "On this phone"). Downloads arrive with P3-04, which adds a saved state. */
enum class SoundOnPhone { INCLUDED, NOT_SAVED }

/** What the details sheet shows about one sound; the facts the catalog has, ready to draw (spec P3-03 → Layout). */
@Immutable
data class SoundDetailsUi(
    val id: String,
    val title: LocalizedText,
    val durationUs: Long,
    /** "0:02". */
    val length: String,
    /** Null when the catalog has no such category; the pair is not drawn. */
    val categoryName: LocalizedText?,
    /** Exactly as stored, or null: the pair is not drawn. */
    val credit: String?,
    /** `CC0`, `CC-BY`, `royalty-free`, `own` or `licensed`; the sheet turns it into a sentence. */
    val licenseType: String,
    /** The page of the work, only when it is a web address; the host is what the link shows. */
    val sourceUrl: String?,
    val sourceHost: String?,
    val onPhone: SoundOnPhone,
)

/** The row that opened the details sheet, so closing it can send focus back (a sound can be in two sections). */
@Immutable
data class RowFocusTarget(val soundId: String, val fromRecent: Boolean)

/** How a row's sound is doing: a paused row keeps its waveform where it stopped. */
enum class RowPlayback { IDLE, PLAYING, PAUSED }


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

enum class SoundsToast { PLAY_FAILED, REFRESH_FAILED, VOLUME_LOW, SOURCE_FAILED }

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
    /** The sound playing now. */
    val playingId: String? = null,
    /** The sound held part-way by a second tap on its disc; the next tap carries on. */
    val pausedId: String? = null,
    /** The open details sheet, or null. */
    val details: SoundDetailsUi? = null,
    /** Peaks of the sounds that played or opened details this session (linear, 0 to 1); a sound with none shows the rail. */
    val waveforms: ImmutableMap<String, List<Float>> = persistentMapOf(),
    /** Set when the sheet closes; the row that opened it takes focus, then [SoundsIntent.FocusReturned] clears it. */
    val focusReturn: RowFocusTarget? = null,
    /** The sounds with a filled heart, on every tab. */
    val favoriteIds: ImmutableSet<String> = persistentSetOf(),
    /** How many sounds the last settled search found; read out as a status message, never drawn. */
    val spokenResultCount: Int? = null,
    val toast: SoundsToast? = null,
) {
    /** The chips show on the catalog tabs while the search box is empty (spec P3-02 → Layout 4). */
    val showsChips: Boolean get() = tab.listsCatalog && query.isEmpty() && categories.isNotEmpty()

    /** The playing or paused sound, which is the one that shows a waveform. */
    val activeId: String? get() = playingId ?: pausedId

    fun playbackOf(soundId: String): RowPlayback = when (soundId) {
        playingId -> RowPlayback.PLAYING
        pausedId -> RowPlayback.PAUSED
        else -> RowPlayback.IDLE
    }
}

sealed interface SoundsIntent {
    data class SelectTab(val tab: SoundsTab) : SoundsIntent
    data class SelectCategory(val categoryId: String?) : SoundsIntent
    data class QueryChanged(val text: String) : SoundsIntent
    data object ClearQuery : SoundsIntent
    /**
     * The disc: play, pause the playing sound, or resume the paused one. [fromRecent]: the tap was in the Recently used
     * section, which `sound_preview` names as its own `tab`.
     */
    data class TogglePlay(val soundId: String, val fromRecent: Boolean = false) : SoundsIntent

    /** The name block or a long press: open the details sheet for the sound. */
    data class OpenDetails(val soundId: String, val fromRecent: Boolean = false) : SoundsIntent

    /** Close button, scrim, drag down, Back or Escape. */
    data object CloseDetails : SoundsIntent

    /** The disc inside the details sheet; `sound_preview` names the tab that opened the sheet. */
    data object ToggleDetailsPlay : SoundsIntent

    /** The source link found no browser to open it. */
    data object SourceOpenFailed : SoundsIntent

    /** The row that opened the sheet has taken focus back. */
    data object FocusReturned : SoundsIntent

    /** The Sounds tab is on screen (its first frame is done): start loading, and get the player ready for the first tap. */
    data object ScreenShown : SoundsIntent

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
