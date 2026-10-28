package app.memix.feature.photoeditor

import androidx.compose.runtime.Immutable
import app.memix.core.domain.EditorTool
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasRatio
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
    /** The Canvas panel's own state while it is open (the custom ratio's steppers). */
    val canvasPanel: PhotoCanvasPanelUi? = null,
    /** Layers whose file is missing or can't be decoded; reported by the scene as it tries to draw them. */
    val missingLayerIds: ImmutableSet<String> = persistentSetOf(),
    /** A layer a held Position arrow has moved but not committed yet; the stage draws it there until the release. */
    val held: HeldTransform? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Goes up on every gesture release, so the stage lets go of the layer it held at its final place. */
    val gestureEndCount: Int = 0,
    val isSaveFailing: Boolean = false,
    val saveBanner: SaveProblem? = null,
    val savedAgainCount: Int = 0,
    val canFreeUpSpace: Boolean = true,
    /** The canvas's background picture can't be read (its file is gone); the Canvas panel says so and the canvas is black (P2-12). */
    val backgroundMissing: Boolean = false,
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
    val isEmpty: Boolean get() = scene != null && layers.isEmpty()
    val canvas: Canvas? get() = scene?.canvas

    /** Whether a panel hides the tool bar (and then the selected-layer bar too). */
    val panelOpen: Boolean get() = panel != null
}

/** Why the photo picker is opened (spec P2-12 → Entry and exit); P2-13 adds Add photo. The app saves it across a restart behind the picker. */
sealed interface PhotoPickRequest {
    /** One photo for the canvas background. */
    data object Background : PhotoPickRequest
}

/** [layerId] shown at [transform] while a Position arrow is held. */
@Immutable
data class HeldTransform(val layerId: String, val transform: Transform)

/** What the scene draws: the canvas and the layers, bottom to top. Compared by value, so the stage redraws only when it changes. */
@Immutable
data class SceneUi(val canvas: Canvas, val layers: ImmutableList<Layer>) {
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
    /** The app's copy of the picture, relative to the files directory; null for text. */
    val path: String?,
    /** The picture's long side in pixels as shown, 0 when it wasn't measured: pictures are never decoded above it. */
    val pictureLongSidePx: Int,
    override val boxWidthPx: Float,
    override val boxHeightPx: Float,
    override val scaleRange: ClosedFloatingPointRange<Float>,
) : GestureTarget

enum class LayerKind { IMAGE, TEXT, STICKER }

/** [this] layer for the editor, on a canvas of [canvasSize]. */
internal fun Layer.toUi(canvasSize: PixelSize): SceneLayerUi {
    val picture = (this as? ImageLayer)?.source?.pixelSize
    val box = picture?.let { fittedImageSize(it, canvasSize) }
    return SceneLayerUi(
        id = id,
        kind = when (this) {
            is ImageLayer -> LayerKind.IMAGE
            is TextLayer -> LayerKind.TEXT
            is StickerLayer -> LayerKind.STICKER
        },
        transform = transform,
        locked = locked,
        hidden = hidden,
        opacity = opacity,
        path = (this as? ImageLayer)?.source?.cachedCopyPath,
        pictureLongSidePx = picture?.let { maxOf(it.widthPx, it.heightPx) } ?: 0,
        boxWidthPx = box?.width ?: 0f,
        boxHeightPx = box?.height ?: 0f,
        scaleRange = PhotoCatalog.scaleRangeOf(this),
    )
}

/** The tool panels over the tool bar. They never stack: opening one closes another. */
enum class PhotoPanel { LAYERS, OPACITY, POSITION, CANVAS }

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

    // The background picture (P2-12).
    /** The Image swatch with no picture yet, or Replace. */
    data object PickBackgroundImage : PhotoEditorIntent

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
