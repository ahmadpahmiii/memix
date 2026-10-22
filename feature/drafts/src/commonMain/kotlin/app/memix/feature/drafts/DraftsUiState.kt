package app.memix.feature.drafts

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import app.memix.core.domain.drafts.EditedTime
import app.memix.core.model.project.ProjectType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/** What the Drafts tab, its sheets and its toast show (spec docs/ux/specs/P1-14-drafts.md → States). */
@Immutable
data class DraftsUiState(
    /** The rows' pictures, decoded once and kept in memory; compared by identity. */
    val thumbnails: DraftThumbnailImages,
    val list: DraftsList = DraftsList.Loading,
    /** The rename sheet, while it's open. */
    val renaming: RenameUi? = null,
    /** The duplicate's copying or "Not enough space" sheet, while it's up. */
    val copySheet: CopySheetUi? = null,
    val toast: DraftsToast? = null,
    /** Focus to move to a row (after Rename, Delete, Undo or coming back from the editor); a new serial each time. */
    val focus: FocusRequestUi? = null,
    /** A draft to open in its editor; the screen navigates, then sends [DraftsIntent.DraftOpened]. */
    val openDraftId: String? = null,
    /** False once the phone's storage screen failed to open, so Free up space hides. */
    val canFreeUpSpace: Boolean = true,
) {
    /** A sheet is up, so the tabs and the navigation behind it are hidden from screen readers and the keyboard. */
    val sheetUp: Boolean get() = renaming != null || copySheet != null
}

@Immutable
sealed interface DraftsList {
    /** Reading the database: only the title shows (no spinner, spec → States). */
    data object Loading : DraftsList

    /** The database can't be read: a message and Try again. */
    data object LoadFailed : DraftsList

    /** Newest edit first; empty shows the empty state. */
    data class Loaded(val rows: ImmutableList<DraftRowUi>) : DraftsList
}

/** One draft row. [name] is empty for an unnamed draft, which shows a stand-in in the user's language. */
@Immutable
data class DraftRowUi(
    val id: String,
    val name: String,
    val type: ProjectType,
    /** Null for a draft saved by a newer Memix ([needsNewerApp]): its meta line says so instead. */
    val edited: EditedTime?,
    val needsNewerApp: Boolean,
    /** Relative to the app's files directory; null shows the placeholder. */
    val thumbnailPath: String?,
)

/** The rename sheet for [draftId], prefilled with [name] (empty for an unnamed draft: its stand-in is the hint). New [serial] per opening. */
@Immutable
data class RenameUi(val draftId: String, val name: String, val type: ProjectType, val serial: Long)

@Immutable
sealed interface CopySheetUi {
    /** Copying the draft's media (shown only once copying takes more than 300 ms); [progress] goes from 0 to 1. */
    data class Copying(val progress: CopyProgress) : CopySheetUi

    /** Sizes include the headroom. */
    data class NotEnoughSpace(val neededBytes: Long, val freeBytes: Long) : CopySheetUi
}

/** The copy's progress, read only by the sheet's bar, so a new value doesn't recompose the list. */
@Stable
class CopyProgress(val fraction: StateFlow<Float>)

@Immutable
sealed interface DraftsToast {
    val serial: Long

    /** "Draft deleted." with Undo, for as long as the delete can be undone. */
    data class Deleted(val draftId: String, override val serial: Long) : DraftsToast

    data class Duplicated(override val serial: Long) : DraftsToast

    /** "This draft can't be opened. Update Memix, or delete it." */
    data class CantOpen(override val serial: Long) : DraftsToast
}

/** Focus [draftId]'s row, scrolling to the top first when [scrollToTop] (the draft just edited is now first). */
@Immutable
data class FocusRequestUi(val draftId: String, val scrollToTop: Boolean, val serial: Long)

sealed interface DraftsIntent {
    /** The tab is on screen (and on every return to the foreground): the list loads and the edit times refresh. */
    data object Shown : DraftsIntent

    /** The tab left the screen or the app went to the background: the minute tick stops. */
    data object Hidden : DraftsIntent

    data object TryAgain : DraftsIntent
    data class Open(val draftId: String) : DraftsIntent
    data object DraftOpened : DraftsIntent

    data class Rename(val draftId: String) : DraftsIntent
    data class SaveName(val typedName: String) : DraftsIntent
    data object CloseRename : DraftsIntent

    data class Duplicate(val draftId: String) : DraftsIntent
    data object CancelCopy : DraftsIntent
    data object CloseSpaceSheet : DraftsIntent
    data object FreeUpSpaceUnavailable : DraftsIntent

    /** The app is in the foreground again, maybe after the user freed space: a duplicate that didn't fit tries again. */
    data object AppResumed : DraftsIntent

    /** [undoWindowMillis]: 8 s, or longer when the user's "Time to take action" setting asks for it. */
    data class Delete(val draftId: String, val undoWindowMillis: Long) : DraftsIntent
    data class Undo(val draftId: String) : DraftsIntent

    data class ToastTimedOut(val serial: Long) : DraftsIntent
    data class FocusDone(val serial: Long) : DraftsIntent

    /** The editor couldn't open a draft (damaged, from a newer app, deleted meanwhile); [serial] says which time. */
    data class EditorCouldntOpen(val serial: Long) : DraftsIntent
}
