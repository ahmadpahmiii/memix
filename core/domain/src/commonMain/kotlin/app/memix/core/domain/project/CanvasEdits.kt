package app.memix.core.domain.project

import app.memix.core.domain.text.CaptionBlockSize
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.CanvasCatalog
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.FrameFit
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.RatioTerms
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TrackKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Canvas edits (spec docs/ux/specs/P1-11-canvas.md → Ratio change, Interactions): the shape, how main clips sit in
 * it, and what shows around them. Like every edit, each returns a new Project, or this one unchanged when nothing
 * changes. The fixed numbers are in CanvasCatalog.
 */

/**
 * The caption renderer's layout (CaptionMeasurer.measure): the laid-out block of a text in a style at scale 1, in
 * canvas pixels, on a canvas of the given width and height.
 */
fun interface CaptionBlockMeasure {
    fun measure(text: String, style: CaptionStyle, canvasWidthPx: Int, canvasHeightPx: Int): CaptionBlockSize
}

/** What [setCanvasRatio] did: the new project, and whether it moved or shrank a caption (the toast says so). */
data class CanvasRatioChange(val project: Project, val movedLayers: Boolean)

/**
 * The canvas in preset [ratio] (CanvasCatalog's size table), or the custom ratio [customTerms] when [ratio] is
 * [CanvasRatio.CUSTOM]. In the same step, every caption that was fully inside the old frame is kept fully inside the new
 * one ([keptInside]); one the user hung off the edge keeps its place. Measures captions with [measure] at the old and
 * the new canvas size. Unchanged when the canvas already has that size and ratio, or the custom terms aren't allowed.
 */
fun Project.setCanvasRatio(ratio: CanvasRatio, customTerms: RatioTerms? = null, measure: CaptionBlockMeasure): CanvasRatioChange {
    val size = when (ratio) {
        CanvasRatio.CUSTOM -> customTerms?.takeIf(CanvasCatalog::allowsCustom)?.let(CanvasCatalog::customSize)
        else -> CanvasCatalog.presetSize(ratio)
    } ?: return CanvasRatioChange(this, movedLayers = false)
    val old = PixelSize(canvas.widthPx, canvas.heightPx)
    if (canvas.ratio == ratio && old == size) return CanvasRatioChange(this, movedLayers = false)
    var moved = false
    val tracks = tracks().map { track ->
        if (track.kind != TrackKind.TEXT) return@map track
        track.items.map { item ->
            val text = item as? TextItem ?: return@map item
            val kept = text.keptInside(old, size, measure)
            if (kept != text) moved = true
            kept
        }.let { track.copy(items = it) }
    }
    val resized = copy(canvas = canvas.copy(ratio = ratio, widthPx = size.widthPx, heightPx = size.heightPx))
    return CanvasRatioChange(resized.withTracks(tracks), moved)
}

/** Every main clip drawn [fit] (spec → Clips: it applies to all of them at once). */
fun Project.setClipsFit(fit: FrameFit): Project {
    val track = mainTrack() ?: return this
    if (track.items.filterIsInstance<MediaClip>().all { it.fit == fit }) return this
    return replaceTrack(track.copy(items = track.items.map { if (it is MediaClip) it.copy(fit = fit) else it }))
}

/** What shows around fitted clips: a color or a blurred copy of the clip. */
fun Project.setCanvasBackground(background: CanvasBackground): Project =
    if (canvas.background == background) this else copy(canvas = canvas.copy(background = background))

/** The fit every main clip shares; null when they differ (an older debug draft, a later per-clip tool) or there are none. */
fun Project.sharedClipsFit(): FrameFit? {
    val fits = mainTrack()?.items.orEmpty().filterIsInstance<MediaClip>().map { it.fit }.distinct()
    return fits.singleOrNull()
}

/**
 * Whether the background can show anywhere: some main clip is drawn Fit and its shape differs from the canvas by more
 * than CanvasCatalog.SAME_SHAPE_TOLERANCE. A clip of unknown size (media added before schema 2) counts as showing it.
 */
fun Project.backgroundShows(): Boolean {
    val canvasSize = PixelSize(canvas.widthPx, canvas.heightPx)
    return mainTrack()?.items.orEmpty().filterIsInstance<MediaClip>().any { clip ->
        val clipSize = clip.source.pixelSize
        clip.fit == FrameFit.FIT && (clipSize == null || !hasSameShape(clipSize, canvasSize))
    }
}

/** Whether [a] and [b] have the same width-to-height ratio within CanvasCatalog.SAME_SHAPE_TOLERANCE. */
fun hasSameShape(a: PixelSize, b: PixelSize): Boolean {
    if (a.widthPx <= 0 || a.heightPx <= 0 || b.widthPx <= 0 || b.heightPx <= 0) return false
    val aspectA = a.widthPx.toFloat() / a.heightPx
    val aspectB = b.widthPx.toFloat() / b.heightPx
    return abs(aspectA / aspectB - 1f) <= CanvasCatalog.SAME_SHAPE_TOLERANCE
}

/**
 * This caption on the canvas [new] after a ratio change from [old] (spec → Ratio change → Layers). Its box is its
 * laid-out block, outline included, scaled and turned. Fully inside [old] and crossing an edge of [new]: it shrinks
 * first if it's bigger than [new] less two margins (never below CaptionCatalog.MIN_SCALE), then moves straight in to
 * the margin. Still too big at the smallest size, it's centered on that axis. Partly outside [old]: unchanged.
 */
internal fun TextItem.keptInside(old: PixelSize, new: PixelSize, measure: CaptionBlockMeasure): TextItem {
    val oldBox = boxOn(old, transform.scale, measure)
    val oldCenterX = transform.centerX * old.widthPx
    val oldCenterY = transform.centerY * old.heightPx
    // Hung off the edge on purpose: it keeps its place (spec → Layers, 3).
    if (crosses(oldCenterX, oldBox.width, old.widthPx.toFloat()) || crosses(oldCenterY, oldBox.height, old.heightPx.toFloat())) return this

    var scale = transform.scale
    var box = boxOn(new, scale, measure)
    val newCenterX = transform.centerX * new.widthPx
    val newCenterY = transform.centerY * new.heightPx
    if (!crosses(newCenterX, box.width, new.widthPx.toFloat()) && !crosses(newCenterY, box.height, new.heightPx.toFloat())) return this

    val marginPx = min(new.widthPx, new.heightPx) * CanvasCatalog.LAYER_MARGIN_OF_SHORT_SIDE
    val roomWidth = new.widthPx - 2 * marginPx
    val roomHeight = new.heightPx - 2 * marginPx
    if (box.width > roomWidth + EDGE_TOLERANCE_PX || box.height > roomHeight + EDGE_TOLERANCE_PX) {
        // The box grows with the scale, so one ratio finds the size that fits.
        val fitting = scale * min(roomWidth / box.width, roomHeight / box.height)
        scale = max(fitting, min(scale, CaptionCatalog.MIN_SCALE))
        box = boxOn(new, scale, measure)
    }
    val centerX = keptOnAxis(newCenterX, box.width, new.widthPx.toFloat(), marginPx)
    val centerY = keptOnAxis(newCenterY, box.height, new.heightPx.toFloat(), marginPx)
    val kept = transform.copy(centerX = centerX / new.widthPx, centerY = centerY / new.heightPx, scale = scale)
    return if (kept == transform) this else copy(transform = kept)
}

// Where a box [length] long centered at [center] goes on an axis [size] long: where it is when it doesn't cross an edge,
// else just far enough in to sit [margin] inside it; centered when it can't fit between the margins.
private fun keptOnAxis(center: Float, length: Float, size: Float, margin: Float): Float {
    val half = length / 2
    return when {
        !crosses(center, length, size) -> center
        length > size - 2 * margin + EDGE_TOLERANCE_PX -> size / 2
        else -> center.coerceIn(margin + half, size - margin - half)
    }
}

// Whether a box [length] long centered at [center] reaches past either end of an axis [size] long.
private fun crosses(center: Float, length: Float, size: Float): Boolean =
    center - length / 2 < -EDGE_TOLERANCE_PX || center + length / 2 > size + EDGE_TOLERANCE_PX

/** The caption's box at [scale] on a canvas [canvasSize]: its turned block's bounds, in canvas pixels. */
private fun TextItem.boxOn(canvasSize: PixelSize, scale: Float, measure: CaptionBlockMeasure): BoxSize {
    val block = measure.measure(text, style, canvasSize.widthPx, canvasSize.heightPx)
    // The outline is stroked on the letters' edges, so half its width lies outside the block.
    val shortSide = min(canvasSize.widthPx, canvasSize.heightPx)
    val outlineOutside = if (style.outlineColor == null) 0f else shortSide * CaptionCatalog.FONT_SIZE_OF_SHORT_SIDE / CaptionCatalog.OUTLINE_WIDTH_DIVISOR / 2
    val width = (block.widthPx + 2 * outlineOutside) * scale
    val height = (block.heightPx + 2 * outlineOutside) * scale
    val radians = transform.rotationDegrees * PI.toFloat() / HALF_TURN
    val cosine = abs(cos(radians))
    val sine = abs(sin(radians))
    return BoxSize(width * cosine + height * sine, width * sine + height * cosine)
}

private data class BoxSize(val width: Float, val height: Float)

/** Rounding slack: a box this close to an edge counts as touching it, not crossing it. */
private const val EDGE_TOLERANCE_PX = 0.5f
private const val HALF_TURN = 180f
