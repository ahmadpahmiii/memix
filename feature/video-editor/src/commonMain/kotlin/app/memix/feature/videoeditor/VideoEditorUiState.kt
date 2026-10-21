package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import app.memix.core.domain.project.LaneTarget
import app.memix.core.domain.project.MediaPlace
import app.memix.core.domain.project.TrimEdge
import app.memix.core.domain.video.PreviewSession
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.Transform
import app.memix.feature.videoeditor.importmedia.ImportIntent
import app.memix.feature.videoeditor.importmedia.ImportUiState
import app.memix.feature.videoeditor.timeline.ItemName
import app.memix.feature.videoeditor.timeline.TimelineThumbnails
import app.memix.feature.videoeditor.timeline.TimelineUi
import app.memix.feature.videoeditor.timeline.TrimRanges
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
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
    /** What just landed (a duplicate, a meme sound, added media): the timeline bonks it once per new [LandedItems.serial]. */
    val landed: LandedItems? = null,
    /** The Meme sounds sheet and the starter pack it lists (spec P1-06 → Meme sounds sheet). */
    val sounds: SoundsSheetUi = SoundsSheetUi(),
    /** The Volume panel over the timeline and tool bar, or null when it's closed (spec P1-06 → Volume panel). */
    val volumePanel: VolumePanelUi? = null,
    /**
     * The original audio (the sound in the user's own videos, P1-09): whether every clip's is muted. Null when no main
     * clip plays its own sound, so there's nothing to mute and the timeline shows no toggle.
     */
    val originalAudio: OriginalAudioUi? = null,
    /**
     * Add media and Replace (P1-16): the import sheet's state, which only the sheet reads (its progress changes up to
     * ten times a second). Compared by identity.
     */
    val mediaImport: EditorMediaImportUi? = null,
    /** From a pick until its import sheet is gone; tools that start another pick wait (spec P1-06 → Entry and exit). */
    val isImporting: Boolean = false,
    /** The picker the app should open now; the app opens it and sends [VideoEditorIntent.PickerOpened]. */
    val mediaPicker: MediaPickRequest? = null,
    /**
     * Where focus goes after a surface closes: the control that opened a pick, the sheet or the panel, or the new
     * sound's Volume tool (spec P1-06 → Accessibility).
     */
    val returnFocus: ReturnFocus? = null,
    /** The captions on the preview (P1-10), laid out by the engine's renderer; empty until the draft has opened. */
    val captions: ImmutableList<CaptionUi> = persistentListOf(),
    /** Measures a caption as it's typed, with the renderer that draws it; null until the draft has opened. */
    val captionMeasure: EditorCaptionMeasure? = null,
    /** Typing a caption over the keyboard (spec P1-10 → Typing); null otherwise. */
    val textEntry: TextEntryUi? = null,
    /** The Style or Position panel of the selected caption, or null when neither is open. */
    val textPanel: TextPanel? = null,
    /**
     * A caption edit the preview hasn't shown yet: the editor's stand-in draws it from the commit until the preview's
     * new frame takes over, so nothing jumps or flashes (spec P1-10 → Live editing). Null otherwise.
     */
    val captionStandIn: CaptionUi? = null,
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

/**
 * The project's frame: its width-to-height ratio, the ratio as screen readers hear it ("9:16"), and its size in pixels,
 * which captions are laid out in.
 */
@Immutable
data class CanvasUi(val aspectRatio: Float, val ratioLabel: String, val widthPx: Int = 0, val heightPx: Int = 0)

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
 * Which tools the clip tool bar shows for the selected item, in the spec's order (P1-06 → Clip tool bar). P1-09 adds
 * Volume and Detach audio for video clips, P1-10 the text tools.
 */
enum class ClipTools {
    /** A video clip on the main track: Split, Volume, Delete, Duplicate, Detach audio, Reorder, Replace (Replace first when its file is missing). */
    VIDEO_CLIP,

    /** A photo clip on the main track: Split, Delete, Duplicate, Reorder, Replace (no sound, so no Volume or Detach audio). */
    PHOTO_CLIP,

    /** A caption: Edit text, Style, Position, Delete, Split, Move here, Duplicate (spec P1-10 → Text clip tools). */
    TEXT,

    /** A meme sound or detached audio: Volume, Split, Delete, Move here, Duplicate. */
    SOUND,

    /** Overlays, stickers and effects: Split, Delete, Move here, Duplicate. */
    OTHER,
}

/**
 * The Meme sounds sheet: whether it's open, the starter pack's sounds in the pack's order (empty until they've loaded,
 * or when the build bundles none), and the one playing in the sheet, if any.
 */
@Immutable
data class SoundsSheetUi(
    val isOpen: Boolean = false,
    val sounds: ImmutableList<SoundRowUi> = persistentListOf(),
    val playingId: String? = null,
)

/** One sound of the sheet: its catalog id, its title in every app language, its length (µs) and its credit text. */
@Immutable
data class SoundRowUi(val id: String, val title: LocalizedText, val durationUs: Long, val credit: String)

/**
 * The Volume panel of item [itemId]: [volume] follows the slider while it moves (1 = 100%). [isVideoClip]: a main video
 * clip, labelled "This clip" with the "Mute original audio of every clip" row under the slider; sounds read "This sound".
 */
@Immutable
data class VolumePanelUi(val itemId: String, val volume: Float, val isVideoClip: Boolean = false)

/** The original audio's one setting: [muted] when every main clip's own sound is off (`Track.muted`). */
@Immutable
data class OriginalAudioUi(val muted: Boolean)

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

/**
 * Items that just landed on the timeline, bonking together; a new [serial] bonks them again, even the same items. A
 * meme sound ([isSound]) also gets the Confirm haptic, the "tick when a sound lands" (spec P1-06 → Motion and haptics).
 */
@Immutable
data class LandedItems(val itemIds: ImmutableSet<String>, val serial: Long, val isSound: Boolean = false)

/**
 * The picker the editor wants opened (spec P1-06 → Add media, Replace), and what the pick is for. The app keeps it
 * while the picker is open and hands it back with the result ([VideoEditorIntent.MediaPicked]), so a pick still lands
 * where it should after Android restarted the app behind the picker.
 */
@Immutable
sealed interface MediaPickRequest {
    /** Up to 35 videos and photos, landing at [place] on the main track. */
    data class Add(val place: MediaPlace) : MediaPickRequest

    /** One video or photo for main clip [itemId]. */
    data class Replace(val itemId: String) : MediaPickRequest
}

/** Which control gets focus; a new [serial] moves it there again. */
@Immutable
data class ReturnFocus(val target: FocusTarget, val serial: Long)

/** The controls focus can be sent back to. */
enum class FocusTarget {
    /** The Add media tool on the main tool bar. */
    ADD_MEDIA_TOOL,

    /** The Add media tile at the end of the main track (P1-05 → Trailing area). */
    ADD_MEDIA_TILE,

    /** The Replace tool of the selected clip. */
    REPLACE_TOOL,

    /** The Meme sounds tool on the main tool bar. */
    MEME_SOUNDS_TOOL,

    /** The "Add a meme sound" pill on the timeline. */
    ADD_SOUND_PILL,

    /** The Volume tool of the selected sound. */
    VOLUME_TOOL,

    /** The Text tool on the main tool bar (P1-10): after typing a blank new caption. */
    TEXT_TOOL,

    /** The selected caption's Style and Position tools, after their panels close. */
    STYLE_TOOL,
    POSITION_TOOL,

    /** The selected caption's node on the preview, after typing commits (spec P1-10 → Accessibility). */
    SELECTED_CAPTION,
}

/** Why a tool explains instead of acting (spec P1-06 → Clip tool bar: tools stay enabled and explain on tap). */
enum class Explanation {
    SPLIT_OUTSIDE,
    LAST_CLIP,
    REORDER_ONE_CLIP,
    ALREADY_FIRST,
    ALREADY_LAST,
    SOUND_UNREADABLE,
    REPLACE_DETACHED,

    /** Volume or Detach audio on a video with no sound track (P1-09). */
    NO_SOUND,

    /** Volume or Detach audio on a clip whose sound is already on its own track. */
    DETACHED,

    /** After detaching while every clip was muted: the new clip starts at 0%. */
    DETACHED_MUTED,

    /** Position panel limits (P1-10): Smaller at 25%, Bigger at 500%, a Move arrow with the center at that edge. */
    TEXT_SMALLEST,
    TEXT_BIGGEST,
    TEXT_EDGE,
}

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

    /** After a meme sound lands, for screen readers only: "Boom added at 3.2 seconds" ([atUs] is where it starts). */
    data class SoundAdded(val title: LocalizedText, val atUs: Long) : ToastMessage

    /** After a new caption lands, for screen readers only: "Text added at 3.2 seconds" ([atUs] is where it starts). */
    data class TextAdded(val atUs: Long) : ToastMessage

    /** After a size or rotation step, for screen readers only: "120%, 15 degrees". */
    data class TextTransformed(val scalePercent: Int, val degrees: Int) : ToastMessage

    /** After Add media lands, for screen readers only: "3 clips added". */
    data class MediaAdded(val count: Int) : ToastMessage

    /** Replace refused a video shorter than the clip, whose length ([clipLengthUs]) the toast names. */
    data class ReplaceTooShort(val clipLengthUs: Long) : ToastMessage
}

/**
 * The editor's import (Add media and Replace): its sheet's state, read only by the sheet. Compared by identity: one
 * per editor.
 */
@Stable
class EditorMediaImportUi internal constructor(internal val state: StateFlow<ImportUiState>)

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

    /** The Meme sounds tool: opens the sounds sheet. */
    data object MemeSoundsTool : VideoEditorIntent

    /** The "Add a meme sound" pill: clears the selection and opens the sounds sheet (spec P1-05 → Add a meme sound). */
    data object AddMemeSoundRow : VideoEditorIntent

    /** The sheet's close button, scrim, drag, back or Escape: nothing is added. */
    data object CloseSoundsSheet : VideoEditorIntent

    /** A row's play disc: plays sound [soundId] alone, or stops it when it's the one playing. */
    data class ToggleSoundPreview(val soundId: String) : VideoEditorIntent

    /** A row's add button: sound [soundId] lands at the playhead and the sheet closes. */
    data class AddSound(val soundId: String) : VideoEditorIntent

    /** The Volume tool of the selected sound: opens the Volume panel. */
    data object VolumeTool : VideoEditorIntent

    /** The Volume slider moved to [volume] (1 = 100%); heard at once, nothing committed yet. */
    data class VolumeChanged(val volume: Float) : VideoEditorIntent

    /** The slider was let go (or stepped): the panel's volume is one Volume step (none if it's where it began). */
    data object VolumeReleased : VideoEditorIntent

    /** Done, system back or Escape on the Volume panel. */
    data object CloseVolumePanel : VideoEditorIntent

    /** Move here on the selected item: it starts at the playhead (spec P1-06 → Move). */
    data object MoveHereSelected : VideoEditorIntent

    /** A long-press lifted item [itemId] to move it (spec P1-06 → Move → By drag): playback pauses, it's selected. */
    data class MoveStarted(val itemId: String) : VideoEditorIntent

    /** The moved item was dropped to start at [startUs] on [lane]: one Move step (none if it's where it was). */
    data class MoveEnded(val itemId: String, val startUs: Long, val lane: LaneTarget) : VideoEditorIntent

    /** The move was cancelled (system back, a lost gesture); nothing changes. */
    data object MoveCancelled : VideoEditorIntent

    /** The Add media tool: the pick lands after the main clip under the playhead (spec P1-06 → Add media). */
    data object AddMediaTool : VideoEditorIntent

    /** The Add media tile at the end of the main track: the pick lands at the end. */
    data object AddMediaTile : VideoEditorIntent

    /** The Replace tool of the selected main clip: one video or photo takes its place (spec P1-06 → Replace). */
    data object ReplaceSelected : VideoEditorIntent

    /** The app opened the picker for [VideoEditorUiState.mediaPicker]. */
    data object PickerOpened : VideoEditorIntent

    /** The phone has no app that picks media: the import sheet says so. */
    data object PickerUnavailable : VideoEditorIntent

    /** The picker returned [items] for [request] (empty when nothing was picked: nothing changes). */
    data class MediaPicked(val request: MediaPickRequest, val items: List<MediaOrigin>) : VideoEditorIntent

    /** Something on the editor's import sheet (Cancel, Continue, Close, back from the storage screen, ...). */
    data class Import(val intent: ImportIntent) : VideoEditorIntent

    /** "Pick again" on the import sheet: it closes, then the same picker opens again. */
    data object PickAgain : VideoEditorIntent

    /** The timeline's original-audio toggle or the Volume panel's row: mutes or unmutes every clip's own sound. */
    data object ToggleOriginalAudio : VideoEditorIntent

    /**
     * Detach audio on the selected video clip: its sound moves to its own audio clip (spec P1-06 → Detach audio).
     * [fileMissing] when the screen knows the clip's file is gone (the thumbnails' missing sources): it explains instead.
     */
    data class DetachAudioSelected(val fileMissing: Boolean) : VideoEditorIntent

    /** The Text tool: typing a new caption starts (spec P1-10 → Main tool bar). */
    data object TextTool : VideoEditorIntent

    /** Edit text on the selected caption, or a tap on it on the preview: typing its words starts. */
    data object EditTextSelected : VideoEditorIntent

    /** The words in the field changed to [text] (already within the 250-character limit); nothing is committed yet. */
    data class TextEntryChanged(val text: String) : VideoEditorIntent

    /** Done, back, Escape or the keyboard going away: the words are committed (spec P1-10 → Typing → commit). */
    data object TextEntryDone : VideoEditorIntent

    /** The Style or Position tool of the selected caption. */
    data class OpenTextPanel(val panel: TextPanel) : VideoEditorIntent

    /** Done, back or Escape on the Style or Position panel. */
    data object CloseTextPanel : VideoEditorIntent

    /** A font tile: caption font [fontId] for the selected caption. */
    data class SetCaptionFont(val fontId: String) : VideoEditorIntent

    /** A color swatch: the selected caption's letters in [argb]. */
    data class SetCaptionColor(val argb: Long) : VideoEditorIntent

    /** An outline swatch: the selected caption's outline in [argb], or none when it's null. */
    data class SetCaptionOutline(val argb: Long?) : VideoEditorIntent

    /** A Position panel tap, or the end of a held Move arrow ([repeats] steps in all). */
    data class PositionStepped(val step: PositionStep, val repeats: Int = 1) : VideoEditorIntent

    /** A held Move arrow repeated: the caption shows [repeats] steps along, nothing committed yet. */
    data class PositionStepHeld(val step: PositionStep, val repeats: Int) : VideoEditorIntent

    /** A tap on caption [itemId] on the preview: selects it, or opens typing when it's already selected. */
    data class CaptionTapped(val itemId: String) : VideoEditorIntent

    /** A drag, pinch or handle drag on caption [itemId] began: playback pauses, it's selected, the stand-in draws it. */
    data class CaptionGestureStarted(val itemId: String) : VideoEditorIntent

    /** The gesture on [itemId] ended at [transform]: one Position or Size and rotation step (none if unchanged). */
    data class CaptionGestureEnded(val itemId: String, val transform: Transform) : VideoEditorIntent

    /** The gesture was cancelled (system back, a lost gesture); nothing changes. */
    data object CaptionGestureCancelled : VideoEditorIntent
}
