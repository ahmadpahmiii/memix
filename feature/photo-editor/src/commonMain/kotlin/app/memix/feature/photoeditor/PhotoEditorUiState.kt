package app.memix.feature.photoeditor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import app.memix.core.domain.EditorTool
import app.memix.core.domain.text.CaptionBlockSize
import app.memix.core.domain.text.MeasureCaptionUseCase
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.BoxSize
import app.memix.core.domain.project.AdjustPart
import app.memix.core.domain.project.EditPhotoTab
import app.memix.core.model.project.CutOut
import app.memix.core.model.project.ImageEdit
import app.memix.core.model.project.editOrNull
import app.memix.core.model.project.pictureZoom
import app.memix.core.model.project.shownSize
import app.memix.core.model.project.BubbleLayer
import app.memix.core.model.project.CensorLayer
import app.memix.core.model.project.CensorMode
import app.memix.core.model.project.CensorShape
import app.memix.core.model.project.DrawCatalog
import app.memix.core.model.project.DrawingLayer
import app.memix.core.model.project.Stroke
import app.memix.core.model.project.BubbleShape
import app.memix.core.model.project.CaptionLook
import app.memix.core.model.project.CaptionBox
import app.memix.core.model.project.CaptionShadow
import app.memix.core.model.project.bubbleBox
import app.memix.core.model.project.tipIn
import app.memix.core.domain.project.bodyOn
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.stickerSizeAtScaleOne
import app.memix.core.ui.text.TextEntryUi
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.CaptionBarFormat
import app.memix.core.model.project.ClassicFormat
import app.memix.core.model.project.DemotivationalFormat
import app.memix.core.model.project.FormatSlot
import app.memix.core.model.project.BubblesFormat
import app.memix.core.model.project.PanelFamily
import app.memix.core.model.project.PanelLayouts
import app.memix.core.model.project.PanelsFormat
import app.memix.core.model.project.PhotoFormat
import app.memix.core.model.project.panelsFormatOf
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.Layer
import app.memix.core.model.project.PhotoCatalog
import app.memix.core.model.project.RatioTerms
import app.memix.core.model.project.StickerLayer
import app.memix.core.model.project.TextLayer
import app.memix.core.model.project.Transform
import app.memix.core.model.project.fittedImageSize
import app.memix.core.model.project.PixelSize
import app.memix.core.ui.editor.SaveProblem
import app.memix.feature.mediaimport.ImportIntent
import app.memix.feature.mediaimport.ProjectImportUi
import app.memix.core.ui.layers.GestureTarget
import app.memix.core.ui.panels.PositionStep
import app.memix.core.ui.panels.RatioStep
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** What the photo editor shows (spec docs/ux/specs/P2-01-photo-editor-shell.md → States). */
@Immutable
data class PhotoEditorUiState(
    /** Null until the draft has opened. */
    val scene: SceneUi? = null,
    /** The layers as the gestures and the Layers panel see them, bottom to top; empty until the draft has opened. */
    val layers: ImmutableList<SceneLayerUi> = persistentListOf(),
    val selectedLayerId: String? = null,
    /** False when a visible, opaque, unturned layer covers the whole frame: the Canvas panel says the background doesn't show. */
    val backgroundShows: Boolean = true,
    /** The open tool panel, or null (spec → Tool bars: the main bar or the selected-layer bar shows then). */
    val panel: PhotoPanel? = null,
    /** The image layer whose Crop screen is open (a mode over the editor), or null (spec P2-04 → The Crop screen). */
    val cropLayerId: String? = null,
    /** The cut-out screen over the editor (a mode, like Crop), or null (spec P2-07). */
    val cutOut: CutOutSessionUi? = null,
    /** Draw mode (spec P2-05): the Draw bar replaces the tool bar and a one-finger drag draws. Null when the mode is off. */
    val draw: DrawModeUi? = null,
    /** The brush for this visit's Draw sessions: it outlives a session, so the next one starts from the last color and size. */
    val brush: DrawBrushUi = DrawBrushUi(),
    /** The Canvas panel's own state while it is open (the custom ratio's steppers). */
    val canvasPanel: PhotoCanvasPanelUi? = null,
    /** Layers whose file is missing or can't be decoded; reported by the scene as it tries to draw them. */
    val missingLayerIds: ImmutableSet<String> = persistentSetOf(),
    /** A layer a held Position arrow has moved but not committed yet; the stage draws it there until the release. */
    val held: HeldTransform? = null,
    /** The scene's meme format; null is plain (spec P2-02). */
    val format: PhotoFormat? = null,
    /** The format's slots nothing fills yet: each shows an editor-only hint while nothing is selected and no panel is open. */
    val emptySlots: ImmutableList<FormatSlot> = persistentListOf(),
    /** Caption bar or Demotivational with a photo that doesn't fill the picture area's width: the Formats panel says why (spec → Formats panel). */
    val formatHintRoom: Boolean = false,
    val canUndo: Boolean = false,
    /** A layer has been on the canvas at some point in this visit. */
    val hadLayers: Boolean = false,
    val canRedo: Boolean = false,
    /** Goes up on every gesture release, so the stage lets go of the layer it held at its final place. */
    val gestureEndCount: Int = 0,
    val isSaveFailing: Boolean = false,
    val saveBanner: SaveProblem? = null,
    val savedAgainCount: Int = 0,
    val canFreeUpSpace: Boolean = true,
    /** The canvas's background picture can't be read (its file is gone); the Canvas panel says so and the canvas is black (P2-12). */
    val backgroundMissing: Boolean = false,
    /** Measures a text as it's typed, with the engine's renderer; null until the editor has started. */
    val captionMeasure: PhotoCaptionMeasure? = null,
    /** Typing a text (spec P2-03 → Typing); while set, Undo, Redo and the ratio pill are inert and the tool bar is the entry bar. */
    val textEntry: TextEntryUi? = null,
    /** Typing a new bubble: its look (the entry itself is [textEntry]); null while typing a text or editing. */
    val bubbleEntry: BubbleEntryUi? = null,
    /** The selected Panels cell (0 is cell 1); a cell and a layer are never selected together. */
    val selectedCell: Int? = null,
    /** The selected Panels divider: its gutter shows in the selection line and the Divider panel is open. */
    val selectedDivider: Int? = null,
    /** Panels cells whose photo file is missing or can't be decoded. */
    val missingCells: ImmutableSet<Int> = persistentSetOf(),
    /** The format a held Panels arrow (a cell's view, a divider) shows until the release commits; null for the project's own. */
    val heldFormat: PhotoFormat? = null,
    /** A bubble's tail a held Tail arrow has moved but not committed yet; the stage draws it there until the release. */
    val heldTail: HeldTail? = null,
    /** The picker the app should open now; the app opens it and sends [PhotoEditorIntent.PickerOpened] (P2-12). */
    val pickRequest: PhotoPickRequest? = null,
    /** The import sheet's state while a picked photo copies; read only by the sheet. Null until the editor starts. */
    val photoImport: ProjectImportUi? = null,
    val toast: PhotoToast? = null,
    val unsavedSheetOpen: Boolean = false,
    /** The editor is done; the app closes the screen. */
    val isClosing: Boolean = false,
    /** Closing because the draft couldn't be opened (damaged, from a newer app, or deleted meanwhile): the app says so. */
    val couldntOpen: Boolean = false,
) {
    val selectedLayer: SceneLayerUi? get() = selectedLayerId?.let { id -> layers.firstOrNull { it.id == id } }
    /** Whether the empty-canvas card shows: nothing on a plain canvas (a format's own hints and pick tiles invite instead). */
    val showsEmptyCard: Boolean get() = scene != null && layers.isEmpty() && format == null

    /** The empty card points to Undo only after a layer was on the canvas and Undo has a step; a ratio or color change alone leaves nothing to bring back. */
    val emptiedByUser: Boolean get() = hadLayers && canUndo
    val canvas: Canvas? get() = scene?.canvas

    /** Whether a panel hides the tool bar (and then the selected-layer bar too). */
    val panelOpen: Boolean get() = panel != null
}

/** Why the photo picker is opened (spec P2-12 → Entry and exit); P2-13 adds Add photo. The app saves it across a restart behind the picker. */
sealed interface PhotoPickRequest {
    /** One photo for the canvas background. */
    data object Background : PhotoPickRequest

    /** One photo as a new top layer (P2-13). */
    data object AddLayer : PhotoPickRequest

    /** One photo for cell [index] of a Panels format (P2-02): an empty cell's pick tile, or Replace. */
    data class Cell(val index: Int) : PhotoPickRequest
}

/** Draw mode is on. [newLayerId] is the id the first stroke gives a new drawing layer when no drawing layer is selected. */
@Immutable
data class DrawModeUi(val newLayerId: String)

/** The chosen brush: Brush or Eraser, the color (an index into the caption palette) and one size index for each (spec P2-05 → The Draw bar). */
@Immutable
data class DrawBrushUi(
    val erasing: Boolean = false,
    val colorIndex: Int = DrawCatalog.DEFAULT_COLOR_INDEX,
    val brushSizeIndex: Int = DrawCatalog.DEFAULT_SIZE_INDEX,
    val eraserSizeIndex: Int = DrawCatalog.DEFAULT_SIZE_INDEX,
) {
    val sizeIndex: Int get() = if (erasing) eraserSizeIndex else brushSizeIndex
}

/** The look a new bubble being typed has (shape, fill); its tail is the default for where it lands. */
@Immutable
data class BubbleEntryUi(val shape: BubbleShape, val fill: ArgbColor)

/** [layerId]'s tail tip (body widths and heights from its center) while a Tail arrow is held. */
@Immutable
data class HeldTail(val layerId: String, val tailX: Float, val tailY: Float)

/** [layerId] shown at [transform] while a Position arrow is held. */
@Immutable
data class HeldTransform(val layerId: String, val transform: Transform)

/** What the scene draws: the canvas and the layers, bottom to top. Compared by value, so the stage redraws only when it changes. */
@Immutable
data class SceneUi(val canvas: Canvas, val layers: ImmutableList<Layer>, val format: PhotoFormat? = null) {
    val aspectRatio: Float get() = canvas.widthPx.toFloat() / canvas.heightPx

    /** "4:3": the ratio in lowest terms, never translated. */
    val ratioLabel: String get() = RatioTerms.of(PixelSize(canvas.widthPx, canvas.heightPx)).toString()
}

/**
 * One layer as the gestures, the selection frame and the Layers panel see it: its state flags, its picture's file and its
 * own box at scale 1 on the canvas (an image's picture fitted inside the frame, spec → Size at scale 1).
 */
@Immutable
data class SceneLayerUi(
    override val id: String,
    val kind: LayerKind,
    override val transform: Transform,
    override val locked: Boolean,
    val hidden: Boolean,
    val opacity: Float,
    /** The app's copy of the picture, relative to the files directory (an image or a sticker); null for text. */
    val path: String?,
    /** A text layer's words, as typed; null for the other kinds. */
    val text: String? = null,
    /** A text layer's look; null for the other kinds. */
    val style: CaptionStyle? = null,
    /** The picture's long side in pixels as shown, 0 when it wasn't measured: pictures are never decoded above it. */
    val pictureLongSidePx: Int,
    /** How many times the box the whole picture is drawn (a crop or a straighten): the decode size grows with it (P2-04). */
    val pictureZoom: Float = 1f,
    /** The image tools changed the picture (crop, turn, filter, ...): the Layers row says "Edited". */
    val edited: Boolean = false,
    /** An image's edit and size as shown, for the Crop screen; null for the other kinds. */
    val edit: ImageEdit? = null,
    val pictureSize: PixelSize? = null,
    /** An image's removed background (P2-07); null for the whole picture and for the other kinds. */
    val cutOut: CutOut? = null,
    override val boxWidthPx: Float,
    override val boxHeightPx: Float,
    override val scaleRange: ClosedFloatingPointRange<Float>,
    /** A bubble's own parts; null for the other kinds. */
    val bubble: BubbleUi? = null,
    /** A censor's own parts; null for the other kinds. */
    val censor: CensorUi? = null,
    /** A drawing's strokes, for its row's thumbnail; empty for the other kinds. */
    val strokes: List<Stroke> = emptyList(),
    override val boxOffsetXPx: Float = 0f,
    override val boxOffsetYPx: Float = 0f,
) : GestureTarget {
    // A bubble's scale-and-rotate handle keeps clear of its tail tip (the tail handle's place).
    override val handleAvoidXPx: Float? get() = bubble?.let { it.tailX * it.bodyWidthPx }
    override val handleAvoidYPx: Float? get() = bubble?.let { it.tailY * it.bodyHeightPx }
}

/** A censor as the editor handles it: its mode, shape, strength and own size in S at scale 1. */
@Immutable
data class CensorUi(val mode: CensorMode, val shape: CensorShape, val strength: Float, val widthS: Float, val heightS: Float)

/** A bubble as the editor handles it: its look, its body at scale 1 and its tail tip (body widths and heights from the center). */
@Immutable
data class BubbleUi(val shape: BubbleShape, val fill: ArgbColor, val tailX: Float, val tailY: Float, val bodyWidthPx: Float, val bodyHeightPx: Float)

enum class LayerKind { IMAGE, TEXT, STICKER, BUBBLE, DRAWING, CENSOR }

/**
 * [this] layer for the editor, on a canvas of [canvasSize]: an image's box is its picture fitted inside the frame, a
 * sticker's its picture at 35% of the shorter side, a text's its laid-out block from [measure] (the caption renderer), all
 * at scale 1.
 */
internal fun Layer.toUi(canvasSize: PixelSize, measure: (text: String, style: CaptionStyle, canvasWidthPx: Int, canvasHeightPx: Int, look: CaptionLook) -> CaptionBlockSize): SceneLayerUi {
    val picture = when (this) {
        is ImageLayer -> source.pixelSize
        is StickerLayer -> source.pixelSize
        is TextLayer, is BubbleLayer, is DrawingLayer, is CensorLayer -> null
    }
    val drawingBounds = (this as? DrawingLayer)?.let { DrawCatalog.boundsOf(it) }
    val shortSide = minOf(canvasSize.widthPx, canvasSize.heightPx).toFloat()
    val bubbleBody = (this as? BubbleLayer)?.let { bodyOn(canvasSize) { text, style, w, h, look -> measure(text, style, w, h, look) } }
    val bubbleBox = (this as? BubbleLayer)?.let { layer -> bubbleBody?.let { bubbleBox(layer.shape, it, layer.tipIn(it), canvasSize) } }
    val box = when (this) {
        is BubbleLayer -> bubbleBox?.let { BoxSize(it.width, it.height) }
        is ImageLayer -> picture?.let { fittedImageSize(shownSize(it), canvasSize) }
        is StickerLayer -> stickerSizeAtScaleOne(picture, canvasSize)
        is DrawingLayer -> BoxSize((drawingBounds?.width ?: 0f) * shortSide, (drawingBounds?.height ?: 0f) * shortSide)
        is CensorLayer -> BoxSize(widthS * shortSide, heightS * shortSide)
        is TextLayer -> measure(text, style, canvasSize.widthPx, canvasSize.heightPx, CaptionLook.Text).let { BoxSize(it.widthPx, it.heightPx) }
    }
    return SceneLayerUi(
        id = id,
        kind = when (this) {
            is ImageLayer -> LayerKind.IMAGE
            is TextLayer -> LayerKind.TEXT
            is StickerLayer -> LayerKind.STICKER
            is BubbleLayer -> LayerKind.BUBBLE
            is DrawingLayer -> LayerKind.DRAWING
            is CensorLayer -> LayerKind.CENSOR
        },
        transform = transform,
        locked = locked,
        hidden = hidden,
        opacity = opacity,
        path = when (this) {
            is ImageLayer -> source.cachedCopyPath
            is StickerLayer -> source.cachedCopyPath
            is TextLayer, is BubbleLayer, is DrawingLayer, is CensorLayer -> null
        },
        text = (this as? TextLayer)?.text ?: (this as? BubbleLayer)?.text,
        style = (this as? TextLayer)?.style ?: (this as? BubbleLayer)?.style,
        pictureLongSidePx = picture?.let { maxOf(it.widthPx, it.heightPx) } ?: 0,
        pictureZoom = (this as? ImageLayer)?.let { layer -> picture?.let { layer.edit.pictureZoom(it) } } ?: 1f,
        edited = (this as? ImageLayer)?.editOrNull() != null,
        edit = (this as? ImageLayer)?.editOrNull(),
        cutOut = (this as? ImageLayer)?.cutOut,
        pictureSize = if (this is ImageLayer) picture else null,
        boxWidthPx = box?.width ?: 0f,
        boxHeightPx = box?.height ?: 0f,
        scaleRange = PhotoCatalog.scaleRangeOf(this),
        strokes = (this as? DrawingLayer)?.strokes.orEmpty(),
        censor = (this as? CensorLayer)?.let { CensorUi(it.mode, it.shape, it.strength, it.widthS, it.heightS) },
        bubble = (this as? BubbleLayer)?.let { BubbleUi(it.shape, it.fill, it.tailX, it.tailY, bubbleBody?.width ?: 0f, bubbleBody?.height ?: 0f) },
        boxOffsetXPx = bubbleBox?.offsetX ?: 0f,
        boxOffsetYPx = bubbleBox?.offsetY ?: 0f,
    )
}

/** The tool panels over the tool bar. They never stack: opening one closes another. */
enum class PhotoPanel { LAYERS, OPACITY, POSITION, CANVAS, STYLE, FORMATS, LAYOUT, DIVIDER, CELL_POSITION, EDIT_PHOTO, CENSOR }

/** The six tiles of the Formats panel, in the spec's order; [PLAIN] is no format (null in the model). */
enum class FormatChoice(val edit: PhotoEdit) {
    PLAIN(PhotoEdit.FORMAT_PLAIN),
    CLASSIC(PhotoEdit.FORMAT_CLASSIC),
    CAPTION_BAR(PhotoEdit.FORMAT_CAPTION_BAR),
    PANELS(PhotoEdit.FORMAT_PANELS),
    DEMOTIVATIONAL(PhotoEdit.FORMAT_DEMOTIVATIONAL),
    BUBBLES(PhotoEdit.FORMAT_BUBBLES),
}

/** The format a tile chooses; Panels starts as two photos side by side, the main photo in the first cell (the edit fills it). */
internal fun FormatChoice.toFormat(): PhotoFormat? = when (this) {
    FormatChoice.PLAIN -> null
    FormatChoice.CLASSIC -> ClassicFormat
    FormatChoice.CAPTION_BAR -> CaptionBarFormat
    FormatChoice.PANELS -> panelsFormatOf(PanelLayouts.default, emptyList())
    FormatChoice.DEMOTIVATIONAL -> DemotivationalFormat
    FormatChoice.BUBBLES -> BubblesFormat
}

/** The tile that is selected for [this] format. */
internal fun PhotoFormat?.toChoice(): FormatChoice = when (this) {
    null -> FormatChoice.PLAIN
    ClassicFormat -> FormatChoice.CLASSIC
    CaptionBarFormat -> FormatChoice.CAPTION_BAR
    is PanelsFormat -> FormatChoice.PANELS
    DemotivationalFormat -> FormatChoice.DEMOTIVATIONAL
    BubblesFormat -> FormatChoice.BUBBLES
}

/** The Canvas panel's custom ratio: the steppers' numbers, and the numbers a held stepper shows before its release commits. */
@Immutable
data class PhotoCanvasPanelUi(val customTerms: RatioTerms? = null, val heldTerms: RatioTerms? = null) {
    val shownTerms: RatioTerms? get() = heldTerms ?: customTerms
}

/** What an undo step is called in "Undo: Opacity". */
enum class PhotoEdit(val tool: EditorTool) {
    TRANSFORM(EditorTool.TRANSFORM_LAYER),
    POSITION(EditorTool.POSITION_LAYER),
    OPACITY(EditorTool.LAYER_OPACITY),
    LOCK(EditorTool.LOCK_LAYER),
    UNLOCK(EditorTool.UNLOCK_LAYER),
    HIDE(EditorTool.HIDE_LAYER),
    SHOW(EditorTool.SHOW_LAYER),
    REORDER(EditorTool.REORDER_LAYER),
    DELETE(EditorTool.DELETE),
    DUPLICATE(EditorTool.DUPLICATE),
    CANVAS_RATIO(EditorTool.CANVAS_RATIO),
    CANVAS_BACKGROUND(EditorTool.CANVAS_BACKGROUND),
    ADD_PHOTO(EditorTool.ADD_PHOTO),

    // Text layers use P1-10's ids, so text analytics join across editors (spec P2-03 → Analytics). A gesture on a text is
    // `transform_text`, a Position-panel step `position_text`.
    ADD_TEXT(EditorTool.ADD_TEXT),
    EDIT_TEXT(EditorTool.EDIT_TEXT),
    TEXT_FONT(EditorTool.TEXT_FONT),
    TEXT_COLOR(EditorTool.TEXT_COLOR),
    TEXT_OUTLINE(EditorTool.TEXT_OUTLINE),
    TEXT_SHADOW(EditorTool.TEXT_SHADOW),
    TEXT_BOX(EditorTool.TEXT_BOX),
    // Every part of a crop screen's Done is one step called "Crop" (the events logged are the parts).
    CROP(EditorTool.CROP),
    // Apply on the cut-out screen and Remove cut-out (P2-07); strokes and softness are the screen's own and fire nothing.
    REMOVE_BG(EditorTool.REMOVE_BG),
    CLEAR_CUTOUT(EditorTool.CLEAR_CUTOUT),
    // The Edit photo panel (P2-04b): each tap or release is one step; the six adjustments are all "Adjust".
    FILTER(EditorTool.FILTER),
    ADJUST_BRIGHTNESS(EditorTool.ADJUST_BRIGHTNESS),
    ADJUST_CONTRAST(EditorTool.ADJUST_CONTRAST),
    ADJUST_SATURATION(EditorTool.ADJUST_SATURATION),
    ADJUST_WARMTH(EditorTool.ADJUST_WARMTH),
    ADJUST_SHARPEN(EditorTool.ADJUST_SHARPEN),
    ADJUST_VIGNETTE(EditorTool.ADJUST_VIGNETTE),
    DEEP_FRY(EditorTool.DEEP_FRY),
    EDIT_RESET(EditorTool.EDIT_PHOTO_RESET),
    TRANSFORM_TEXT(EditorTool.TRANSFORM_TEXT),
    POSITION_TEXT(EditorTool.POSITION_TEXT),

    // Meme formats (P2-02): every format tool is "Format" in the undo history; the analytics id is the tool's.
    FORMAT_PLAIN(EditorTool.FORMAT_PLAIN),
    FORMAT_CLASSIC(EditorTool.FORMAT_CLASSIC),
    FORMAT_CAPTION_BAR(EditorTool.FORMAT_CAPTION_BAR),
    FORMAT_PANELS(EditorTool.FORMAT_PANELS),
    FORMAT_DEMOTIVATIONAL(EditorTool.FORMAT_DEMOTIVATIONAL),
    FORMAT_BUBBLES(EditorTool.FORMAT_BUBBLES),
    ADD_BUBBLE(EditorTool.ADD_BUBBLE),
    BUBBLE_SHAPE(EditorTool.BUBBLE_SHAPE),
    BUBBLE_FILL(EditorTool.BUBBLE_FILL),
    BUBBLE_TAIL(EditorTool.BUBBLE_TAIL),
    PANELS_LAYOUT(EditorTool.PANELS_LAYOUT),
    PANELS_DIVIDER(EditorTool.PANELS_DIVIDER),
    ADD_CELL_PHOTO(EditorTool.ADD_CELL_PHOTO),
    REMOVE_CELL_PHOTO(EditorTool.REMOVE_CELL_PHOTO),
    PANEL_PHOTO_TRANSFORM(EditorTool.CELL_TRANSFORM),
    PANEL_PHOTO_POSITION(EditorTool.CELL_POSITION),

    // Drawing (P2-05): each committed stroke is one step.
    DRAW_STROKE(EditorTool.DRAW_STROKE),
    ERASE_STROKE(EditorTool.ERASE_STROKE),

    // Censor (P2-05b): the undo names; the events sent are the mode's ids, through loggedAs.
    ADD_CENSOR(EditorTool.ADD_CENSOR),
    CENSOR_MODE(EditorTool.CENSOR_MODE),
    CENSOR_SHAPE(EditorTool.CENSOR_SHAPE),
    CENSOR_STRENGTH(EditorTool.CENSOR_STRENGTH),
    ;

    companion object {
        fun of(tool: EditorTool): PhotoEdit? = entries.firstOrNull { it.tool == tool }
    }
}

/** Why a tool explains instead of acting. Tools stay enabled and say why on tap (spec → Locked layers, Layers panel). */
enum class PhotoExplanation {
    LAYER_LOCKED,
    LAYER_ONE,
    LAYER_FRONT,
    LAYER_BACK,
    LAYER_EDGE,
    LAYER_MISSING,
    LAYERS_EMPTY,
    SIZE_SMALLEST,
    SIZE_BIGGEST,
    CANVAS_WIDEST,
    CANVAS_TALLEST,
    CANVAS_MIN,
    CANVAS_MAX,
    DRAW_NOTHING,
    DRAW_FULL,
    CENSOR_MIN,
    CENSOR_MAX,
}

/** A toast on the stage; a new [id] restarts its timer. [isSpokenOnly] when the save banner holds the slot. */
@Immutable
data class PhotoToast(val id: Long, val message: PhotoToastMessage, val isSpokenOnly: Boolean = false)

sealed interface PhotoToastMessage {
    data class Undone(val edit: PhotoEdit) : PhotoToastMessage

    data class Redone(val edit: PhotoEdit) : PhotoToastMessage

    data class Explain(val explanation: PhotoExplanation) : PhotoToastMessage

    /** Once per open, when a layer's picture file is missing. */
    data object MissingMedia : PhotoToastMessage

    /** A ratio change moved or shrank a layer. */
    data object LayersMoved : PhotoToastMessage

    /** Spoken only, after Delete, Duplicate, Lock, Hide: "Delete: Photo, layer 2 of 3". */
    data class Edited(val edit: PhotoEdit, val position: Int, val count: Int) : PhotoToastMessage

    /** Spoken only, after a Move up or down: "Layer 2 of 3". */
    data class LayerOrder(val position: Int, val count: Int) : PhotoToastMessage

    /** Spoken only, after the Position panel sized or turned. */
    data class PositionTransformed(val scalePercent: Int, val degrees: Int) : PhotoToastMessage

    /** Spoken only, after the Position panel moved. */
    data class PositionMoved(val acrossPercent: Int, val downPercent: Int) : PhotoToastMessage

    /** Spoken only, after the opacity slider. */
    data class OpacityChanged(val percent: Int) : PhotoToastMessage

    /** Spoken only, after a ratio step. */
    data class RatioStepped(val label: String) : PhotoToastMessage

    /** Once per open, when the background picture's file is missing; [ratioLabel] is the pill's text ("9:16"). */
    data class BackgroundMissing(val ratioLabel: String) : PhotoToastMessage

    /** Spoken only, after a background picture landed. */
    data object BackgroundImageSet : PhotoToastMessage

    /** Spoken only, after Remove. */
    data object BackgroundImageRemoved : PhotoToastMessage

    /** Spoken only, after a photo landed as a new top layer: "Photo added in front". */
    data object LayerAdded : PhotoToastMessage

    /** Spoken only, after a new text landed: "Text added". */
    data object TextAdded : PhotoToastMessage

    /** Spoken only, after a new bubble landed: "Bubble added". */
    data object BubbleAdded : PhotoToastMessage

    /** Spoken only, after a format tile: "Format: Caption bar". */
    data class FormatSet(val choice: FormatChoice) : PhotoToastMessage

    /** Spoken only, when Draw mode opens: "Drawing. Draw with one finger." */
    data object DrawMode : PhotoToastMessage

    /** Spoken only, after Done: "Drawing saved". */
    data object DrawSaved : PhotoToastMessage

    /** Spoken only, after a censor landed: "Censor added". */
    data object CensorAdded : PhotoToastMessage

    /** The photos in removed Panels cells left the meme (a lower count, or Panels was left); Undo brings them back. */
    data object PanelPhotosDropped : PhotoToastMessage
}

sealed interface PhotoEditorIntent {
    data object Undo : PhotoEditorIntent

    data object Redo : PhotoEditorIntent

    /** The Close button. */
    data object Close : PhotoEditorIntent

    /** System back or Escape: the innermost thing first (spec → System back order). */
    data object Back : PhotoEditorIntent

    data object LeaveAnyway : PhotoEditorIntent

    data object StayInEditor : PhotoEditorIntent

    data object DismissSaveBanner : PhotoEditorIntent

    data object FreeUpSpaceUnavailable : PhotoEditorIntent

    data object AppStarted : PhotoEditorIntent

    data object AppStopped : PhotoEditorIntent

    data class ToastTimedOut(val id: Long) : PhotoEditorIntent

    /** The scene tried to draw these files' pictures (app copies, relative paths) and couldn't read them. */
    data class MediaMissing(val paths: Set<String>) : PhotoEditorIntent

    // Selection and gestures on the canvas.
    data class SelectLayer(val layerId: String) : PhotoEditorIntent

    /** A tap on the background or the stage outside the frame. */
    data object ClearSelection : PhotoEditorIntent

    data class LayerGestureStarted(val layerId: String) : PhotoEditorIntent

    data class LayerGestureEnded(val layerId: String, val transform: Transform) : PhotoEditorIntent

    data object LayerGestureCancelled : PhotoEditorIntent

    data class LockedLayerTouched(val layerId: String) : PhotoEditorIntent

    // Tools.
    data class OpenPanel(val panel: PhotoPanel) : PhotoEditorIntent

    data object ClosePanel : PhotoEditorIntent

    data class OpacityCommitted(val layerId: String, val opacity: Float) : PhotoEditorIntent

    data class PositionStepped(val layerId: String, val step: PositionStep, val repeats: Int) : PhotoEditorIntent

    data class PositionStepHeld(val layerId: String, val step: PositionStep, val repeats: Int) : PhotoEditorIntent

    data class DeleteLayer(val layerId: String) : PhotoEditorIntent

    data class DuplicateLayer(val layerId: String) : PhotoEditorIntent

    data class SetLocked(val layerId: String, val locked: Boolean) : PhotoEditorIntent

    data class SetHidden(val layerId: String, val hidden: Boolean) : PhotoEditorIntent

    data class MoveLayer(val layerId: String, val towardFront: Boolean) : PhotoEditorIntent

    /** A row dropped in the Layers panel at [indexFromFront] (0 is the front layer). */
    data class ReorderLayer(val layerId: String, val indexFromFront: Int) : PhotoEditorIntent

    // The Canvas panel.
    data object RatioPill : PhotoEditorIntent

    data class SetCanvasRatio(val ratio: CanvasRatio) : PhotoEditorIntent

    data class CanvasStepped(val step: RatioStep, val repeats: Int) : PhotoEditorIntent

    data class CanvasStepHeld(val step: RatioStep, val repeats: Int) : PhotoEditorIntent

    data class SetCanvasBackground(val argb: Long) : PhotoEditorIntent

    // Text (P2-03).
    /** The Text tool: typing a new text at the first free slot. */
    data object AddText : PhotoEditorIntent

    /** Edit text on the bar, or a tap on the selected text. */
    data object EditText : PhotoEditorIntent

    /** The words in the entry field now. */
    data class TextEntryChanged(val text: String) : PhotoEditorIntent

    /** Done, back, Escape, Close or the keyboard going away: the words are kept. */
    data object TextEntryDone : PhotoEditorIntent

    data class SetTextFont(val fontId: String) : PhotoEditorIntent

    data class SetTextColor(val argb: Long) : PhotoEditorIntent

    /** The outline color, or null for "No outline". */
    data class SetTextOutline(val argb: Long?) : PhotoEditorIntent

    /** Shadow and box (P2-11): the new setting, or null for "No shadow" / "No box". */
    data class SetTextShadow(val shadow: CaptionShadow?) : PhotoEditorIntent
    data class SetTextBox(val box: CaptionBox?) : PhotoEditorIntent

    /** The Crop tool: on [layerId], or with null (the main bar) on the main photo, which it selects (spec P2-04 → Entry and exit). */
    data class OpenCrop(val layerId: String?) : PhotoEditorIntent

    /** The Crop screen's Done: its whole edit as one step. */
    data class CropDone(val edit: ImageEdit) : PhotoEditorIntent

    /** The Edit photo tool: on [layerId], or with null (the main bar) on the main photo, which it selects. */
    data class OpenEditPhoto(val layerId: String?) : PhotoEditorIntent

    /** A filter tile: the filter [id] at 100%, or null for None. */
    data class SetFilter(val id: String?) : PhotoEditorIntent

    /** The Intensity slider's release, 0 to 1. */
    data class SetFilterIntensity(val intensity: Float) : PhotoEditorIntent

    /** An Adjust slider's release. */
    data class SetAdjust(val part: AdjustPart, val value: Float) : PhotoEditorIntent

    /** The Deep-fry slider's release, 0 to 1. */
    data class SetDeepFry(val amount: Float) : PhotoEditorIntent

    /** The header's Reset on [tab]. */
    data class ResetEditTab(val tab: EditPhotoTab) : PhotoEditorIntent

    /** The Crop screen's Close, back or Escape: nothing was applied. */
    data object CropClosed : PhotoEditorIntent

    /** The Remove BG tool: on [layerId], or with null (the main bar) on the main photo, which it selects (spec P2-07 → D2). */
    data class OpenCutOut(val layerId: String?) : PhotoEditorIntent

    /** Try again on a status card: runs the check, the download and the segmenter again. */
    data object CutOutRetry : PhotoEditorIntent

    /** The cut-out screen's Apply: the mask, the refine strokes and the softness as one step. */
    data class CutOutApply(val strokes: List<Stroke>, val softness: Float) : PhotoEditorIntent

    /** Save as sticker: [png] is the trimmed cut-out as the screen shows it (null when nothing is left, or when it couldn't be made), [key] says which cut-out it is. */
    data class CutOutSaveSticker(val png: ByteArray?, val key: String, val couldNotEncode: Boolean = false) : PhotoEditorIntent

    /** A tap on Apply or Save as sticker while they can't act: the stage says why (not ready yet, or nothing erased by hand yet). */
    data object CutOutInertTapped : PhotoEditorIntent

    /** The Quiet "Remove cut-out" button: the layer gets its whole picture back, as one step. */
    data object CutOutRemove : PhotoEditorIntent

    /** The cut-out screen's Close, back or Escape: nothing was applied. */
    data object CutOutClosed : PhotoEditorIntent

    // Meme formats (P2-02).
    /** A tile of the Formats panel; the active tile does nothing. */
    data class SetFormat(val choice: FormatChoice) : PhotoEditorIntent

    /** The Add bubble tool: typing a new bubble at the first free landing slot. */
    data object AddBubble : PhotoEditorIntent

    data class SetBubbleShape(val shape: BubbleShape) : PhotoEditorIntent

    data class SetBubbleFill(val argb: Long) : PhotoEditorIntent

    /** The tail handle was released at ([tailX], [tailY]) body widths and heights from the center. */
    data class TailMoved(val layerId: String, val tailX: Float, val tailY: Float) : PhotoEditorIntent

    /** A Tail arrow tapped or released after [repeats] steps; [step] is one of the four Move steps. */
    data class TailStepped(val layerId: String, val step: PositionStep, val repeats: Int) : PhotoEditorIntent

    data class TailStepHeld(val layerId: String, val step: PositionStep, val repeats: Int) : PhotoEditorIntent

    data class ResetTail(val layerId: String) : PhotoEditorIntent

    // Panels (P2-02).
    /** A tap on a cell: an empty (or missing) one opens the picker, a filled one is selected. */
    data class TapCell(val index: Int) : PhotoEditorIntent

    /** Replace on the selected-cell bar. */
    data object ReplaceCell : PhotoEditorIntent

    /** Remove on the selected-cell bar: the photo leaves the cell, one step. */
    data object RemoveCell : PhotoEditorIntent

    /** A cell's pan or pinch released at ([zoom], [centerX], [centerY]). */
    data class CellViewReleased(val index: Int, val zoom: Float, val centerX: Float, val centerY: Float) : PhotoEditorIntent

    data class CellStepped(val index: Int, val step: PositionStep, val repeats: Int) : PhotoEditorIntent

    data class CellStepHeld(val index: Int, val step: PositionStep, val repeats: Int) : PhotoEditorIntent

    /** A tap on a divider's handle: it is selected and the Divider panel opens. */
    data class SelectDivider(val index: Int) : PhotoEditorIntent

    /** A divider's drag released at [position], a share of its split's extent. */
    data class DividerMoved(val index: Int, val position: Float) : PhotoEditorIntent

    /** A Divider-panel arrow: [direction] is -1 (left or up) or 1 (right or down), [repeats] steps of 2% of the extent. */
    data class DividerStepped(val index: Int, val direction: Int, val repeats: Int) : PhotoEditorIntent

    data class DividerStepHeld(val index: Int, val direction: Int, val repeats: Int) : PhotoEditorIntent

    /** The Divider panel's Even button. */
    data class DividerEven(val index: Int) : PhotoEditorIntent

    data class SetPanelCount(val count: Int) : PhotoEditorIntent

    data class SetPanelFamily(val family: PanelFamily) : PhotoEditorIntent

    data object MakePanelsEven : PhotoEditorIntent

    /** A tap on an empty slot's hint: typing a new text (or bubble) placed in that slot. */
    data class TapSlot(val slot: FormatSlot) : PhotoEditorIntent

    // Drawing (P2-05).
    /** The Draw tool, on the main bar or the selected-drawing bar: Draw mode opens. */
    data object OpenDraw : PhotoEditorIntent

    /** Done, back or Escape in Draw mode. */
    data object DrawDone : PhotoEditorIntent

    data class SetEraser(val erasing: Boolean) : PhotoEditorIntent

    data class SetBrushColor(val index: Int) : PhotoEditorIntent

    data class SetBrushSize(val index: Int) : PhotoEditorIntent

    /** A finger went down on the canvas in Draw mode: Undo and Redo wait until it is up. */
    data object StrokeStarted : PhotoEditorIntent

    /** The stroke was cancelled (system back, or the finger was taken by the system). */
    data object StrokeCancelled : PhotoEditorIntent

    /**
     * The finger lifted: a stroke of [count] points ([xs], [ys] in S from the canvas center) to layer [layerId] (an existing
     * drawing, or the id a new one gets), as the brush or eraser chosen when it started.
     */
    class StrokeFinished(
        val layerId: String,
        val erasing: Boolean,
        val colorIndex: Int,
        val widthS: Float,
        val xs: FloatArray,
        val ys: FloatArray,
        val count: Int,
    ) : PhotoEditorIntent

    // Censor (P2-05b).
    /** The Censor tool on the main bar: a censor lands in the middle and its panel opens. */
    data object AddCensor : PhotoEditorIntent

    data class SetCensorMode(val mode: CensorMode) : PhotoEditorIntent

    data class SetCensorShape(val shape: CensorShape) : PhotoEditorIntent

    /** The Strength slider's release, 0 to 1. */
    data class SetCensorStrength(val layerId: String, val strength: Float) : PhotoEditorIntent

    /** An edge handle released: the censor's own size in S and its new place (the opposite side stayed). */
    data class CensorResized(val layerId: String, val widthS: Float, val heightS: Float, val transform: Transform) : PhotoEditorIntent

    /** A Width or Height tap or a hold's release: [widthSteps] and [heightSteps] steps of 2% of S (negative is smaller). */
    data class CensorSizeStepped(val layerId: String, val widthSteps: Int, val heightSteps: Int) : PhotoEditorIntent

    // The background picture (P2-12).
    /** The Image swatch with no picture yet, or Replace. */
    data object PickBackgroundImage : PhotoEditorIntent

    /** The Add photo tool, or the Layers panel's Add photo button (P2-13): the picker for a new top layer opens. */
    data object AddPhoto : PhotoEditorIntent

    data object RemoveBackgroundImage : PhotoEditorIntent

    /** The picker opened. */
    data object PickerOpened : PhotoEditorIntent

    /** The picker couldn't open: the import sheet says so. */
    data object PickerUnavailable : PhotoEditorIntent

    /** The picker returned [items] for [request] (empty when nothing was picked: nothing changes). */
    data class PhotoPicked(val request: PhotoPickRequest, val items: List<MediaOrigin>) : PhotoEditorIntent

    /** Something on the import sheet (Cancel, Continue, Close, back from the storage screen, ...). */
    data class Import(val intent: ImportIntent) : PhotoEditorIntent

    /** "Pick again" on the import sheet: it closes, then the same picker opens again. */
    data object PickAgain : PhotoEditorIntent
}

/**
 * Measures a caption with the engine's renderer while it's typed, so its frame fits the words on every keystroke. Compared by
 * identity: one per editor.
 */
@Stable
class PhotoCaptionMeasure internal constructor(private val measure: MeasureCaptionUseCase) {
    /** [text] in [style] at scale 1, in canvas pixels on a canvas [canvasWidthPx] x [canvasHeightPx]. */
    fun blockOf(text: String, style: CaptionStyle, canvasWidthPx: Int, canvasHeightPx: Int, look: CaptionLook = CaptionLook.Text): CaptionBlockSize =
        measure(text, style, canvasWidthPx, canvasHeightPx, look)
}

/**
 * The cut-out screen's data (spec docs/ux/specs/P2-07-background-removal.md): which layer, what the segmenter is doing, and the
 * saved cut-out it opened on, if any. The strokes and softness being edited are the screen's own until Apply.
 */
@Immutable
data class CutOutSessionUi(
    val layerId: String,
    val phase: CutOutPhase,
    /** The mask and what was refined so far: the layer's saved cut-out, or the fresh mask once the segmenter is done; null before. */
    val cutOut: CutOut? = null,
    /** Whether the layer already had a cut-out when the screen opened (the Quiet "Remove cut-out" shows). */
    val hadCutOut: Boolean = false,
    /** A toast-like line on the stage after Save as sticker (serial so the same line twice shows again); cleared after a few seconds. */
    val notice: CutOutNotice? = null,
)

sealed interface CutOutPhase {
    /** The model is downloading (first use); [slow] after 30 s. */
    data class Preparing(val fraction: Float?, val slow: Boolean = false) : CutOutPhase

    /** The model is ready and the photo is being segmented. */
    data object Working : CutOutPhase

    data object Ready : CutOutPhase

    /** The picture has no subject; the brush stays live. */
    data object NothingFound : CutOutPhase

    data class Failed(val problem: CutOutProblem) : CutOutPhase
}

/** What Save as sticker answers (spec P2-07 → States). */
enum class CutOutNotice { SAVED, EMPTY, ALREADY_SAVED, FAILED, NOT_READY, NOTHING_TO_APPLY }

/** The status cards that end in Try again (spec → States). */
enum class CutOutProblem { UNAVAILABLE, OFFLINE, MEMORY, SLOW }
