package app.memix.core.model.project

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * A speech bubble's shape, as pure functions in canvas pixels at scale 1, around the body's center (spec
 * docs/ux/specs/P2-02-meme-formats.md → Bubbles). The editor, the render hook and (P2-08) export draw the same numbers.
 */

/** A point in a bubble's own axes: pixels at scale 1, x to the right and y down from the body's center. */
data class BubblePoint(val x: Float, val y: Float)

/** One circle of a thought bubble's trail. */
data class BubbleDot(val center: BubblePoint, val radius: Float)

/**
 * The tail's triangle: [tip] and the two ends of its base on the body's boundary ([baseA], [baseB]); [boundary] is where the
 * line from the body's center to the tip leaves the body.
 */
data class BubbleTail(val tip: BubblePoint, val baseA: BubblePoint, val baseB: BubblePoint, val boundary: BubblePoint)

/** A bubble's whole drawn box in its own axes: [width] by [height], its center ([offsetX], [offsetY]) from the body's center. */
data class BubbleBox(val width: Float, val height: Float, val offsetX: Float, val offsetY: Float)

/**
 * The body at scale 1 on [canvas] around words that measure [blockWidthPx] by [blockHeightPx] (laid out at 60% of the caption
 * size and wrapped at 55% of S): the words plus 0.45 em at the sides and 0.35 em above and below, at least 0.25 S by 0.15 S.
 * An empty bubble is the minimum.
 */
fun bubbleBodySize(blockWidthPx: Float, blockHeightPx: Float, canvas: PixelSize): BoxSize {
    val s = shortSideOf(canvas)
    val em = s * CaptionCatalog.FONT_SIZE_OF_SHORT_SIDE * PhotoFormatCatalog.BUBBLE_TEXT_SCALE_OF_CAPTION
    return BoxSize(
        max(blockWidthPx + 2f * PhotoFormatCatalog.BUBBLE_PADDING_X_EM * em, PhotoFormatCatalog.BUBBLE_MIN_WIDTH_OF_SHORT_SIDE * s),
        max(blockHeightPx + 2f * PhotoFormatCatalog.BUBBLE_PADDING_Y_EM * em, PhotoFormatCatalog.BUBBLE_MIN_HEIGHT_OF_SHORT_SIDE * s),
    )
}

/** The default tail's tip for a bubble centered at [centerY] (a fraction of the canvas): toward the canvas center. */
fun defaultTail(centerY: Float): BubblePoint =
    BubblePoint(PhotoFormatCatalog.TAIL_DEFAULT_X, if (centerY <= 0.5f) PhotoFormatCatalog.TAIL_DEFAULT_Y_TOP_HALF else PhotoFormatCatalog.TAIL_DEFAULT_Y_BOTTOM_HALF)

/** The tip in the bubble's own axes: [BubbleLayer.tailX] body widths and [BubbleLayer.tailY] body heights from the center. */
fun BubbleLayer.tipIn(body: BoxSize): BubblePoint = BubblePoint(tailX * body.width, tailY * body.height)

/**
 * The tail of a bubble of [shape] with a [body] and its tip [tip], or null when the tip is inside the body (nothing to
 * point with). The base is [PhotoFormatCatalog.TAIL_BASE_OF_SHORTER_SIDE] of the body's shorter side wide, centered on the
 * boundary point of the line from the center to the tip. A speech body's rounded corners are ignored for that point: the
 * drawn tail reaches into the body, so no gap shows at a corner.
 */
fun bubbleTail(shape: BubbleShape, body: BoxSize, tip: BubblePoint): BubbleTail? {
    val distance = hypot(tip.x, tip.y)
    if (distance <= 0f) return null
    val dx = tip.x / distance
    val dy = tip.y / distance
    val halfWidth = body.width / 2f
    val halfHeight = body.height / 2f
    val reach = when (shape) {
        BubbleShape.SPEECH -> min(
            if (dx == 0f) Float.MAX_VALUE else halfWidth / kotlin.math.abs(dx),
            if (dy == 0f) Float.MAX_VALUE else halfHeight / kotlin.math.abs(dy),
        )
        BubbleShape.ROUND, BubbleShape.THOUGHT -> 1f / sqrt((dx / halfWidth) * (dx / halfWidth) + (dy / halfHeight) * (dy / halfHeight))
    }
    if (distance <= reach) return null
    val boundary = BubblePoint(dx * reach, dy * reach)
    val halfBase = PhotoFormatCatalog.TAIL_BASE_OF_SHORTER_SIDE * min(body.width, body.height) / 2f
    return BubbleTail(
        tip = tip,
        baseA = BubblePoint(boundary.x - dy * halfBase, boundary.y + dx * halfBase),
        baseB = BubblePoint(boundary.x + dy * halfBase, boundary.y - dx * halfBase),
        boundary = boundary,
    )
}

/**
 * A thought bubble's trail: circles (radii [PhotoFormatCatalog.THOUGHT_DOT_RADII_OF_SHORT_SIDE] of S) spaced evenly from the
 * body's boundary to the tip, the smallest on the tip. The chain scales down with the tail's reach (the boundary to the tip, over
 * [PhotoFormatCatalog.THOUGHT_FULL_REACH_OF_SHORT_SIDE] of S, never below [PhotoFormatCatalog.THOUGHT_MIN_SCALE] of the radii);
 * a reach too short for three dots at that floor draws two, then one (spec review P2-02, 5 Nov). Empty when the tip is inside the body.
 */
fun thoughtDots(body: BoxSize, tip: BubblePoint, canvas: PixelSize): List<BubbleDot> {
    val tail = bubbleTail(BubbleShape.THOUGHT, body, tip) ?: return emptyList()
    val s = shortSideOf(canvas)
    val reach = hypot(tip.x - tail.boundary.x, tip.y - tail.boundary.y)
    val share = reach / (PhotoFormatCatalog.THOUGHT_FULL_REACH_OF_SHORT_SIDE * s)
    val count = when {
        share >= PhotoFormatCatalog.THOUGHT_MIN_SCALE -> PhotoFormatCatalog.THOUGHT_DOT_RADII_OF_SHORT_SIDE.size
        share >= PhotoFormatCatalog.THOUGHT_TWO_DOTS_FROM -> 2
        else -> 1
    }
    val scale = share.coerceIn(PhotoFormatCatalog.THOUGHT_MIN_SCALE, 1f)
    val radii = PhotoFormatCatalog.THOUGHT_DOT_RADII_OF_SHORT_SIDE.take(count)
    return radii.mapIndexed { index, radius ->
        val along = (index + 1).toFloat() / radii.size
        BubbleDot(
            BubblePoint(tail.boundary.x + (tip.x - tail.boundary.x) * along, tail.boundary.y + (tip.y - tail.boundary.y) * along),
            radius * s * scale,
        )
    }
}

/**
 * The default tip of a thought bubble (spec review P2-02: a reach of [PhotoFormatCatalog.THOUGHT_DEFAULT_REACH_OF_SHORT_SIDE] of S
 * from the body's edge), toward the canvas center like the other shapes' default, clamped so the tip stays on the canvas
 * (the bubble is centered at ([centerX], [centerY]) in canvas fractions) and within the tail limit. In body widths and heights.
 */
fun defaultThoughtTip(body: BoxSize, centerX: Float, centerY: Float, canvas: PixelSize): BubblePoint {
    val toward = defaultTail(centerY)
    val dirX = toward.x * body.width
    val dirY = toward.y * body.height
    val length = hypot(dirX, dirY)
    val halfWidth = body.width / 2f
    val halfHeight = body.height / 2f
    val edge = 1f / sqrt((dirX / length / halfWidth) * (dirX / length / halfWidth) + (dirY / length / halfHeight) * (dirY / length / halfHeight))
    var distance = edge + PhotoFormatCatalog.THOUGHT_DEFAULT_REACH_OF_SHORT_SIDE * shortSideOf(canvas)
    // Keep the tip on the canvas: shorten the reach until it is.
    val originX = centerX * canvas.widthPx
    val originY = centerY * canvas.heightPx
    val ux = dirX / length
    val uy = dirY / length
    while (distance > edge && (originX + ux * distance !in 0f..canvas.widthPx.toFloat() || originY + uy * distance !in 0f..canvas.heightPx.toFloat())) distance -= canvas.widthPx / 100f
    return clampedTail(ux * distance / body.width, uy * distance / body.height)
}

/** The whole drawn box of a bubble: the body, its tail or trail, and half the stroke. */
fun bubbleBox(shape: BubbleShape, body: BoxSize, tip: BubblePoint, canvas: PixelSize): BubbleBox {
    var minX = -body.width / 2f
    var maxX = body.width / 2f
    var minY = -body.height / 2f
    var maxY = body.height / 2f
    when (shape) {
        BubbleShape.THOUGHT -> thoughtDots(body, tip, canvas).forEach { dot ->
            minX = min(minX, dot.center.x - dot.radius)
            maxX = max(maxX, dot.center.x + dot.radius)
            minY = min(minY, dot.center.y - dot.radius)
            maxY = max(maxY, dot.center.y + dot.radius)
        }
        BubbleShape.SPEECH, BubbleShape.ROUND -> if (bubbleTail(shape, body, tip) != null) {
            minX = min(minX, tip.x)
            maxX = max(maxX, tip.x)
            minY = min(minY, tip.y)
            maxY = max(maxY, tip.y)
        }
    }
    val stroke = PhotoFormatCatalog.BUBBLE_STROKE_OF_SHORT_SIDE * shortSideOf(canvas) / 2f
    return BubbleBox(maxX - minX + 2f * stroke, maxY - minY + 2f * stroke, (minX + maxX) / 2f, (minY + maxY) / 2f)
}

/** [tailX] and [tailY] kept within [PhotoFormatCatalog.TAIL_TIP_LIMIT_OF_BODY] body widths and heights of the center. */
fun clampedTail(tailX: Float, tailY: Float): BubblePoint {
    val limit = PhotoFormatCatalog.TAIL_TIP_LIMIT_OF_BODY
    return BubblePoint(tailX.coerceIn(-limit, limit), tailY.coerceIn(-limit, limit))
}
