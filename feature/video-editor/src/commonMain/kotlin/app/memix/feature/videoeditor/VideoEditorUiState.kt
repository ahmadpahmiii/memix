package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import app.memix.core.domain.project.LaneTarget
import app.memix.core.domain.project.MediaPlace
import app.memix.core.domain.project.TrimEdge
import app.memix.core.domain.video.ExportChoice
import app.memix.core.domain.video.ExportEstimate
import app.memix.core.domain.video.PreviewSession
import app.memix.core.domain.video.VideoCodec
import app.memix.core.domain.voiceover.VoiceRoute
import app.memix.core.domain.sound.PickedSound
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.domain.project.AdjustPart
import app.memix.core.domain.project.EditPhotoTab
import app.memix.core.domain.project.MaskStep
import app.memix.core.model.project.ChromaKey
import app.memix.core.model.project.ClipMask
import app.memix.core.model.project.MaskShape
import app.memix.core.model.project.AudioEffects
import app.memix.core.model.project.AudioEffectsCatalog
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.CaptionBox
import app.memix.core.model.project.CaptionShadow
import app.memix.core.model.project.EchoLength
import app.memix.core.model.project.FrameFit
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.RatioTerms
import app.memix.core.model.project.Transform
import app.memix.core.model.project.VoicePreset
import app.memix.core.ui.text.TextEntryUi
import app.memix.core.ui.editor.SaveProblem
import app.memix.core.ui.panels.PositionStep
import app.memix.core.ui.panels.RatioStep
import app.memix.core.ui.panels.BackgroundUi
import app.memix.feature.mediaimport.ImportIntent
import app.memix.feature.mediaimport.ImportUiState
import app.memix.feature.mediaimport.ImportWording
import app.memix.feature.videoeditor.timeline.ItemName
import app.memix.feature.videoeditor.timeline.TimelineThumbnails
import app.memix.feature.videoeditor.timeline.TimelineUi
import app.memix.feature.videoeditor.timeline.TimelineWaveforms
import app.memix.feature.videoeditor.timeline.TrimRanges
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.StateFlow

/** What the video editor shows (spec docs/ux/specs/P1-04-editor-and-preview.md → States). */
@Immutable
data class VideoEditorUiState(
    /** Null until the draft has opened. */
    val canvas: CanvasUi? = null,
    /** How long the project plays, in µs: where its last main video clip ends. */
    val lengthUs: Long = 0,
    val stage: StageContent = StageContent.OPENING,
    /**
     * The picture on screen has another canvas's shape: a ratio change whose first frame hasn't shown yet. The frame
     * shows the background color instead, never a stretched picture (spec P1-11 → States, "Preview catching up").
     */
    val pictureOutOfShape: Boolean = false,
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
    /** The Audio effects panel over the timeline and tool bar, or null when it's closed (spec P3-09). */
    val audioEffects: AudioEffectsPanelUi? = null,
    /** The Voiceover panel over the timeline and tool bar, or null when it's closed (spec P3-08). */
    val voiceover: VoiceoverPanelUi? = null,
    /** The Canvas panel over the timeline and tool bar, or null when it's closed (spec P1-11). */
    val canvasPanel: CanvasPanelUi? = null,
    /** The Export sheet (spec P1-12), or null when it's closed. */
    val exportSheet: ExportSheetUi? = null,
    /** An export just started: the app opens the Exporting screen, then sends [VideoEditorIntent.ExportScreenOpened]. */
    val openExportScreen: Boolean = false,
    /** At least one clip's media file is missing (the preview plays it black); the Export sheet says so. */
    val missingMedia: Boolean = false,
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
    /** The words of the import sheet: a background picture is "your photo", Add media and Replace are "your media" (spec P2-12 → Entry and exit). */
    val importWording: ImportWording = ImportWording.MEDIA,
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
    /** The overlays on the stage (P4-01b), back to front; empty until the draft has opened. */
    val overlays: ImmutableList<OverlayUi> = persistentListOf(),
    /** The Position, Opacity or Layer order panel of the selected overlay, or null when none is open. */
    val overlayPanel: OverlayPanelUi? = null,
    /** The Green screen panel of the selected main clip or overlay (P4-04), or null when it is closed. */
    val greenScreen: GreenScreenUi? = null,
    /** The Mask panel of the selected main clip or overlay (P4-03b), or null when it is closed. */
    val maskPanel: MaskUi? = null,
    /** The Filters panel (P4-07), from the main bar (every main clip) or a clip's bar (that clip), or null when it is closed. */
    val lookPanel: LookUi? = null,
    /** The Effects sheet (P4-06), from the main bar (add) or an effect item's bar (replace), or null when it is closed. */
    val effectsSheet: EffectsSheetUi? = null,
    /** The Intensity panel of the selected effect item (P4-06), or null when it is closed. */
    val effectPanel: EffectPanelUi? = null,
    /** The Transition panel of the selected main clip's cut (P4-08), or null when it is closed. */
    val transitionPanel: TransitionPanelUi? = null,
    /** The Speed panel of the selected video clip (P4-09a), or null when it is closed. */
    val speedPanel: SpeedPanelUi? = null,
    /**
     * An overlay a finger or a held arrow moved, or a commit the preview hasn't shown yet: the editor's stand-in draws it from
     * there until the preview's new frame takes over, so it never shows twice or jumps (spec P4-01b → Live feedback). Null otherwise.
     */
    val overlayStandIn: OverlayStandInUi? = null,
    /** Overlay videos the phone can't decode together with the rest: left out of the preview and the export, so they are on their lane only, never on the stage. */
    val skippedOverlayIds: ImmutableSet<String> = persistentSetOf(),
    /** A clip the timeline's lanes scroll to show (after Layer order moved it); a new serial scrolls again. */
    val revealed: LandedItems? = null,
    /** The overlay the preview leaves out because the stand-in draws it now (a finger, a held arrow or a commit not shown yet). */
    val hiddenOverlayId: String? = null,
    /** The pictures the stand-in draws overlays with, once the draft has opened. Compared by identity. */
    val overlayFrames: OverlayFrames? = null,
    /** The keyframe bar of the selected item (P4-02b), or null when it isn't open; it replaces the tool bar. */
    val keyframeBar: KeyframeBarUi? = null,
    /** The thumbnail tiles of the timeline's strips, once the draft has opened. Compared by identity. */
    val thumbnails: TimelineThumbnails? = null,
    /** The waveforms of the timeline's sounds, once the draft has opened. Compared by identity. */
    val waveforms: TimelineWaveforms? = null,
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
    /** The one-time note after Make it a video meme is up, with its Got it button (spec P2-10 → Arriving); dismissed by the button or its time. */
    val videoMemeNote: Boolean = false,
    /**
     * "Updating the preview" in the stage's message slot: an edit hasn't reached the preview after a second (spec
     * P1-06 → Preview catching up). Shown only when the save banner and toasts leave the slot free.
     */
    val previewUpdating: Boolean = false,
    /** How far the reversed copy of a clip is, 0 to 100, once it has taken a second (P4-10: "Reversing 40%"); null when nothing is being prepared or it is still quick. */
    val reversePercent: Int? = null,
    /** "Your latest changes aren't saved": leaving while saving fails. */
    val unsavedSheetOpen: Boolean = false,
    /** The editor is done; the app closes the screen. */
    val isClosing: Boolean = false,
    /** Closing because the draft couldn't be opened (damaged, from a newer app, or deleted meanwhile): the app says so. */
    val couldntOpen: Boolean = false,
    /** Debug and benchmark builds: a long-press on the timecode makes one test edit, for the P1-07 hand checks. */
    val canMakeDebugEdit: Boolean = false,
) {
    /** Play/Pause and a tap on the stage work; disabled when there's nothing to play (empty draft, preview error). */
    val canPlay: Boolean get() = stage != StageContent.EMPTY && stage != StageContent.ERROR
}

/**
 * The project's frame: its width-to-height ratio, the ratio as shown and spoken ("9:16", a custom one in lowest terms),
 * its size in pixels (captions are laid out in it), and what the Canvas panel shows of it (spec P1-11): the [ratio]
 * choice, the background (a color, blur or a picture, [background]), the fit every main clip shares ([clipsFit],
 * null when they differ) and whether the background can show around any clip ([backgroundShows]).
 */
@Immutable
data class CanvasUi(
    val aspectRatio: Float,
    val ratioLabel: String,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val ratio: CanvasRatio = CanvasRatio.RATIO_9_16,
    val background: BackgroundUi = BackgroundUi.Solid(BLACK_ARGB),
    val clipsFit: FrameFit? = FrameFit.FIT,
    val backgroundShows: Boolean = true,
)

private const val BLACK_ARGB = 0xFF000000

/**
 * The Export sheet (spec P1-12 → Export sheet): the [choice] shown (the remembered one, stepped down to what the phone can
 * do for this canvas), the codec the facts line names, the estimate (null while unknown), and the notices: the
 * setting [loweredTo] when the remembered one isn't possible here, the bytes to free ([spaceShortBytes], rounded up to
 * 10 MB), and nothing possible for this canvas at all. A missing clip's notice reads [VideoEditorUiState.missingMedia].
 */
@Immutable
data class ExportSheetUi(
    val choice: ExportChoice,
    val codec: VideoCodec = VideoCodec.H264,
    val estimate: ExportEstimate? = null,
    val loweredTo: ExportChoice? = null,
    val spaceShortBytes: Long? = null,
    /** The total the export needs free: what Free up space asks the storage screen for (never [spaceShortBytes]). */
    val spaceNeededBytes: Long = 0L,
    val nothingPossible: Boolean = false,
)

/**
 * The Canvas panel's own state for this visit (spec P1-11 → Interactions). [customTerms]: the Custom tile is selected
 * and its steppers show these numbers as stepped (10 and 16, not 5:8), null when a preset is. [heldTerms]: a stepper is
 * held, and the readout and the pill show these until the release commits them.
 */
@Immutable
data class CanvasPanelUi(val customTerms: RatioTerms? = null, val heldTerms: RatioTerms? = null) {
    /** What the steppers and the pill show now. */
    val shownTerms: RatioTerms? get() = heldTerms ?: customTerms
}

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

/**
 * Which tools the clip tool bar shows for the selected item, in the spec's order (P1-06 → Clip tool bar). P1-09 adds
 * Volume and Detach audio for video clips, P1-10 the text tools.
 */
enum class ClipTools {
    /** A video clip on the main track: Split, Volume, Delete, Duplicate, Detach audio, Reorder, Replace (Replace first when its file is missing). */
    VIDEO_CLIP,

    /** A photo clip on the main track: Split, Delete, Duplicate, Reorder, Replace (no sound, so no Volume or Detach audio). */
    PHOTO_CLIP,

    /** A frozen frame on the main track (P4-10): a photo clip without Replace, and without Keyframes. */
    FREEZE_CLIP,

    /** A caption: Edit text, Style, Position, Delete, Split, Move here, Duplicate (spec P1-10 → Text clip tools). */
    TEXT,

    /** A meme sound or detached audio: Volume, Split, Delete, Audio effects, Move here, Duplicate. */
    SOUND,

    /** A video overlay: Position, Opacity, Delete, Split, Volume, Layer order, Move here, Duplicate, Detach audio (spec P4-01b → Overlay tool bar). */
    VIDEO_OVERLAY,

    /** A photo overlay: Position, Opacity, Delete, Split, Layer order, Move here, Duplicate. */
    PHOTO_OVERLAY,

    /** An effect item (P4-06): Intensity, Delete, Change effect, Split, Move here, Duplicate. */
    EFFECT,

    /** Stickers: Split, Delete, Move here, Duplicate. */
    OTHER,
}

/**
 * Whether the Meme sounds sheet is open (spec P3-05). What it shows, plays and downloads is the picker's own state
 * in `:feature:sounds`; the editor only opens it and lands the sound it hands over.
 */
@Immutable
data class SoundsSheetUi(val isOpen: Boolean = false)

/**
 * The Volume panel of item [itemId]: [volume] follows the slider while it moves (1 = 100%). [isVideoClip]: a main video
 * clip, labelled "This clip" with the "Mute original audio of every clip" row under the slider, unless it is an overlay's ([isOverlay]:
 * that row belongs to the main track); sounds read "This sound".
 */
@Immutable
data class VolumePanelUi(
    val itemId: String,
    val volume: Float,
    val isVideoClip: Boolean = false,
    val isOverlay: Boolean = false,
    val keyed: Boolean = false,
    /** The clip's Sound is Mute in Speed (P4-09): the panel says so at the top and keeps working. */
    val soundOffBySpeed: Boolean = false,
)

/**
 * The Green screen panel of clip [itemId] (P4-04): [key] follows the sliders while they move; [picking] is the eyedropper mode
 * (stage taps pick a color); [colorAt] reads the color under a finger in that mode; [hint] the one stage banner this panel owns.
 */
@Immutable
data class GreenScreenUi(
    val itemId: String,
    val key: ChromaKey,
    val picking: Boolean = false,
    /** The color under canvas fractions (fx, fy) in pick mode, or null outside the clip or before the frame is read; read by the pick layer's own state. */
    val colorAt: ((Float, Float) -> Int?)? = null,
    val hint: GreenScreenHint? = null,
)

/** What the Green screen banner says: how to pick, that a release missed the clip, or that nothing in the picture is removed. */
enum class GreenScreenHint { PICK, PICK_MISS, NOTHING }

/** The Position, Opacity or Layer order [panel] of overlay [itemId]; [opacity] follows the slider while it moves (1 = fully drawn); [keyed]: the overlay has keyframes, so the panel says so and its ranges widen (P4-02b). */
@Immutable
data class OverlayPanelUi(val panel: OverlayPanel, val itemId: String, val opacity: Float, val keyed: Boolean = false)

/**
 * The Audio effects panel of sound [itemId] (spec P3-09): the open [tab] (kept for the visit), and the [effects] as the sliders
 * show them, which follow a drag before it's committed. [clipLengthUs] is the clip's length on the timeline: what its two fades share.
 */
@Immutable
data class AudioEffectsPanelUi(val itemId: String, val tab: AudioEffectsTab, val effects: AudioEffects, val clipLengthUs: Long) {
    /** Under [AudioEffectsCatalog.MIN_FADEABLE_US] the clip is too short to fade. */
    val canFade: Boolean get() = clipLengthUs >= AudioEffectsCatalog.MIN_FADEABLE_US
}

/** The Audio effects panel's tabs, in the order they show (spec P3-09 → Layout). */
enum class AudioEffectsTab { VOICE, TONE, BASS, ECHO, FADES }

/** The sliders of the Audio effects panel: each release commits one step named for it. */
enum class AudioEffectPart { PITCH, SPEED, FADE_IN, FADE_OUT, BASS, DISTORTION, ECHO }

/**
 * The Voiceover panel (spec P3-08). [countdown] is the 3, 2, 1 while [phase] counts down; [savedAtUs] is where the last take of this
 * visit landed ("Voiceover added at 0:03"); [microphoneDenied] swaps Record for the explanation and Open settings;
 * [askMicrophone] asks the screen to show Android's own dialog, once; [quiet] shows the covered-microphone hint. The level
 * and the take's length change many times a second, so they are in [live], which only the views that draw them read.
 */
@Immutable
data class VoiceoverPanelUi(
    val phase: VoicePhase,
    val route: VoiceRoute,
    val live: VoiceoverLive,
    val countdown: Int = 0,
    val savedAtUs: Long? = null,
    val microphoneDenied: Boolean = false,
    val askMicrophone: Boolean = false,
    val quiet: Boolean = false,
) {
    /** A countdown or a take runs without touches, so the screen must not go dark. */
    val keepsScreenOn: Boolean get() = phase != VoicePhase.READY
}

enum class VoicePhase { READY, COUNTING_DOWN, RECORDING }

/** The take's microphone level (0 to 1) and length so far; read only in the meter's and the timecode's own draw or layout. Compared by identity. */
@Stable
class VoiceoverLive {
    var level: Float by mutableFloatStateOf(0f)
    var elapsedUs: Long by mutableLongStateOf(0L)
}

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

    /** One photo for the canvas background (P2-12: the Canvas panel's Image swatch and Replace). */
    data object Background : MediaPickRequest

    /** One video or photo for a new overlay (P4-01b), landing at [atUs]: where the playhead was when the Overlay tool was tapped. */
    data class Overlay(val atUs: Long) : MediaPickRequest
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

    /** The Audio effects tool of the selected sound, after its panel closes (P3-09). */
    AUDIO_EFFECTS_TOOL,

    /** The Voiceover tool on the main tool bar (P3-08), after its panel closes. */
    VOICEOVER_TOOL,

    /** The Text tool on the main tool bar (P1-10): after typing a blank new caption. */
    TEXT_TOOL,

    /** The selected caption's Style and Position tools, after their panels close. */
    STYLE_TOOL,
    POSITION_TOOL,

    /** The selected caption's node on the preview, after typing commits (spec P1-10 → Accessibility). */
    SELECTED_CAPTION,

    /** The ratio pill in the top bar, after the Canvas panel closes (P1-11). */
    RATIO_PILL,

    /** The Overlay tool on the main tool bar (P4-01b): after a pick that added nothing. */
    OVERLAY_TOOL,

    /** The selected overlay's Opacity and Layer order tools, after their panels close. */
    OPACITY_TOOL,
    LAYER_ORDER_TOOL,

    /** The selected item's Keyframes tool (after the keyframe bar closes) and the bar's Add keyframe tool and Easing tool (P4-02b). */
    KEYFRAMES_TOOL,
    ADD_KEYFRAME_TOOL,
    EASING_TOOL,

    /** The selected item's Green screen tool, after its panel closes (P4-04). */
    GREEN_SCREEN_TOOL,

    /** The selected clip's Mask tool, after its panel closes (P4-03b). */
    MASK_TOOL,

    /** The Filters tool, on the main bar or the selected clip's bar, after its panel closes (P4-07). */
    FILTERS_TOOL,

    /** The Effects tool on the main bar, after the sheet closes with nothing changed (P4-06); an effect item's Change effect and Intensity tools likewise. */
    EFFECTS_TOOL,
    CHANGE_EFFECT_TOOL,
    INTENSITY_TOOL,

    /** The selected main clip's Transition tool, after its panel closes (P4-08). */
    TRANSITION_TOOL,

    /** The selected video clip's Speed tool, after its panel closes (P4-09a). */
    SPEED_TOOL,
}

/** What a spoken mask step reads out: the width or height in percent, or the turn in degrees (a move is read as a place). */
enum class MaskReading { WIDTH, HEIGHT, ROTATION }

/** Why a tool explains instead of acting (spec P1-06 → Clip tool bar: tools stay enabled and explain on tap). */
enum class Explanation {
    SPLIT_OUTSIDE,
    LAST_CLIP,
    REORDER_ONE_CLIP,
    ALREADY_FIRST,
    ALREADY_LAST,
    SOUND_UNREADABLE,

    /** The phone has no room to copy a library sound into the project (P3-05; the Sounds screen's full-phone line). */
    SOUND_NO_SPACE,

    /** The editor opened from a Use flow without the sound that was meant to come along (P3-15). */
    SOUND_MISSING,
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

    /** Overlays (P4-01b): a Move arrow with the center at that edge; Bring forward or Send back with no overlay in that direction sharing a moment. */
    OVERLAY_EDGE,

    /** Mask Position tab (P4-03b): no shape picked yet, the smallest and biggest size, a Move arrow with the center at the box's edge. */
    MASK_NONE,
    MASK_MIN,
    MASK_MAX,
    MASK_EDGE,
    LAYER_ORDER_FRONT,
    LAYER_ORDER_BACK,

    /** Keyframes (P4-02b): the limit, Add outside the item, Previous or Next with none that way, Easing with nothing to ease, Move here with nothing selected or blocked. */
    KEYFRAME_MAX,
    KEYFRAME_OUTSIDE,
    KEYFRAME_NONE_PREVIOUS,
    KEYFRAME_NONE_NEXT,
    EASING_NONE,
    EASING_LAST,
    KEYFRAME_MOVE_NONE,
    KEYFRAME_MOVE_BLOCKED,

    /** Custom ratio limits (P1-11): past 2.39:1 either way, below 1, above 99. */
    CANVAS_WIDEST,
    CANVAS_TALLEST,
    CANVAS_MIN,
    CANVAS_MAX,

    /** The ratio pill or Export in reorder mode: "Tap Done to finish reordering first." */
    CANVAS_REORDER,

    /**
     * Voiceover (P3-08): Record at the end of the video; a take that reached the end, was interrupted, was too short or
     * silent; the microphone busy or the phone full; a control that waits for Stop.
     */
    VOICEOVER_AT_END,
    VOICEOVER_END,
    VOICEOVER_INTERRUPTED,
    VOICEOVER_TOO_SHORT,
    VOICEOVER_SILENT,
    VOICEOVER_BUSY,
    VOICEOVER_NO_SPACE,
    VOICEOVER_STOP_FIRST,

    /** Audio effects (P3-09): the sound's file is missing, or too short to fade. */
    AUDIO_EFFECTS_MISSING,
    FADE_TOO_SHORT,

    /** Detach audio on a clip whose speed the new sound clip can't follow: a curve, outside 0.5x to 2x, or Mute (P4-09). */
    DETACH_SPEED,

    /** Reset in the Speed panel with nothing set (P4-09). */
    SPEED_NOTHING_TO_RESET,

    /** Detach audio on a reversed clip: a sound clip has no direction (P4-10). */
    DETACH_REVERSED,

    /** Reverse on a clip whose trimmed source is over 60 s; the phone is full; the copy couldn't be made (the flag is switched off again) (P4-10). */
    REVERSE_LONG,
    REVERSE_NO_SPACE,
    REVERSE_FAILED,

    /** Freeze frame with the playhead off the selected clip; the frame couldn't be read or saved (P4-10). */
    FREEZE_OUTSIDE,
    FREEZE_FAILED,

    /** Transition tool (P4-08): on the last clip (nothing follows it), or on a cut whose two clips are too short to give 0.1 s. */
    TRANSITION_LAST,
    TRANSITION_SHORT,

    /** A fourth effect would overlap three others: an add, move, trim or duplicate that would make one is refused (P4-06). */
    EFFECT_MAX,
    EFFECT_FLASH,

    /** Export (P1-12): an empty draft, no resolution for this shape, not enough space. */
    EXPORT_EMPTY,
    EXPORT_NO_SHAPE,
    EXPORT_NO_SPACE,
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

    /** Once per open, when only overlay files are missing (P4-01b). */
    data object MissingOverlay : ToastMessage

    /** When overlay videos are left out because the phone can't play that many at once (P4-01b); once for each new set. */
    data object OverlaysSkipped : ToastMessage

    /** A keyframe was made by an edit, at [atUs] (2 s); or the first one of this item this visit (4 s, [KeyframesOn]). */
    data class KeyframeAdded(val atUs: Long) : ToastMessage

    data object KeyframesOn : ToastMessage

    /** Spoken only, after Previous, Next or a tap picked keyframe number [position] of [count]: "Keyframe 2 of 3, at 0:02.0, Smooth". */
    data class KeyframePicked(val position: Int, val count: Int, val atUs: Long, val easing: app.memix.core.model.project.Easing) : ToastMessage

    /** Spoken only, after a Position tab step on the mask (P4-03b): [value] is the new width or height in percent, or the turn in degrees. */
    data class MaskStepped(val reading: MaskReading, val value: Int) : ToastMessage

    /** Spoken only, after the eyedropper picked a color (P4-04). */
    data object GreenScreenPicked : ToastMessage

    /** Spoken only, after Remove keyframe. */
    data object KeyframeRemoved : ToastMessage

    /** Spoken only, after a keyframe's easing changed: its name. */
    data class EasingChanged(val easing: app.memix.core.model.project.Easing) : ToastMessage

    /** Spoken only, after an overlay landed. */
    data object OverlayAdded : ToastMessage

    /** Spoken only, after Freeze frame landed a still (P4-10). */
    data object FreezeAdded : ToastMessage

    /** Spoken only, when a reversed copy is ready (P4-10). */
    data object ReverseReady : ToastMessage

    /** Spoken only, as the reversed copy passes each quarter (P4-10): "Reversing 50%". */
    data class ReverseProgress(val percent: Int) : ToastMessage

    /** A speed change made an overlay overlap its neighbor, so it moved to a free layer (P4-09; 4 s). */
    data object SpeedMovedLane : ToastMessage

    /** An edit shortened a transition to fit, or removed it ([removed]) because its clips got too short (P4-08); 3 s. */
    data class TransitionSettled(val removed: Boolean) : ToastMessage

    /** Spoken only, after an effect landed on the timeline (P4-06). */
    data object EffectAdded : ToastMessage

    /** Spoken only, after the Opacity slider was released at [percent]. */
    data class OverlayOpacity(val percent: Int) : ToastMessage

    /** Spoken only, after Bring forward or Send back: "Layer 2 of 3", 1 being the front-most. */
    data class OverlayLayer(val layer: Int, val count: Int) : ToastMessage

    /** Once per open, when the background picture's file is missing; [ratioLabel] is the pill's text ("9:16"). */
    data class BackgroundMissing(val ratioLabel: String) : ToastMessage

    /** Spoken only, after a background picture landed. */
    data object BackgroundImageSet : ToastMessage

    /** Spoken only, after Remove. */
    data object BackgroundImageRemoved : ToastMessage

    /** A tool that can't act right now says why. */
    data class Explain(val explanation: Explanation) : ToastMessage

    /** After each edit, for screen readers only: "Delete: clip 2" (spec P1-06 → Accessibility). */
    data class Edited(val edit: VideoEdit, val item: ItemName) : ToastMessage

    /** In reorder mode, after each move, for screen readers only: "Clip 2 of 3". */
    data class ReorderPosition(val position: Int, val count: Int) : ToastMessage

    /** After a meme sound lands, for screen readers only: "Boom added at 3.2 seconds" ([atUs] is where it starts). A sound with no known [title] is "Sound added at 3.2 seconds". */
    data class SoundAdded(val title: LocalizedText?, val atUs: Long) : ToastMessage

    /** After a library sound landed with more than half a second past the end of the video: shown as the trim-or-move toast, spoken with the name and time (P3-05). */
    data class SoundPastEnd(val title: LocalizedText?, val atUs: Long) : ToastMessage

    /** After a Speed change made a sound longer than the room it had: it moved to another track (P3-09 rule 4). */
    data object SoundMovedLane : ToastMessage

    /** Reset with no effect set (P3-09). */
    data object NothingToReset : ToastMessage

    /** After a slider or Reset of the Audio effects panel, for screen readers only: "Audio effects reset" (P3-09). */
    data object AudioEffectsReset : ToastMessage

    /** After a voiceover take lands, for screen readers only: "Voiceover added at 3.2 seconds, 4 seconds long" ([atUs] is where it starts). */
    data class VoiceoverAdded(val atUs: Long, val lengthUs: Long) : ToastMessage

    /** After a new caption lands, for screen readers only: "Text added at 3.2 seconds" ([atUs] is where it starts). */
    data class TextAdded(val atUs: Long) : ToastMessage

    /** After a size or rotation step, for screen readers only: "120%, 15 degrees". */
    data class TextTransformed(val scalePercent: Int, val degrees: Int) : ToastMessage

    /** After a Move step or Center, for screen readers only: "50% across, 30% down" (the caption's center). */
    data class TextPositioned(val acrossPercent: Int, val downPercent: Int) : ToastMessage

    /** After Add media lands, for screen readers only: "3 clips added". */
    data class MediaAdded(val count: Int) : ToastMessage

    /** Replace refused a video shorter than the clip, whose length ([clipLengthUs]) the toast names. */
    data class ReplaceTooShort(val clipLengthUs: Long) : ToastMessage

    /** A ratio change moved or shrank a caption to keep it inside the frame (P1-11). */
    data object LayersMoved : ToastMessage

    /** After a custom ratio step, for screen readers only: the new ratio in lowest terms ("5:8"). */
    data class RatioStepped(val label: String) : ToastMessage

    /** A resolution was tapped and the frame rate stepped down to [frameRate] to make it work (P1-12). */
    data class ExportFrameRateLowered(val resolutionLabel: String, val frameRate: Int) : ToastMessage

    /** A frame rate was tapped and the resolution stepped down to [resolutionLabel]. */
    data class ExportResolutionLowered(val frameRate: Int, val resolutionLabel: String) : ToastMessage

    /** The phone can't export this resolution, or this frame rate, at all. One of the two is set. */
    data class ExportCantDo(val resolutionLabel: String?, val frameRate: Int?) : ToastMessage

    /** The phone exports [resolutionLabel] at 16:9 but not in this shape; [highestLabel] is the best this shape gets. */
    data class ExportShapeCantDo(val resolutionLabel: String, val highestLabel: String) : ToastMessage

    /** Back from a cancelled export: "Export stopped." */
    data object ExportStopped : ToastMessage
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
    /** The editor made from a photo (Make it a video meme) has finished arriving: the Meme sounds sheet opens by itself. */
    data object ArrivedFromPhoto : VideoEditorIntent

    /** The editor opened from the Sounds tab's Use flow (P3-15) and has arrived: its sound at 0:00 bonks once and the phone ticks. */
    data object ArrivedWithSound : VideoEditorIntent

    /** The same, but the sound did not come along: a toast says so. */
    data object ArrivedWithoutSound : VideoEditorIntent

    /** Got it on the one-time note, or its time ran out. */
    data object DismissVideoMemeNote : VideoEditorIntent

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

    /** The picker's sound would not play (a damaged file): the editor shows its "won't play" toast above the sheet's scrim. */
    data object SoundUnplayable : VideoEditorIntent

    /** The picker handed over a sound with its file ready (a row's Add block, or "Add to video"): it lands at the playhead. */
    data class AddSound(val sound: PickedSound) : VideoEditorIntent

    /** The Keyframes tool of the selected item: opens the keyframe bar (P4-02b). */
    data object KeyframesTool : VideoEditorIntent

    /** The keyframe bar's Close, or system back with it open. */
    data object CloseKeyframeBar : VideoEditorIntent

    data object KeyframePrevious : VideoEditorIntent

    data object KeyframeNext : VideoEditorIntent

    /** The bar's Add keyframe / Remove keyframe tool: one tool, which removes when the playhead is on a keyframe. */
    data object KeyframeToggle : VideoEditorIntent

    data object KeyframeEasingTool : VideoEditorIntent

    data object CloseEasingPanel : VideoEditorIntent

    /** An Easing tile: one step if it changes the easing, and the segment plays either way. */
    data class KeyframeEasingChosen(val easing: app.memix.core.model.project.Easing) : VideoEditorIntent

    /** Move here in the keyframe bar: the selected keyframe goes to the playhead. */
    data object KeyframeMoveHere : VideoEditorIntent

    data object KeyframeClear : VideoEditorIntent

    /** A tap on the diamond at [timeUs] (the timeline moves the playhead): it is the selected keyframe. */
    data class KeyframeSelected(val timeUs: Long) : VideoEditorIntent

    /** A diamond's drag released: the keyframe at [fromUs] goes to [toUs], one step. */
    data class KeyframeMoved(val fromUs: Long, val toUs: Long) : VideoEditorIntent

    /** A diamond's screen-reader actions: Remove and Easing for the keyframe at [timeUs]. */
    data class KeyframeRemoveAt(val timeUs: Long) : VideoEditorIntent

    data class KeyframeEasingAt(val timeUs: Long) : VideoEditorIntent

    /** The Volume tool of the selected sound: opens the Volume panel. */
    data object VolumeTool : VideoEditorIntent

    /** The Volume slider moved to [volume] (1 = 100%); heard at once, nothing committed yet. */
    data class VolumeChanged(val volume: Float) : VideoEditorIntent

    /** The slider was let go (or stepped): the panel's volume is one Volume step (none if it's where it began). */
    data object VolumeReleased : VideoEditorIntent

    /** Done, system back or Escape on the Volume panel. */
    data object CloseVolumePanel : VideoEditorIntent

    /** The Audio effects tool of the selected sound: opens its panel (P3-09), or explains when the sound's file is gone. */
    data object AudioEffectsTool : VideoEditorIntent

    /** Done, system back or Escape on the Audio effects panel. */
    data object CloseAudioEffects : VideoEditorIntent

    /** A tab of the Audio effects panel; it stays for the visit. */
    data class AudioEffectsTabSelected(val tab: AudioEffectsTab) : VideoEditorIntent

    /** A slider of the Audio effects panel moved to [value] (semitones, speed, seconds of fade, or 0 to 1 for bass, distortion and echo); nothing committed yet. */
    data class AudioEffectChanged(val part: AudioEffectPart, val value: Float) : VideoEditorIntent

    /** The slider of [part] was let go (or stepped): its value is one step, named for it (none if it's where it began). */
    data class AudioEffectReleased(val part: AudioEffectPart) : VideoEditorIntent

    /** A tile of the Voice tab: one step ("Voice"), none if it is the voice already set. */
    data class AudioVoiceSelected(val preset: VoicePreset) : VideoEditorIntent

    /** A segment of the Echo tab's length: one step ("Echo length"), none if it is the one already set. */
    data class AudioEchoLengthSelected(val length: EchoLength) : VideoEditorIntent

    /** Reset in the Audio effects panel: every effect of the clip back to its default, one step. */
    data object ResetAudioEffects : VideoEditorIntent

    /** The Voiceover tool: opens its panel (the microphone is not asked yet). */
    data object VoiceoverTool : VideoEditorIntent

    /** Record: [microphoneGranted] is the phone's permission right now. Starts the 3 second countdown, or asks, or explains. */
    data class VoiceoverRecord(val microphoneGranted: Boolean) : VideoEditorIntent

    /** Cancel during the countdown, or Stop while recording. */
    data object VoiceoverStop : VideoEditorIntent

    /** Done, system back or Escape on the Voiceover panel: stops what runs, or closes when nothing does. */
    data object CloseVoiceoverPanel : VideoEditorIntent

    /** Android's microphone dialog closed with this answer. */
    data class MicrophoneAnswered(val granted: Boolean) : VideoEditorIntent

    /** The editor is back on screen (or the panel opened): the phone's microphone permission is [granted] now. */
    data class MicrophoneChecked(val granted: Boolean) : VideoEditorIntent

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

    /** Shadow and box (P2-11): the new setting, or null for "No shadow" / "No box". */
    data class SetCaptionShadow(val shadow: CaptionShadow?) : VideoEditorIntent
    data class SetCaptionBox(val box: CaptionBox?) : VideoEditorIntent

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
    data object StageGestureCancelled : VideoEditorIntent

    /** The Overlay tool: pauses and opens the picker for one video or photo (spec P4-01b → Entry and exit). */
    data object AddOverlayTool : VideoEditorIntent

    /** A tap on overlay [itemId] on the preview: selects it; on the selected overlay, plays or pauses. */
    data class OverlayTapped(val itemId: String) : VideoEditorIntent

    /** A drag, pinch or handle drag on overlay [itemId] began: playback pauses, it's selected, the stand-in draws it. */
    data class OverlayGestureStarted(val itemId: String) : VideoEditorIntent

    /** The gesture on overlay [itemId] ended at [transform]: one "Position and size" step (none if unchanged). */
    data class OverlayGestureEnded(val itemId: String, val transform: Transform) : VideoEditorIntent

    /** The Position, Opacity or Layer order tool of the selected overlay. */
    data class OpenOverlayPanel(val panel: OverlayPanel) : VideoEditorIntent

    /** Done, system back or Escape on an overlay panel. */
    data object CloseOverlayPanel : VideoEditorIntent

    /** The Opacity slider moved to [opacity] (0.1 to 1); seen at once, nothing committed yet. */
    data class OverlayOpacityChanged(val opacity: Float) : VideoEditorIntent

    /** The slider was let go (or stepped): one "Opacity" step, none if it is where it began. */
    data object OverlayOpacityReleased : VideoEditorIntent

    /** The Green screen tool of the selected clip: keying turns on at the defaults when it isn't on (spec P4-04 → Entry and exit). */
    data object GreenScreenTool : VideoEditorIntent

    /** The Mask tool of the selected clip: the panel opens on its Shape tab (spec P4-03b → Entry and exit). */
    data object MaskTool : VideoEditorIntent

    /** Done, system back or Escape on the Mask panel. */
    data object CloseMask : VideoEditorIntent

    data class MaskTabSelected(val tab: MaskTab) : VideoEditorIntent

    /** A shape tile (null is None): one "Mask" step. */
    data class MaskShapePicked(val shape: MaskShape?) : VideoEditorIntent

    /** Edge softness or Rounded corners moved: seen at once, nothing committed yet. */
    data class MaskSoftnessChanged(val softness: Float) : VideoEditorIntent
    data class MaskCornersChanged(val roundness: Float) : VideoEditorIntent

    /** A mask slider was let go (or stepped): one step, none if unchanged. */
    data object MaskSliderReleased : VideoEditorIntent

    /** Effects on the main bar: the sheet opens to add an effect at the playhead (P4-06). */
    data object EffectsTool : VideoEditorIntent

    /** Close, the scrim, back or Escape on the Effects sheet. */
    data object CloseEffectsSheet : VideoEditorIntent

    data class EffectsTabSelected(val tab: app.memix.core.model.project.EffectTab) : VideoEditorIntent

    /** A tile: lands the effect, or in replace mode swaps the selected item's. */
    data class EffectPicked(val effectId: String) : VideoEditorIntent

    /** Change effect on the selected effect item: the sheet in replace mode. */
    data object ChangeEffectTool : VideoEditorIntent

    /** Intensity on the selected effect item, and Done, back or Escape on its panel. */
    data object IntensityTool : VideoEditorIntent
    data object CloseIntensity : VideoEditorIntent

    /** The Intensity slider moved: seen at once, nothing committed yet; and let go (or stepped): one step. */
    data class EffectIntensityChanged(val intensity: Float) : VideoEditorIntent
    data object EffectIntensityReleased : VideoEditorIntent

    /** Transition on the selected main clip's bar: the panel for the cut after it opens, or the tool says why it can't (P4-08). */
    data object TransitionTool : VideoEditorIntent

    /** Speed on a video clip's bar (or an overlay's): the panel opens (P4-09a). */
    data object SpeedTool : VideoEditorIntent

    /** Speed on the main bar: it selects the video clip under the playhead and opens its panel. */
    data object SpeedMainTool : VideoEditorIntent

    /** Reverse on a video clip's bar (main or overlay): switches it on or off, one step (P4-10). */
    data object ReverseTool : VideoEditorIntent

    /** Freeze frame on a main video clip's bar: holds the picture at the playhead (P4-10). */
    data object FreezeFrameTool : VideoEditorIntent

    /** Done, system back or Escape on the Speed panel. */
    data object CloseSpeed : VideoEditorIntent

    /** A tile: that speed, one step. */
    data class SpeedTilePicked(val speed: Float) : VideoEditorIntent

    /** The slider moved (the readout follows, nothing committed), and was let go or stepped (one step). */
    data class SpeedChanged(val speed: Float) : VideoEditorIntent
    data object SpeedReleased : VideoEditorIntent

    /** The Sound choice: Keep or Mute. */
    data class SpeedSoundSelected(val keep: Boolean) : VideoEditorIntent

    /** Reset in the Speed panel. */
    data object ResetSpeed : VideoEditorIntent

    /** Done, system back or Escape on the Transition panel. */
    data object CloseTransition : VideoEditorIntent

    /** A tile (null is None): one step, then the cut plays once. */
    data class TransitionPicked(val id: String?) : VideoEditorIntent

    /** The Duration slider moved (µs, seen at once, nothing committed), and was let go or stepped (one step). */
    data class TransitionDurationChanged(val durationUs: Long) : VideoEditorIntent
    data object TransitionDurationReleased : VideoEditorIntent

    /** Filters on the main bar: the panel opens for every main clip (spec P4-07 → The tool and its scope). */
    data object FiltersAllTool : VideoEditorIntent

    /** Filters on the selected clip's bar: the panel opens for that clip. */
    data object FiltersTool : VideoEditorIntent

    /** Done, system back or Escape on the Filters panel. */
    data object CloseLook : VideoEditorIntent

    data class LookTabSelected(val tab: EditPhotoTab) : VideoEditorIntent

    /** A filter tile (null is None): one "Filter" step. */
    data class LookFilterPicked(val id: String?) : VideoEditorIntent

    /** The Intensity slider moved: seen at once, nothing committed yet. */
    data class LookIntensityChanged(val intensity: Float) : VideoEditorIntent
    data object LookIntensityReleased : VideoEditorIntent

    /** An Adjust slider moved or was let go: seen at once, then one step on release. */
    data class LookAdjustChanged(val part: AdjustPart, val value: Float) : VideoEditorIntent
    data class LookAdjustReleased(val part: AdjustPart) : VideoEditorIntent

    /** Reset in the Filters panel's header: the open tab's part goes back to normal, one step. */
    data object LookReset : VideoEditorIntent

    data class MaskInvertChanged(val invert: Boolean) : VideoEditorIntent

    /** A Position tab tap or a held arrow's release: one "Mask position" step ([repeats] taps), or an explanation. */
    data class MaskStepped(val step: MaskStep, val repeats: Int) : VideoEditorIntent

    /** A held Position tab arrow, [repeats] taps along: shown, not committed. */
    data class MaskStepHeld(val step: MaskStep, val repeats: Int) : VideoEditorIntent

    /** A finger went down on the stage's mask; the mask under the fingers, drawn at once; the fingers lifted with the mask where they left it. */
    data object MaskGestureStarted : VideoEditorIntent
    data class MaskGestureMoved(val mask: ClipMask) : VideoEditorIntent
    data class MaskGestureEnded(val mask: ClipMask) : VideoEditorIntent

    /** Done, system back or Escape on the Green screen panel (in pick mode the first back ends the mode). */
    data object CloseGreenScreen : VideoEditorIntent

    /** Turn off in the panel's header: one step, and the panel closes. */
    data object GreenScreenOff : VideoEditorIntent

    /** A key swatch (Green, Blue): one step. */
    data class GreenScreenColor(val argb: Int) : VideoEditorIntent

    /** Strength or Edge softness moved: seen at once, nothing committed yet. */
    data class GreenScreenStrengthChanged(val strength: Float) : VideoEditorIntent
    data class GreenScreenSoftnessChanged(val softness: Float) : VideoEditorIntent

    /** The slider was let go (or stepped): one step, none if unchanged. */
    data object GreenScreenReleased : VideoEditorIntent

    /** The eyedropper swatch: starts pick mode, or ends it. */
    data object GreenScreenPickToggled : VideoEditorIntent

    /** In pick mode the finger was lifted at that point: picks the color there, or misses. */
    data class GreenScreenPickReleased(val canvasFx: Float, val canvasFy: Float) : VideoEditorIntent

    /** Bring forward ([forward]) or Send back on the selected overlay: one "Layer order" step, or an explanation. */
    data class OverlayOrder(val forward: Boolean) : VideoEditorIntent

    /** The ratio pill: opens the Canvas panel, or closes it when it's open (spec P1-11). */
    data object RatioPill : VideoEditorIntent

    /** Done, system back or Escape on the Canvas panel. */
    data object CloseCanvasPanel : VideoEditorIntent

    /** A ratio tile: a preset applies at once; Custom shows the steppers. */
    data class SetCanvasRatio(val ratio: CanvasRatio) : VideoEditorIntent

    /** A custom stepper tap, or the release of a held one after [repeats] steps: one Ratio step. */
    data class CanvasStepped(val step: RatioStep, val repeats: Int = 1) : VideoEditorIntent

    /** A held custom stepper repeated: the readout and the pill show [repeats] steps along, nothing committed yet. */
    data class CanvasStepHeld(val step: RatioStep, val repeats: Int) : VideoEditorIntent

    /** Fit or Fill: every main clip at once. */
    data class SetClipsFit(val fit: FrameFit) : VideoEditorIntent

    /** A background swatch: the color [argb] (0xAARRGGBB), or blur when it's null. */
    data class SetCanvasBackground(val argb: Long?) : VideoEditorIntent

    /** The Image swatch with no picture yet, or Replace: the picker for the background picture opens (P2-12). */
    data object PickBackgroundImage : VideoEditorIntent

    /** Remove: the default color goes back. */
    data object RemoveBackgroundImage : VideoEditorIntent

    /** The background picture's file couldn't be read (decoding its thumbnail failed). */
    data class BackgroundImageUnreadable(val path: String) : VideoEditorIntent

    /** Export in the top bar: opens the Export sheet (spec P1-12). */
    data object ExportTool : VideoEditorIntent

    /** The Export sheet's close, scrim, drag, back or Escape. */
    data object CloseExportSheet : VideoEditorIntent

    /** A resolution segment, by its index in ExportResolution order. */
    data class ExportResolutionTapped(val index: Int) : VideoEditorIntent

    /** A frame rate segment, by its index in EXPORT_FRAME_RATES. */
    data class ExportFrameRateTapped(val index: Int) : VideoEditorIntent

    /** Export video in the sheet. */
    data object ExportVideo : VideoEditorIntent

    /** The app opened the Exporting screen for [VideoEditorUiState.openExportScreen]. */
    data object ExportScreenOpened : VideoEditorIntent
}
