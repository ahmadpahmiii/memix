package app.memix.core.model.project

import kotlin.math.abs
import kotlin.math.min

/*
 * What a meme format draws and where it invites words, as pure functions of (canvas, format) in canvas pixels, used by the
 * editor, the render hook and (P2-08) export, so they can't disagree (spec docs/ux/specs/P2-02-meme-formats.md → How a
 * format works). Every size is a share of S, the canvas's shorter side. Nothing here is saved.
 */

/** A rectangle in canvas pixels. */
data class FormatRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f

    /** The same rectangle moved in by [amount] on every side (never below zero size). */
    fun inset(amount: Float): FormatRect {
        val inner = amount.coerceAtMost(min(width, height) / 2f)
        return FormatRect(left + inner, top + inner, width - 2 * inner, height - 2 * inner)
    }
}

/** S: the canvas's shorter side in pixels. */
fun shortSideOf(canvas: PixelSize): Float = min(canvas.widthPx, canvas.heightPx).toFloat()

/** The caption bar's rectangle: the full width, [PhotoFormatCatalog.BAR_HEIGHT_OF_SHORT_SIDE] of S tall, at the top. */
fun captionBarRect(canvas: PixelSize): FormatRect =
    FormatRect(0f, 0f, canvas.widthPx.toFloat(), PhotoFormatCatalog.BAR_HEIGHT_OF_SHORT_SIDE * shortSideOf(canvas))

/**
 * The demotivational frame's rectangle (the line is drawn on its edge): inset 0.10 S on the left, right and top, ending
 * 0.40 S above the bottom (the text zone below it is 0.30 S tall with 0.10 S under it).
 */
fun demotivationalFrameRect(canvas: PixelSize): FormatRect {
    val s = shortSideOf(canvas)
    val margin = PhotoFormatCatalog.FRAME_MARGIN_OF_SHORT_SIDE * s
    val bottom = canvas.heightPx - PhotoFormatCatalog.FRAME_BOTTOM_FROM_CANVAS_BOTTOM_OF_SHORT_SIDE * s
    return FormatRect(margin, margin, canvas.widthPx - 2 * margin, (bottom - margin).coerceAtLeast(0f))
}

/**
 * Where the main photo is placed: the canvas below the bar (Caption bar), the frame inset a further 0.02 S (Demotivational);
 * null for a format with no picture area (plain, Classic, Bubbles, Panels).
 */
fun PhotoFormat?.pictureArea(canvas: PixelSize): FormatRect? = when (this) {
    CaptionBarFormat -> captionBarRect(canvas).let { FormatRect(0f, it.bottom, it.width, (canvas.heightPx - it.bottom).coerceAtLeast(0f)) }
    DemotivationalFormat -> demotivationalFrameRect(canvas).inset(PhotoFormatCatalog.FRAME_PICTURE_INSET_OF_SHORT_SIDE * shortSideOf(canvas))
    null, ClassicFormat, BubblesFormat, is PanelsFormat -> null
}

/** What a slot is waiting for: words or a bubble. */
enum class SlotKind { TEXT, BUBBLE }

/** A slot's name, for the hint's spoken position and the order of filling. */
enum class SlotId { TOP, BOTTOM, BAR, TITLE, SUBTITLE, BUBBLE }

/**
 * An empty place a format shows as an editor-only hint (spec → The slot hint): not content, never saved, rendered or exported.
 * ([centerX], [centerY]) is its center in canvas fractions. A new text there takes [style] at [scale]; a null [style] means
 * the last style used in this visit (Classic).
 */
data class FormatSlot(
    val id: SlotId,
    val kind: SlotKind,
    val centerX: Float,
    val centerY: Float,
    val style: CaptionStyle?,
    val scale: Float,
)

/** The format's slots in the order they fill; none for plain, Panels. */
fun PhotoFormat?.slots(canvas: PixelSize): List<FormatSlot> {
    val s = shortSideOf(canvas)
    val height = canvas.heightPx.toFloat()
    return when (this) {
        null, is PanelsFormat -> emptyList()
        ClassicFormat -> {
            val edge = PhotoFormatCatalog.CLASSIC_SLOT_EDGE_DISTANCE_OF_SHORT_SIDE * s / height
            listOf(
                FormatSlot(SlotId.TOP, SlotKind.TEXT, 0.5f, edge, style = null, scale = 1f),
                FormatSlot(SlotId.BOTTOM, SlotKind.TEXT, 0.5f, 1f - edge, style = null, scale = 1f),
            )
        }
        CaptionBarFormat -> listOf(
            FormatSlot(SlotId.BAR, SlotKind.TEXT, 0.5f, PhotoFormatCatalog.BAR_SLOT_CENTER_Y_OF_SHORT_SIDE * s / height, PhotoFormatCatalog.BAR_SLOT_STYLE, 1f),
        )
        DemotivationalFormat -> {
            val frameBottom = demotivationalFrameRect(canvas).bottom
            listOf(
                FormatSlot(SlotId.TITLE, SlotKind.TEXT, 0.5f, (frameBottom + PhotoFormatCatalog.TITLE_BELOW_FRAME_OF_SHORT_SIDE * s) / height, PhotoFormatCatalog.TITLE_SLOT_STYLE, 1f),
                FormatSlot(
                    SlotId.SUBTITLE, SlotKind.TEXT, 0.5f, (frameBottom + PhotoFormatCatalog.SUBTITLE_BELOW_FRAME_OF_SHORT_SIDE * s) / height,
                    PhotoFormatCatalog.SUBTITLE_SLOT_STYLE, PhotoFormatCatalog.SUBTITLE_SLOT_SCALE,
                ),
            )
        }
        BubblesFormat -> listOf(
            FormatSlot(SlotId.BUBBLE, SlotKind.BUBBLE, 0.5f, PhotoFormatCatalog.BUBBLE_LANDING_SLOTS.first(), style = null, scale = 1f),
        )
    }
}

/**
 * Whether [slot] is filled: a visible text layer (a visible bubble for the bubble slot) has its center within
 * [PhotoFormatCatalog.SLOT_TAKEN_WITHIN] of the slot's center on both axes, in canvas fractions, so a caption the user already
 * put there fills it and deleting it empties the slot again (spec → How a format works, rule 4).
 */
fun FormatSlot.isFilledBy(layers: List<Layer>): Boolean = layers.any { layer ->
    if (layer.hidden) return@any false
    val matches = when (kind) {
        SlotKind.TEXT -> layer is TextLayer
        SlotKind.BUBBLE -> layer is BubbleLayer
    }
    matches && abs(layer.transform.centerX - centerX) < PhotoFormatCatalog.SLOT_TAKEN_WITHIN &&
        abs(layer.transform.centerY - centerY) < PhotoFormatCatalog.SLOT_TAKEN_WITHIN
}

/** The slots of this format that nothing fills yet, in filling order. */
fun PhotoFormat?.emptySlots(canvas: PixelSize, layers: List<Layer>): List<FormatSlot> = slots(canvas).filterNot { it.isFilledBy(layers) }

/** The main photo: the lowest image layer of the stack, or null (spec → How a format works, rule 3). */
fun List<Layer>.mainPhoto(): ImageLayer? = firstOrNull { it is ImageLayer } as? ImageLayer

/**
 * Where the main photo [photo] sits under [format] on [canvas]: scale and center set so the whole photo lies inside the
 * picture area, centered; with no picture area, scale 1 centered (the plain placement). The scale is relative to the photo's
 * size at scale 1 ([fittedImageSize]).
 */
fun placeMainPhoto(format: PhotoFormat?, canvas: PixelSize, photo: PixelSize): Transform {
    val area = format.pictureArea(canvas) ?: return Transform()
    if (photo.widthPx <= 0 || photo.heightPx <= 0 || area.width <= 0f || area.height <= 0f) return Transform()
    val fitted = fittedImageSize(photo, canvas)
    if (fitted.width <= 0f) return Transform()
    val inside = min(area.width / photo.widthPx, area.height / photo.heightPx)
    return Transform(
        centerX = area.centerX / canvas.widthPx,
        centerY = area.centerY / canvas.heightPx,
        scale = photo.widthPx * inside / fitted.width,
    )
}

/**
 * Whether [layer] is where [format] put it: its center within [PhotoFormatCatalog.AS_PLACED_CENTER_TOLERANCE] of the placement's,
 * its scale within [PhotoFormatCatalog.AS_PLACED_SCALE_TOLERANCE] of it and no turn. A photo the user moved, zoomed or turned
 * stays where it is when the format changes (the ratio-change rule keeps it inside).
 */
fun isAsPlaced(layer: ImageLayer, format: PhotoFormat?, canvas: PixelSize): Boolean {
    val expected = if (format.pictureArea(canvas) == null) Transform() else placeMainPhoto(format, canvas, layer.source.pixelSize ?: return false)
    val transform = layer.transform
    return abs(transform.centerX - expected.centerX) <= PhotoFormatCatalog.AS_PLACED_CENTER_TOLERANCE &&
        abs(transform.centerY - expected.centerY) <= PhotoFormatCatalog.AS_PLACED_CENTER_TOLERANCE &&
        abs(transform.scale / expected.scale - 1f) <= PhotoFormatCatalog.AS_PLACED_SCALE_TOLERANCE &&
        abs(transform.rotationDegrees) <= AS_PLACED_ROTATION_TOLERANCE_DEGREES
}

private const val AS_PLACED_ROTATION_TOLERANCE_DEGREES = 0.5f
