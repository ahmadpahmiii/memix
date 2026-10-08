package app.memix.feature.videoeditor.importmedia

import androidx.compose.runtime.Immutable
import app.memix.core.domain.media.ImportFailure
import app.memix.core.model.project.MediaOrigin
import kotlinx.collections.immutable.ImmutableList

/** What the import sheet shows (spec docs/ux/specs/P1-02-gallery-picker.md → States). */
@Immutable
sealed interface ImportUiState {
    /** No import running; no sheet. */
    data object Idle : ImportUiState

    /**
     * Copying and checking the picked files. The sheet stays hidden for the first 300 ms ([sheetShown] false), so a
     * quick copy goes straight to the editor. [limit] is set when the pick was over the limit (file chooser only).
     */
    data class Copying(val sheetShown: Boolean, val progress: CopyProgressUi, val limit: Int?) : ImportUiState

    /** Checked before copying, or the phone ran out while copying. Sizes include the headroom. */
    data class NotEnoughSpace(val neededBytes: Long, val freeBytes: Long, val canFreeUpSpace: Boolean) : ImportUiState

    /** At least one item went in and the project is saved; Continue opens it. */
    data class SomeNotAdded(
        val projectId: String,
        val addedCount: Int,
        val pickedCount: Int,
        val notAdded: ImmutableList<NotAddedItemUi>,
        val limit: Int?,
    ) : ImportUiState

    /** Nothing went in; no project. */
    data class NoneAdded(val notAdded: ImmutableList<NotAddedItemUi>) : ImportUiState

    /** The phone has no app that can pick media. */
    data object NoPicker : ImportUiState

    /** Everything went in: the app opens the editor, then sends [ImportIntent.EditorOpened]. */
    data class OpenEditor(val projectId: String) : ImportUiState
}

/** Whether the import sheet is up. */
val ImportUiState.showsSheet: Boolean
    get() = when (this) {
        is ImportUiState.Copying -> sheetShown
        is ImportUiState.NotEnoughSpace, is ImportUiState.SomeNotAdded, is ImportUiState.NoneAdded, ImportUiState.NoPicker -> true
        ImportUiState.Idle, is ImportUiState.OpenEditor -> false
    }

/**
 * Whether the sheet is the blocking variant: no grabber or close button, and scrim taps do nothing. Copying must
 * finish or be cancelled, and a partial result must be acknowledged; the other results close like any sheet.
 */
val ImportUiState.isBlockingSheet: Boolean
    get() = when (this) {
        is ImportUiState.Copying, is ImportUiState.SomeNotAdded -> true
        is ImportUiState.NotEnoughSpace, is ImportUiState.NoneAdded, ImportUiState.NoPicker -> false
        ImportUiState.Idle, is ImportUiState.OpenEditor -> false
    }

/** The copying sheet's numbers, already throttled to at most 10 changes a second. */
@Immutable
data class CopyProgressUi(
    /** The item being copied now, from 1; never past [itemCount]. */
    val itemNumber: Int,
    val itemCount: Int,
    /** 0 to 1, for the bar. */
    val fraction: Float,
    /** Rounded down and at most 99 while copying, so it shows 100% only once the copy is done. */
    val percent: Int,
    /** Only after 2 s of copying, when every size is known and the estimate is over 10 s. */
    val timeLeft: TimeLeft?,
    /** Bytes copied of the current file, only while a file of unknown size copies (there's no time left then). */
    val currentFileCopiedBytes: Long?,
) {
    companion object {
        fun startingAt(itemNumber: Int, itemCount: Int) = CopyProgressUi(itemNumber, itemCount, 0f, 0, null, null)

        /** Every item copied and checked. */
        fun finished(itemCount: Int) = CopyProgressUi(itemCount, itemCount, 1f, 100, null, null)
    }
}

/** Seconds in 5 s steps under a minute, whole minutes above (always rounded up). */
sealed interface TimeLeft {
    data class Seconds(val value: Int) : TimeLeft
    data class Minutes(val value: Int) : TimeLeft
}

/** One row of the result sheet: an item that didn't go in. */
@Immutable
data class NotAddedItemUi(
    /** Null when the source gave no name; the row then says "Video" or "Photo". */
    val displayName: String?,
    /** False also when the type is unknown, so the row falls back to "Photo". */
    val isVideo: Boolean,
    val reason: ImportFailure,
)

sealed interface ImportIntent {
    /** The picker returned; an empty list means nothing was picked. */
    data class Picked(val items: List<MediaOrigin>) : ImportIntent

    /** The picker couldn't open: the phone has no app for it. */
    data object PickerUnavailable : ImportIntent

    /** Cancel button, or system back while copying. */
    data object Cancel : ImportIntent

    /** Continue button, or system back on "Some didn't go in". */
    data object Continue : ImportIntent

    /** Close, scrim, drag or back on the standard result sheets. */
    data object Close : ImportIntent

    /** The app is in the foreground again, maybe after the user freed space. */
    data object AppResumed : ImportIntent

    /** Neither storage screen opened, so the "Free up space" button goes away. */
    data object FreeUpSpaceUnavailable : ImportIntent

    /** The app has navigated to the editor for [ImportUiState.OpenEditor]. */
    data object EditorOpened : ImportIntent
}
