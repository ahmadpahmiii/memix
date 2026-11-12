package app.memix.feature.sounds

import androidx.compose.runtime.Immutable
import app.memix.core.designsystem.component.SoundRowLook
import app.memix.core.domain.mysound.MySoundSource
import app.memix.core.domain.sound.SoundReportReason
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.model.project.MediaOrigin
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

/** Which screen a [SoundsViewModel] serves: the Sounds tab, or the video editor's sound picker sheet (P3-05). */
enum class SoundsMode { LIBRARY, PICKER }

/**
 * The tab the picker last showed during this app run (spec P3-05 → Entry and exit): the next opening starts there. A
 * Koin single, so it outlives the editor; nothing is saved, and a new run starts on Trending.
 */
class SoundPickerMemory {
    var tab: SoundsTab = SoundsTab.TRENDING
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
    /** Set for a My sound (P3-07): no heart, its meta says where it came from, and a blank title is drawn as a default name. */
    val mine: MySoundSource? = null,
)

/** Where a sound's audio is (spec P3-03 → Facts, "On this phone"): in the app, downloaded, or not on the phone yet. */
enum class SoundOnPhone { INCLUDED, SAVED, NOT_SAVED }

/** A download the user can see (spec P3-04): running past 300 ms, or failed. A quick one never gets an entry. */
sealed interface SoundDownloadUi {
    /** [progress] is 0 to 1, or null while the server has not said how big the file is (the rail shows empty). */
    data class Downloading(val progress: Float?) : SoundDownloadUi

    /** [storageFull]: the phone has no room, so the row says that instead of "try again". */
    data class Failed(val storageFull: Boolean) : SoundDownloadUi
}

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
    /** Set for a My sound: the sheet shows its origin as the credit and has Rename and Delete instead of the catalog facts and Report. */
    val mine: MySoundSource? = null,
)

/** Whether this row is a My sound. */
val SoundItemUi.isMine: Boolean get() = mine != null

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

    /**
     * Offline with no saved catalog (first launch): the bundled starter pack as normal rows under a "Starter sounds"
     * heading (spec P3-04 → Offline). Rows are the same as [Rows]; they play, open details and take a heart.
     */
    data class Starters(val rows: ImmutableList<SoundItemUi>) : SoundsContent

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

enum class SoundsToast { PLAY_FAILED, REFRESH_FAILED, VOLUME_LOW, SOURCE_FAILED, REPORT_SENT, MY_SOUND_ADDED, MY_SOUND_SAVE_FAILED }

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
    /** The bundled starter pack: always on the phone. */
    val starterIds: ImmutableSet<String> = persistentSetOf(),
    /** The sounds downloaded from the library (P3-04). */
    val downloadedIds: ImmutableSet<String> = persistentSetOf(),
    /** Favorites and recents the catalog no longer lists; they show "No longer available". */
    val removedIds: ImmutableSet<String> = persistentSetOf(),
    /** Whether the phone has a connection. Without one, sounds that are not on the phone are dimmed. */
    val online: Boolean = true,
    /** Downloads past 300 ms and failed ones, by sound id. */
    val downloads: ImmutableMap<String, SoundDownloadUi> = persistentMapOf(),
    /** The phone-is-full toast with "Free up space" (spec P3-04): shown after a download hit a full phone, until dismissed. */
    val storageFullNote: Boolean = false,
    /** The picker's row whose sound is being made ready to land: its Add block is disabled until the sheet closes. */
    val addingId: String? = null,
    /** False once the phone showed it has no storage screen; the toast then has no action (as P1-02). */
    val canFreeUpSpace: Boolean = true,
    /** The sounds this phone has reported: the details show "Reported. Thank you." instead of the Report row (P3-10). */
    val reportedIds: ImmutableSet<String> = persistentSetOf(),
    /** The details sheet shows the Report pane (spec P3-10) instead of the sound's details. */
    val reportOpen: Boolean = false,
    val report: SoundReportUi = SoundReportUi(),
    /** The ids of My sounds (P3-07): audio on the phone, no heart, no download. */
    val mineIds: ImmutableSet<String> = persistentSetOf(),
    /** The My sounds import sheet, or null. In the picker it is a pane of the sheet. */
    val myImport: MyImportUi? = null,
    /** The sound just deleted, while its Undo toast is up (8 s). */
    val deletedMine: DeletedMineUi? = null,
    /** The details sheet shows the Rename pane. */
    val renameOpen: Boolean = false,
    /** Save was tapped on an empty name: the field says so under itself. Counts the taps so a repeat moves focus again. */
    val renameEmptyCount: Int = 0,
    /** The name of the sound just added (empty when it had none), for the toast's spoken text. */
    val addedName: String = "",
    /** Counts each added sound: the list scrolls to the top and the new row takes focus. */
    val addedCount: Int = 0,
) {
    /** The chips show on the catalog tabs while the search box is empty (spec P3-02 → Layout 4). */
    val showsChips: Boolean get() = tab.listsCatalog && query.isEmpty() && categories.isNotEmpty()

    /** The playing or paused sound, which is the one that shows a waveform. */
    val activeId: String? get() = playingId ?: pausedId

    fun onPhoneOf(soundId: String): SoundOnPhone = when (soundId) {
        in mineIds -> SoundOnPhone.SAVED
        in starterIds -> SoundOnPhone.INCLUDED
        in downloadedIds -> SoundOnPhone.SAVED
        else -> SoundOnPhone.NOT_SAVED
    }

    /** What a row (or the sheet's player) looks like for [soundId]; the order is the spec's (P3-04 → Row states). */
    fun lookOf(soundId: String): SoundRowLook {
        val download = downloads[soundId]
        return when {
            soundId in removedIds -> SoundRowLook.UNAVAILABLE
            download is SoundDownloadUi.Downloading -> SoundRowLook.DOWNLOADING
            // A failure while the connection is gone is just "offline": there is nothing to retry until it returns.
            !online && onPhoneOf(soundId) == SoundOnPhone.NOT_SAVED -> SoundRowLook.OFFLINE_NOT_SAVED
            download is SoundDownloadUi.Failed -> if (download.storageFull) SoundRowLook.FAILED_STORAGE else SoundRowLook.FAILED
            else -> SoundRowLook.NORMAL
        }
    }

    /** The download's progress for [soundId] while it shows as Downloading, else null. */
    fun downloadProgressOf(soundId: String): Float? = (downloads[soundId] as? SoundDownloadUi.Downloading)?.progress

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

    /** The Report row in the details: swaps the sheet's content for the Report pane. */
    data object OpenReport : SoundsIntent

    /** The Report pane's back arrow, system back or Escape: back to the details, the form kept. */
    data object CloseReport : SoundsIntent
    data class ReportReasonChosen(val reason: SoundReportReason) : SoundsIntent

    /** The note field changed; the text is limited to 480 characters before it is kept. */
    data class ReportNoteChanged(val text: String) : SoundsIntent
    data object SendReport : SoundsIntent

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

    /** The × on the phone-is-full toast. */
    data object DismissStorageNote : SoundsIntent

    /** The phone has no storage screen to open: the toast loses its action. */
    data object FreeUpSpaceUnavailable : SoundsIntent

    /** The user moved to another bottom-nav item: the search text clears and the preview stops (tab and chip are kept). */
    data object LeftScreen : SoundsIntent

    /** The picker's Add block: the sound lands at the playhead. [fromRecent]: tapped in Recently used, which `sound_add` names as its own `tab`. */
    data class Add(val soundId: String, val fromRecent: Boolean = false) : SoundsIntent

    /** The picker returned for My sounds' [source]: [origin] is the file or video, null when nothing was picked. */
    data class MySoundPicked(val source: MySoundSource, val origin: MediaOrigin?) : SoundsIntent

    /** The phone has no app that can open the picker for [source]. */
    data class MySoundPickerUnavailable(val source: MySoundSource) : SoundsIntent

    /** Cancel, system back or Escape while converting. */
    data object CancelMySoundImport : SoundsIntent

    /** Close, scrim, drag or back on a result sheet (or "Pick again", which the screen follows with the picker). */
    data object CloseMySoundImport : SoundsIntent

    /** The app is in the foreground again: a waiting import checks the space. */
    data object MySoundImportResumed : SoundsIntent

    /** The Rename row in the details sheet. */
    data object OpenRename : SoundsIntent

    /** The Rename pane's back arrow, system back or Escape: back to the details, nothing saved. */
    data object CloseRename : SoundsIntent

    /** Save in the Rename pane, with the name as typed. */
    data class SaveRename(val typed: String) : SoundsIntent

    /** Delete in the details sheet: the row goes now and the toast offers Undo. */
    data object DeleteMine : SoundsIntent

    /** The toast's Undo. */
    data object UndoDeleteMine : SoundsIntent

    /** The picker's "Add to video" on the details pane. */
    data object AddFromDetails : SoundsIntent

    /** The Sounds tab's Use block (P3-15): a new video meme starts with the sound. [fromRecent]: as for [Add]. */
    data class Use(val soundId: String, val fromRecent: Boolean = false) : SoundsIntent

    /** The details sheet's "Use in a video meme". */
    data object UseFromDetails : SoundsIntent

    /** The system picker a Use opened is gone, with a pick or without: the row takes taps again and the sound may be cleaned up. */
    data object UseFinished : SoundsIntent

    /** The picker's sheet started to close (a sound landed, or it was dismissed): stop what plays and loads. */
    data object PickerClosed : SoundsIntent

    /** The picker's sheet has slid out: the next visit starts fresh (details closed, search cleared). */
    data object PickerExited : SoundsIntent
}
