package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import app.memix.core.domain.video.PreviewSession
import app.memix.feature.videoeditor.timeline.TimelineThumbnails
import app.memix.feature.videoeditor.timeline.TimelineUi
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

    /** A tap on an empty part of the timeline. */
    data object ClearSelection : VideoEditorIntent
}
