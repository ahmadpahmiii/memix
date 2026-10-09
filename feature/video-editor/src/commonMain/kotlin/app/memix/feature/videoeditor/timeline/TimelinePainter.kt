package app.memix.feature.videoeditor.timeline

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.TrackKind
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the timeline (spec docs/ux/specs/P1-05-timeline.md): the ruler, the lanes with their items and thumbnail
 * strips, the selection and the playhead. Each draw call takes the playhead and the scale as plain values, read by
 * the caller inside its draw block, so a scroll frame redraws without recomposing. Only items and tiles near the
 * screen are drawn, found by binary search on start time.
 *
 * Text is measured once and cached here (labels per item, ruler labels per time), so a scroll frame measures
 * nothing new; only a label that is being cut by the screen edge is re-measured, in `space-2` steps.
 */
@Stable
internal class TimelinePainter(
    private val textMeasurer: TextMeasurer,
    private val labelStyle: TextStyle,
    private val rulerStyle: TextStyle,
    private val originalAudioLabel: String,
    private val missingFileLabel: String,
    density: Density,
) {
    private val gapPx = with(density) { MemixSpacing.space1.toPx() }
    private val minItemWidthPx = with(density) { MemixSize.playheadWidth.toPx() }
    private val cornerPx = with(density) { MemixShapes.radiusSm.topStart.toPx(Size.Unspecified, density) }
    private val labelPaddingPx = with(density) { MemixSpacing.space2.toPx() }
    private val outlineGapPx = with(density) { MemixStroke.strokeHairline.toPx() }
    private val outlineWidthPx = with(density) { MemixStroke.strokeSelection.toPx() }
    private val handleWidthPx = with(density) { MemixSize.trimHandleWidth.toPx() }
    private val majorTickPx = with(density) { MemixSpacing.space2.toPx() }
    private val tickWidthPx = with(density) { MemixStroke.strokeHairline.toPx() }
    private val rulerLabelInsetPx = with(density) { MemixSpacing.space1.toPx() }
    private val playheadWidthPx = with(density) { MemixSize.playheadWidth.toPx() }
    private val playheadHeadPx = with(density) { MemixSize.playheadHeadSize.toPx() }
    private val badgeInsetPx = with(density) { MemixSpacing.space1.toPx() }
    private val badgeMinMarginPx = with(density) { MemixSpacing.space2.toPx() }

    /** Thumbnail tiles are squares the main clip's height. */
    val tileSizePx: Int = with(density) { MemixSize.trackHeightVideo.roundToPx() }

    private val itemPath = Path()
    private val outlineStroke = Stroke(outlineWidthPx)
    private val itemLabels = HashMap<String, CachedLabel>()
    private val rulerLabels = HashMap<Long, TextLayoutResult>()
    private var rulerLabelsStepUs = 0L

    // ---- Ruler --------------------------------------------------------------------------------------------------

    /**
     * Ticks and labels for the time on screen. [labelSpacingPx] is the widest label plus `space-4`: the step is the
     * smallest on the ladder that keeps labels that far apart. Minor ticks closer than `space-2` are left out.
     */
    fun DrawScope.drawRuler(playheadUs: Long, pxPerUs: Float, labelSpacingPx: Float) {
        if (pxPerUs <= 0f) return
        val centerPx = size.width / 2
        val step = rulerStepFor(pxPerUs, labelSpacingPx)
        // Only a handful of labels are on screen; scrolling through an hour must not keep all of them.
        if (step.labelEveryUs != rulerLabelsStepUs || rulerLabels.size > MAX_CACHED_RULER_LABELS) {
            rulerLabels.clear()
            rulerLabelsStepUs = step.labelEveryUs
        }
        val minorEveryUs = step.labelEveryUs / step.minorTicks
        val tickEveryUs = if (minorEveryUs * pxPerUs >= majorTickPx) minorEveryUs else step.labelEveryUs
        // A label that starts just left of the screen still shows its end.
        val firstUs = max(0L, timeAt(-labelSpacingPx, playheadUs, centerPx, pxPerUs))
        val lastUs = timeAt(size.width, playheadUs, centerPx, pxPerUs)
        var tickUs = (firstUs + tickEveryUs - 1) / tickEveryUs * tickEveryUs
        while (tickUs <= lastUs) {
            val x = xOf(tickUs, playheadUs, centerPx, pxPerUs)
            val isMajor = tickUs % step.labelEveryUs == 0L
            val tickHeightPx = if (isMajor) majorTickPx else majorTickPx / 2
            drawLine(MemixColors.textMuted, Offset(x, size.height - tickHeightPx), Offset(x, size.height), tickWidthPx)
            if (isMajor) drawText(rulerLabelLayout(tickUs), color = MemixColors.textMuted, topLeft = Offset(x + rulerLabelInsetPx, rulerLabelInsetPx))
            tickUs += tickEveryUs
        }
    }

    private fun rulerLabelLayout(timeUs: Long): TextLayoutResult =
        rulerLabels.getOrPut(timeUs) { textMeasurer.measure(rulerLabel(timeUs), rulerStyle, softWrap = false, maxLines = 1) }

    // ---- Lanes --------------------------------------------------------------------------------------------------

    /** Bands, items, strips, washes and labels of every row, for the time on screen plus one tile on each side. */
    fun DrawScope.drawLanes(
        timeline: TimelineUi,
        lanes: LaneLayout,
        playheadUs: Long,
        pxPerUs: Float,
        thumbnails: TimelineThumbnails?,
        missingSources: Set<MediaRef>,
    ) {
        if (pxPerUs <= 0f) return
        val centerPx = size.width / 2
        val windowStartUs = timeAt(-tileSizePx.toFloat(), playheadUs, centerPx, pxPerUs)
        val windowEndUs = timeAt(size.width + tileSizePx, playheadUs, centerPx, pxPerUs)
        val zeroX = xOf(0, playheadUs, centerPx, pxPerUs)
        val endX = xOf(timeline.lengthUs, playheadUs, centerPx, pxPerUs)
        for (rowIndex in lanes.rows.indices) {
            val row = lanes.rows[rowIndex]
            if (row.track.kind != TrackKind.MAIN_VIDEO && timeline.lengthUs > 0) drawBand(row, zeroX, endX)
            row.track.forEachItemBetween(windowStartUs, windowEndUs) { item ->
                val left = xOf(item.startUs, playheadUs, centerPx, pxPerUs)
                val right = left + itemWidthPx(item, pxPerUs)
                when (row.track.kind) {
                    TrackKind.MAIN_VIDEO -> drawStrip(item, row, left, right, pxPerUs, thumbnails, missingSources)
                    // Overlays get their thumbnails and their label rules with P4-01.
                    TrackKind.OVERLAY -> {
                        drawItemBody(row, left, right, MemixColors.trackVideo)
                        if (item.endUs > timeline.lengthUs) drawPastEndWash(row, left, right, endX)
                    }
                    TrackKind.TEXT, TrackKind.STICKER, TrackKind.MEME_SOUND, TrackKind.AUDIO, TrackKind.EFFECT -> {
                        drawItemBody(row, left, right, trackColorOf(row.track.kind))
                        if (item.endUs > timeline.lengthUs) drawPastEndWash(row, left, right, endX)
                        labelOf(item)?.let { drawItemLabel(item.id, it, row, left, right, endX, MemixColors.onTrack) }
                    }
                }
            }
        }
    }

    private fun DrawScope.drawBand(row: LaneRow, zeroX: Float, endX: Float) {
        val left = max(zeroX, 0f)
        val right = min(endX, size.width)
        if (right <= left) return
        drawRect(MemixColors.surface, Offset(left, row.itemTopPx), Size(right - left, row.itemHeightPx))
    }

    /** Duration × scale minus the `space-1` gap at its end, but never thinner than the playhead. */
    fun itemWidthPx(item: ItemUi, pxPerUs: Float): Float = max(item.durationUs * pxPerUs - gapPx, minItemWidthPx)

    /** The item drawn under [x] in [row] (the later one where two overlap, as it's drawn on top), or null. */
    fun itemAt(row: LaneRow, x: Float, playheadUs: Long, centerPx: Float, pxPerUs: Float): ItemUi? {
        if (pxPerUs <= 0f) return null
        val timeUs = timeAt(x, playheadUs, centerPx, pxPerUs)
        // An item thinner than its minimum width can be hit just after its end.
        val reachUs = (minItemWidthPx / pxPerUs).toLong() + 1
        var hit: ItemUi? = null
        row.track.forEachItemBetween(timeUs - reachUs, timeUs) { item ->
            val left = xOf(item.startUs, playheadUs, centerPx, pxPerUs)
            if (x >= left && x <= left + itemWidthPx(item, pxPerUs)) hit = item
        }
        return hit
    }

    private fun DrawScope.drawItemBody(row: LaneRow, left: Float, right: Float, color: Color) {
        drawRoundRect(color, Offset(left, row.itemTopPx), Size(right - left, row.itemHeightPx), CornerRadius(cornerPx))
    }

    /** The part after the end of the video, washed with `scrim`: it doesn't play or export. */
    private fun DrawScope.drawPastEndWash(row: LaneRow, left: Float, right: Float, endX: Float) {
        val washLeft = max(left, endX)
        if (washLeft >= right) return
        clipPath(itemShape(left, row.itemTopPx, right, row.itemTopPx + row.itemHeightPx)) {
            drawRect(MemixColors.scrim, Offset(washLeft, row.itemTopPx), Size(right - washLeft, row.itemHeightPx))
        }
    }

    /**
     * A main clip: `track-video` with its thumbnail tiles, or "File missing" when its file is gone. Each tile shows
     * the frame at its own start (see [tileSourceTimeUs]); until it arrives, the nearest tile already in memory
     * stands in, and the exact one fades in over it in 120 ms.
     */
    private fun DrawScope.drawStrip(
        item: ItemUi,
        row: LaneRow,
        left: Float,
        right: Float,
        pxPerUs: Float,
        thumbnails: TimelineThumbnails?,
        missingSources: Set<MediaRef>,
    ) {
        val top = row.itemTopPx
        val media = item.media
        if (media != null && media in missingSources) {
            drawItemBody(row, left, right, MemixColors.trackVideo)
            drawItemLabel(item.id, missingFileLabel, row, left, right, endX = Float.MAX_VALUE, MemixColors.text)
            return
        }
        clipPath(itemShape(left, top, right, top + row.itemHeightPx)) {
            drawRect(MemixColors.trackVideo, Offset(left, top), Size(right - left, row.itemHeightPx))
            if (media == null || thumbnails == null) return@clipPath
            val tilePx = tileSizePx.toFloat()
            val tileDurationUs = (tilePx / pxPerUs).toLong()
            val firstTile = max(0, floor(-left / tilePx).toInt())
            val lastTile = min(ceil((right - left) / tilePx).toInt() - 1, floor((size.width - left) / tilePx).toInt())
            for (tileIndex in firstTile..lastTile) {
                val timeUs = if (item.isPhoto) 0L else tileSourceTimeUs(item.trimInUs, item.trimOutUs, tileIndex, tileDurationUs)
                val tile = thumbnails.tileAt(media, timeUs) ?: continue
                val x = left + tileIndex * tilePx
                val alpha = if (tile.timeUs == timeUs) fadeInAlpha(tile) else 1f
                if (alpha < 1f) thumbnails.standInFor(media, timeUs)?.let { drawTile(it, x, top, alpha = 1f) }
                drawTile(tile, x, top, alpha)
            }
        }
    }

    private fun DrawScope.drawTile(tile: ThumbnailTile, x: Float, top: Float, alpha: Float) {
        drawImage(
            tile.image,
            dstOffset = IntOffset(x.roundToInt(), top.roundToInt()),
            dstSize = IntSize(tileSizePx, tileSizePx),
            alpha = alpha,
        )
    }

    private fun fadeInAlpha(tile: ThumbnailTile): Float =
        (tile.arrivedAt.elapsedNow().inWholeMilliseconds.toFloat() / MemixMotion.durationPress).coerceIn(0f, 1f)

    /**
     * A one-line label, `space-2` in from the item's visible left edge, so it stays readable while the item's start
     * is scrolled off screen. Past [endX] (the end of the video) it switches to `text`, readable on the wash.
     */
    private fun DrawScope.drawItemLabel(itemId: String, text: String, row: LaneRow, left: Float, right: Float, endX: Float, color: Color) {
        val labelLeft = max(left, 0f) + labelPaddingPx
        val availablePx = right - labelPaddingPx - labelLeft
        val layout = labelLayout(itemId, text, availablePx) ?: return
        val top = row.itemTopPx + (row.itemHeightPx - layout.size.height) / 2
        val topLeft = Offset(labelLeft, top)
        when {
            endX >= labelLeft + layout.size.width -> drawText(layout, color = color, topLeft = topLeft)
            endX <= labelLeft -> drawText(layout, color = MemixColors.text, topLeft = topLeft)
            else -> {
                clipRect(right = endX) { drawText(layout, color = color, topLeft = topLeft) }
                clipRect(left = endX) { drawText(layout, color = MemixColors.text, topLeft = topLeft) }
            }
        }
    }

    /**
     * The label measured once at its own width; only when it doesn't fit is it measured again with an ellipsis, at
     * the available width rounded down to `space-2`, so a label being cut by the screen edge is re-measured every
     * 8 dp rather than every frame. Null when not even `space-2` is free.
     */
    private fun labelLayout(itemId: String, text: String, availablePx: Float): TextLayoutResult? {
        if (availablePx < labelPaddingPx) return null
        if (itemLabels.size > MAX_CACHED_ITEM_LABELS) itemLabels.clear()
        val cached = itemLabels[itemId]?.takeIf { it.text == text }
            ?: CachedLabel(text, textMeasurer.measure(text, labelStyle, softWrap = false, maxLines = 1)).also { itemLabels[itemId] = it }
        if (cached.natural.size.width <= availablePx) return cached.natural
        val widthPx = (floor(availablePx / labelPaddingPx) * labelPaddingPx).toInt()
        if (cached.cutWidthPx != widthPx) {
            cached.cut = textMeasurer.measure(
                text,
                labelStyle,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = widthPx),
            )
            cached.cutWidthPx = widthPx
        }
        return cached.cut
    }

    private fun labelOf(item: ItemUi): String? = when (val label = item.label) {
        is ItemLabel.Written -> label.text
        ItemLabel.OriginalAudio -> originalAudioLabel
        ItemLabel.None -> null
    }

    // ---- Selection ----------------------------------------------------------------------------------------------

    /**
     * The selected item's outline (`stroke-selection`, a `stroke-hairline` gap outside the item), its two handles
     * outside the outline, and for a main clip the duration [badge]. [alpha] runs the 120 ms fade-in.
     */
    fun DrawScope.drawSelection(item: ItemUi, row: LaneRow, playheadUs: Long, pxPerUs: Float, badge: TextLayoutResult?, alpha: Float) {
        if (pxPerUs <= 0f || alpha <= 0f) return
        val centerPx = size.width / 2
        val left = xOf(item.startUs, playheadUs, centerPx, pxPerUs)
        val right = left + itemWidthPx(item, pxPerUs)
        val top = row.itemTopPx
        val bottom = top + row.itemHeightPx
        if (right < -handleWidthPx * 2 || left > size.width + handleWidthPx * 2) return
        if (badge != null) drawDurationBadge(badge, left, right, top, alpha)
        val outlineInset = outlineGapPx + outlineWidthPx / 2
        drawRoundRect(
            MemixColors.selection,
            Offset(left - outlineInset, top - outlineInset),
            Size(right - left + 2 * outlineInset, bottom - top + 2 * outlineInset),
            CornerRadius(cornerPx + outlineInset),
            style = outlineStroke,
            alpha = alpha,
        )
        val handleInset = outlineGapPx + outlineWidthPx
        drawHandle(left - handleInset - handleWidthPx, top - handleInset, bottom + handleInset, roundedOnLeft = true, alpha)
        drawHandle(right + handleInset, top - handleInset, bottom + handleInset, roundedOnLeft = false, alpha)
    }

    /** A white trim handle with its outer corners rounded and an `on-primary` grip line down its middle (P1-06 drags it). */
    private fun DrawScope.drawHandle(left: Float, top: Float, bottom: Float, roundedOnLeft: Boolean, alpha: Float) {
        val corner = CornerRadius(cornerPx)
        val shape = RoundRect(
            left,
            top,
            left + handleWidthPx,
            bottom,
            topLeftCornerRadius = if (roundedOnLeft) corner else CornerRadius.Zero,
            topRightCornerRadius = if (roundedOnLeft) CornerRadius.Zero else corner,
            bottomRightCornerRadius = if (roundedOnLeft) CornerRadius.Zero else corner,
            bottomLeftCornerRadius = if (roundedOnLeft) corner else CornerRadius.Zero,
        )
        itemPath.rewind()
        itemPath.addRoundRect(shape)
        drawPath(itemPath, MemixColors.selection, alpha = alpha)
        val gripX = left + handleWidthPx / 2
        val gripHalfHeight = (bottom - top) / 4
        val middle = (top + bottom) / 2
        drawLine(MemixColors.onPrimary, Offset(gripX, middle - gripHalfHeight), Offset(gripX, middle + gripHalfHeight), outlineWidthPx, alpha = alpha)
    }

    /** "00:03.20" on `scrim`, `space-1` inside the clip's top-left corner, only when the clip has room for it. */
    private fun DrawScope.drawDurationBadge(badge: TextLayoutResult, left: Float, right: Float, top: Float, alpha: Float) {
        val badgeWidth = badge.size.width + 2 * badgeInsetPx
        if (right - left < badgeWidth + badgeMinMarginPx) return
        val topLeft = Offset(left + badgeInsetPx, top + badgeInsetPx)
        drawRoundRect(MemixColors.scrim, topLeft, Size(badgeWidth, badge.size.height.toFloat()), CornerRadius(cornerPx), alpha = alpha)
        drawText(badge, color = MemixColors.text, topLeft = Offset(topLeft.x + badgeInsetPx, topLeft.y), alpha = alpha)
    }

    // ---- Playhead -----------------------------------------------------------------------------------------------

    /** The fixed playhead: a white line down the center to [bottomPx], with its round head at the top of the ruler. */
    fun DrawScope.drawPlayhead(bottomPx: Float) {
        val x = size.width / 2
        drawRect(MemixColors.selection, Offset(x - playheadWidthPx / 2, 0f), Size(playheadWidthPx, bottomPx))
        drawCircle(MemixColors.selection, radius = playheadHeadPx / 2, center = Offset(x, playheadHeadPx / 2))
    }

    private fun itemShape(left: Float, top: Float, right: Float, bottom: Float): Path {
        itemPath.rewind()
        itemPath.addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(cornerPx)))
        return itemPath
    }

    /** One item's label layouts: [natural] at its own width, [cut] with an ellipsis at [cutWidthPx]. */
    private class CachedLabel(val text: String, val natural: TextLayoutResult) {
        var cut: TextLayoutResult? = null
        var cutWidthPx = -1
    }
}

/** Well above what fits on one screen, so scrolling never re-measures, yet a long project's labels aren't all kept. */
private const val MAX_CACHED_RULER_LABELS = 64
private const val MAX_CACHED_ITEM_LABELS = 256

internal fun trackColorOf(kind: TrackKind): Color = when (kind) {
    TrackKind.MAIN_VIDEO, TrackKind.OVERLAY -> MemixColors.trackVideo
    TrackKind.TEXT -> MemixColors.trackText
    TrackKind.STICKER -> MemixColors.trackSticker
    TrackKind.MEME_SOUND -> MemixColors.trackMemeSound
    TrackKind.AUDIO -> MemixColors.trackAudio
    TrackKind.EFFECT -> MemixColors.trackEffect
}
