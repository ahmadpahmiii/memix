package app.memix.core.domain.project

import app.memix.core.domain.text.CaptionBlockSize
import app.memix.core.model.project.BoxSize
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.CanvasCatalog
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.FrameFit
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.PhotoCatalog
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import app.memix.core.model.project.RatioTerms
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.hasSameShape
import app.memix.core.model.project.turnedBounds
import kotlin.math.min

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
    val size = canvasSizeFor(ratio, customTerms) ?: return CanvasRatioChange(this, movedLayers = false)
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

/** The canvas size of preset [ratio], or of the custom ratio [customTerms] for [CanvasRatio.CUSTOM]; null when the terms aren't allowed. */
internal fun canvasSizeFor(ratio: CanvasRatio, customTerms: RatioTerms?): PixelSize? = when (ratio) {
    CanvasRatio.CUSTOM -> customTerms?.takeIf(CanvasCatalog::allowsCustom)?.let(CanvasCatalog::customSize)
    else -> CanvasCatalog.presetSize(ratio)
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

/** The picture behind everything, or null when the background is a color or Blur. */
fun Project.backgroundImage(): MediaRef? = (canvas.background as? CanvasBackground.Image)?.source

/** The picture [source] as the background, covering the canvas (a new pick, or Replace: [source] is always a new file). */
fun Project.setBackgroundImage(source: MediaRef): Project = setCanvasBackground(CanvasBackground.Image(source))

/**
 * The background picture gone, the editor's default color back (video black, photo white). The picture's file stays in
 * the draft's media folder while Undo can bring it back (spec P2-12 → Entry and exit). Unchanged without a picture.
 */
fun Project.removeBackgroundImage(): Project {
    if (canvas.background !is CanvasBackground.Image) return this
    val color = if (type == ProjectType.PHOTO) PhotoCatalog.BACKGROUND else CanvasCatalog.VIDEO_BACKGROUND
    return setCanvasBackground(CanvasBackground.Solid(color))
}

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

/**
 * This caption on the canvas [new] after a ratio change from [old] (spec → Ratio change → Layers). Its box is its
 * laid-out block, outline included, scaled and turned; the rule is [keptInside]'s, with CaptionCatalog.MIN_SCALE as the
 * smallest size and the 2% margin.
 */
internal fun TextItem.keptInside(old: PixelSize, new: PixelSize, measure: CaptionBlockMeasure): TextItem {
    val kept = transform.keptInside(old, new, CaptionCatalog.MIN_SCALE, CanvasCatalog.LAYER_MARGIN_OF_SHORT_SIDE) { canvas, scale ->
        boxOn(canvas, scale, measure)
    }
    return if (kept == transform) this else copy(transform = kept)
}

/** The caption's box at [scale] on a canvas [canvasSize]: its turned block's bounds, in canvas pixels. */
private fun TextItem.boxOn(canvasSize: PixelSize, scale: Float, measure: CaptionBlockMeasure): BoxSize {
    val block = measure.measure(text, style, canvasSize.widthPx, canvasSize.heightPx)
    // The outline is stroked on the letters' edges, so half its width lies outside the block.
    val shortSide = min(canvasSize.widthPx, canvasSize.heightPx)
    val outlineOutside = if (style.outlineColor == null) 0f else shortSide * CaptionCatalog.FONT_SIZE_OF_SHORT_SIDE / CaptionCatalog.OUTLINE_WIDTH_DIVISOR / 2
    val width = (block.widthPx + 2 * outlineOutside) * scale
    val height = (block.heightPx + 2 * outlineOutside) * scale
    return turnedBounds(BoxSize(width, height), transform.rotationDegrees)
}
