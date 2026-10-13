package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import app.memix.core.domain.project.TrimEdge
import app.memix.core.domain.video.PreviewSession
import app.memix.feature.videoeditor.timeline.ItemName
import app.memix.feature.videoeditor.timeline.TimelineThumbnails
import app.memix.feature.videoeditor.timeline.TrimRanges
import app.memix.feature.videoeditor.timeline.TimelineUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/** What the video editor shows (spec docs/ux/specs/P1-04-editor-and-preview.md → States). */
@Immutable
data class VideoEditorUiState(
    /** Null until the draft has opened. */
    val canvas: CanvasUi? = null,
    /** How long the project plays, in µs: where its last main video clip ends. */
    val lengthUs: Long = 0,
    val stage: StageContent = StageContent.OPENING,
    /** The live preview, once the draft has opened and has clips. */
    val preview: EditorPreview? = null,
    /** The project's tracks and items as the timeline draws them (spec P1-05). */
    val timeline: TimelineUi = TimelineUi.Empty,
    /** The one selected timeline item; null when nothing is. Kept through edits and undo while the item exists. */
    val selectedItemId: String? = null,
    /** The clip tool bar's tools for the selected item; null shows the main tool bar (spec P1-06 → Layout). */
    val clipTools: ClipTools? = null,
    /** Where the selected item's handles can trim it to; null with nothing selected. */
    val trimRanges: TrimRanges? = null,
    /** Reorder mode (spec P1-06 → Reorder): the main clips as tiles in their new order; null otherwise. */
    val reorder: ReorderUi? = null,
    /** The item that just landed (a duplicate): the timeline bonks it once per new [LandedItem.serial]. */
    val landed: LandedItem? = null,
    /** The thumbnail tiles of the timeline's strips, once the draft has opened. Compared by identity. */
    val thumbnails: TimelineThumbnails? = null,
    /** The play button shows Pause; switches as soon as play is asked for. */
    val isPlaying: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** True while the latest changes aren't saved; leaving then asks first. */
    val isSaveFailing: Boolean = false,
    /** The banner on the stage while saving fails; null once the user dismissed it. */
    val saveBanner: SaveProblem? = null,
    /** Goes up by one each time saving works again after failing, so a screen reader hears "Changes saved". */
    val savedAgainCount: Int = 0,
    /** False once the phone's storage screen failed to open: Free up space then hides (never a button that does nothing). */
    val canFreeUpSpace: Boolean = true,
    val toast: EditorToast? = null,
    /**
     * "Updating the preview" in the stage's message slot: an edit hasn't reached the preview after a second (spec
     * P1-06 → Preview catching up). Shown only when the save banner and toasts leave the slot free.
     */
    val previewUpdating: Boolean = false,
    /** "Your latest changes aren't saved": leaving while saving fails. */
    val unsavedSheetOpen: Boolean = false,
    /** The editor is done; the app closes the screen. */
    val isClosing: Boolean = false,
    /** Debug and benchmark builds: a long-press on the timecode makes one test edit, for the P1-07 hand checks. */
    val canMakeDebugEdit: Boolean = false,
) {
    /** Play/Pause and a tap on the stage work; disabled when there's nothing to play (empty draft, preview error). */
    val canPlay: Boolean get() = stage != StageContent.EMPTY && stage != StageContent.ERROR
}

/** The project's frame: its width-to-height ratio, and the ratio as screen readers hear it ("9:16"). */
@Immutable
data class CanvasUi(val aspectRatio: Float, val ratioLabel: String)

/** What the canvas frame on the stage shows. */
enum class StageContent {
    /** The draft or the first frame isn't ready: an empty `surface-raised` frame, nothing moving. */
    OPENING,

    /** The preview's picture. */
    VIDEO,

    /** The preview couldn't be built or played: a message and Try again. Edits keep working. */
    ERROR,

    /** No clip on the main video track (defensive: the app never makes one, and leaving deletes it). */
    EMPTY,
}

/** Why the banner says the latest changes aren't saved. */
enum class SaveProblem { STORAGE_FULL, OTHER }

/**
 * Which tools the clip tool bar shows for the selected item, in the spec's order (P1-06 → Clip tool bar). P1-08 adds
 * Move here and sound volume, P1-09 Volume and Detach audio for video clips, P1-10 the text tools.
 */
enum class ClipTools {
    /** A video or photo clip on the main track: Split, Delete, Duplicate, Reorder. */
    MAIN_CLIP,

    /** A caption: Delete, Split, Duplicate (captions are deleted more often than cut). */
    TEXT,

    /** Sounds, detached audio, overlays, stickers and effects: Split, Delete, Duplicate. */
    OTHER,
}

/**
 * Reorder mode: the main clips' ids in the order the tiles show now, the clip being moved, and whether a long-press
 * drag started it (the tool bar's Reorder starts it for taps). Nothing is committed until Done or the drop; back
 * cancels and the clips stay in [originalOrder].
 */
@Immutable
data class ReorderUi(
    val order: ImmutableList<String>,
    val movingId: String,
    val byDrag: Boolean,
    val originalOrder: ImmutableList<String>,
) {
    val movingIndex: Int get() = order.indexOf(movingId)
}

/** An item that just landed on the timeline; a new [serial] bonks it again, even the same item. */
@Immutable
data class LandedItem(val itemId: String, val serial: Long)

/** Why a tool explains instead of acting (spec P1-06 → Clip tool bar: tools stay enabled and explain on tap). */
enum class Explanation { SPLIT_OUTSIDE, LAST_CLIP, REORDER_ONE_CLIP, ALREADY_FIRST, ALREADY_LAST }

/**
 * A toast on the stage; a new [id] restarts its timer, even for the same message. [isSpokenOnly] when it arrived
 * while the save banner was up: it isn't shown (it would be stale once the banner goes), only spoken to screen
 * readers (spec P1-04 → Saving).
 */
@Immutable
data class EditorToast(val id: Long, val message: ToastMessage, val isSpokenOnly: Boolean = false)

sealed interface ToastMessage {
    data class Undone(val edit: VideoEdit) : ToastMessage

    data class Redone(val edit: VideoEdit) : ToastMessage

    /** Once per open, when a clip's media file is missing. */
    data object MissingMedia : ToastMessage

    /** A tool that can't act right now says why. */
    data class Explain(val explanation: Explanation) : ToastMessage

    /** After each edit, for screen readers only: "Delete: clip 2" (spec P1-06 → Accessibility). */
    data class Edited(val edit: VideoEdit, val item: ItemName) : ToastMessage

    /** In reorder mode, after each move, for screen readers only: "Clip 2 of 3". */
    data class ReorderPosition(val position: Int, val count: Int) : ToastMessage
}

/**
 * The editor's live preview: the engine session behind the picture, and the time on screen. The time changes with
 * every frame while playing, so only the views that draw it read it; it never goes through [VideoEditorUiState].
 * Compared by identity: a new preview (Try again) is a new instance.
 */
@Stable
class EditorPreview internal constructor(internal val session: PreviewSession) {
    internal val positionUs: StateFlow<Long> get() = session.positionUs
}

sealed interface VideoEditorIntent {
    /** The play button or a tap on the stage. */
    data object PlayPause : VideoEditorIntent

    data object Undo : VideoEditorIntent

    data object Redo : VideoEditorIntent

    /** The Close button, system back or Escape. */
    data object Close : VideoEditorIntent

    /** "Leave anyway" on the unsaved-changes sheet. */
    data object LeaveAnyway : VideoEditorIntent

    /** The unsaved-changes sheet closed by its close button, the scrim or back: stay in the editor. */
    data object StayInEditor : VideoEditorIntent

    /** The × on the save banner. */
    data object DismissSaveBanner : VideoEditorIntent

    /** Free up space was tapped, but the phone has no storage screen that opens. */
    data object FreeUpSpaceUnavailable : VideoEditorIntent

    /** "Try again" after the preview stopped. */
    data object RetryPreview : VideoEditorIntent

    data class ToastTimedOut(val id: Long) : VideoEditorIntent

    /** The app is in the foreground again, maybe back from the storage manager. */
    data object AppStarted : VideoEditorIntent

    /** The app went to the background. */
    data object AppStopped : VideoEditorIntent

    /** Debug and benchmark builds only (see [VideoEditorUiState.canMakeDebugEdit]). */
    data object DebugEdit : VideoEditorIntent

    /** A finger landed on the timeline: playback pauses (spec P1-05 → Scroll, zoom and scrub). */
    data object TimelineTouched : VideoEditorIntent

    /** The timeline started moving time under the playhead (a drag, then maybe a fling). */
    data object ScrubStarted : VideoEditorIntent

    /** The time under the playhead while the timeline moves, µs; many arrive per second. */
    data class ScrubTo(val positionUs: Long) : VideoEditorIntent

    /** The timeline stopped at [positionUs]: the preview shows that exact frame. */
    data class ScrubEnded(val positionUs: Long) : VideoEditorIntent

    /** A tap on the ruler, or a screen reader's step or jump: pause and show [positionUs]. */
    data class SeekTo(val positionUs: Long) : VideoEditorIntent

    /** A tap on a timeline item. */
    data class SelectItem(val itemId: String) : VideoEditorIntent

    /** A tap on an empty part of the timeline, or Close clip tools. */
    data object ClearSelection : VideoEditorIntent

    /** System back or Escape inside the editor: undoes the innermost thing first, then closes (spec P1-04 back order). */
    data object Back : VideoEditorIntent

    /** The Edit tool: selects the main clip under the playhead, or the last one at the very end. */
    data object EditTool : VideoEditorIntent

    /** The selected item's tools (or its screen-reader actions). */
    data object SplitSelected : VideoEditorIntent

    data object DeleteSelected : VideoEditorIntent

    data object DuplicateSelected : VideoEditorIntent

    /** Moves the selected main clip [steps] places later (negative: earlier); the screen-reader route to reorder. */
    data class MoveSelectedClip(val steps: Int) : VideoEditorIntent

    /** A finger took [edge]'s handle of [itemId] (spec P1-06 → Trim): playback pauses, the preview follows the edge. */
    data class TrimStarted(val itemId: String, val edge: TrimEdge) : VideoEditorIntent

    /** The dragged edge is at [edgeUs] now; many arrive per second. */
    data class TrimMoved(val edgeUs: Long) : VideoEditorIntent

    /** The finger lifted with the edge at [edgeUs]: one Trim step. [playheadUs] is where the timeline's playhead is. */
    data class TrimEnded(val itemId: String, val edge: TrimEdge, val edgeUs: Long, val playheadUs: Long) : VideoEditorIntent

    /** The drag was cancelled (system back); nothing changes. */
    data class TrimCancelled(val playheadUs: Long) : VideoEditorIntent

    /** A handle's screen-reader step (one frame) or Trim to playhead: one Trim step without a drag. */
    data class TrimTo(val itemId: String, val edge: TrimEdge, val edgeUs: Long) : VideoEditorIntent

    /** The Reorder tool on the selected main clip. */
    data object StartReorderSelected : VideoEditorIntent

    /** A long-press on main clip [itemId] that goes on as a drag ([byDrag] true), or the Reorder tool (false). */
    data class StartReorder(val itemId: String, val byDrag: Boolean) : VideoEditorIntent

    /** In reorder mode: the moving clip goes to place [index] (a drag passed a neighbor, or a tap on another tile). */
    data class ReorderMoveTo(val index: Int) : VideoEditorIntent

    /** Move earlier (-1) or Move later (+1) in reorder mode. */
    data class ReorderStep(val steps: Int) : VideoEditorIntent

    /** Done, or the drop: the new order is one Reorder step (none if nothing moved). */
    data object ReorderDone : VideoEditorIntent
}
