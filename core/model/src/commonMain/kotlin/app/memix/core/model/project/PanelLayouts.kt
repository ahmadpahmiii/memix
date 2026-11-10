package app.memix.core.model.project

import kotlin.math.abs

/*
 * The Panels format's layouts and geometry (spec docs/ux/specs/P2-02-meme-formats.md → Panels). A layout is a tree of splits
 * written as data: the cells are cut into groups along the outer axis, and each group is cut into cells along the other
 * axis. Every gap between neighbours is a divider whose position is a share (0 to 1) of its split's extent; the saved
 * [PanelsFormat.dividers] list them in a fixed order: the outer dividers first, then each group's own, in reading order.
 */

/** The direction cells are laid next to each other: [COLUMNS] side by side (dividers are vertical lines), [ROWS] stacked. */
enum class PanelAxis { COLUMNS, ROWS }

/** The three families of layouts a cell count offers. */
enum class PanelFamily { GRID, COLUMNS, ROWS }

/**
 * One layout: [outerAxis] cuts the canvas into [groups].size groups, group k holding `groups[k]` cells laid along the other
 * axis. `cols-3` is three groups of one cell along [PanelAxis.COLUMNS]; `grid-3` is two rows (one cell, then two).
 */
class PanelLayoutSpec(val id: String, val family: PanelFamily, val outerAxis: PanelAxis, val groups: List<Int>) {
    val cellCount: Int = groups.sum()

    /** The axis cells inside a group are laid along. */
    val innerAxis: PanelAxis get() = if (outerAxis == PanelAxis.COLUMNS) PanelAxis.ROWS else PanelAxis.COLUMNS

    val outerDividerCount: Int get() = groups.size - 1
    val dividerCount: Int = (groups.size - 1) + groups.sumOf { it - 1 }

    /** Every divider at its even position: the outer ones at k/groups, each group's at j/cells. */
    fun evenDividers(): List<Float> = buildList {
        for (k in 1 until groups.size) add(k.toFloat() / groups.size)
        for (cells in groups) for (j in 1 until cells) add(j.toFloat() / cells)
    }
}

object PanelLayouts {
    /** The layout ids of each cell count, in the Layout panel's order (the spec's table; 2 has no grid). */
    val all: List<PanelLayoutSpec> = buildList {
        for (count in PhotoFormatCatalog.PANELS_MIN_CELLS..PhotoFormatCatalog.PANELS_MAX_CELLS) {
            gridGroupsOf(count)?.let { add(PanelLayoutSpec("grid-$count", PanelFamily.GRID, PanelAxis.ROWS, it)) }
            add(PanelLayoutSpec("cols-$count", PanelFamily.COLUMNS, PanelAxis.COLUMNS, List(count) { 1 }))
            add(PanelLayoutSpec("rows-$count", PanelFamily.ROWS, PanelAxis.ROWS, List(count) { 1 }))
        }
    }

    /** What a new Panels format starts as: two photos side by side, the first holding the main photo. */
    val default: PanelLayoutSpec = of("cols-2")!!

    fun of(id: String): PanelLayoutSpec? = all.firstOrNull { it.id == id }

    /** The layouts [count] cells offer. */
    fun forCount(count: Int): List<PanelLayoutSpec> = all.filter { it.cellCount == count }

    /** The layout of [family] for [count] cells, else side by side (a count with no grid: 2). */
    fun of(count: Int, family: PanelFamily): PanelLayoutSpec =
        forCount(count).firstOrNull { it.family == family } ?: forCount(count).first { it.family == PanelFamily.COLUMNS }

    // Grid: 3 one wide on top and two below; 4 is 2 x 2; 5 two on top and three below; 6 two rows of 3.
    private fun gridGroupsOf(count: Int): List<Int>? = when (count) {
        3 -> listOf(1, 2)
        4 -> listOf(2, 2)
        5 -> listOf(2, 3)
        6 -> listOf(3, 3)
        else -> null
    }
}

/** The layout of this format, or null for an id this build doesn't know. */
fun PanelsFormat.spec(): PanelLayoutSpec? = PanelLayouts.of(layout)

/** The divider positions as saved when there are as many as the layout needs, else every divider even. */
fun PanelsFormat.dividerPositions(): List<Float> {
    val spec = spec() ?: return dividers
    return if (dividers.size == spec.dividerCount) dividers else spec.evenDividers()
}

/**
 * One divider as the editor and the render see it, in canvas pixels. [gutter] is its strip (the gutter's width by the length
 * of the split it cuts); [vertical] when the strip stands upright (it separates side-by-side cells and moves along x).
 * [position], [min], [max] and [even] are shares of the split's extent, which runs from [extentStart] for [extentLength] pixels
 * along the axis the divider moves (the split's area widened by half a gutter on each side, so equal shares give equal cells).
 */
data class PanelDivider(
    val index: Int,
    val vertical: Boolean,
    val gutter: FormatRect,
    val position: Float,
    val min: Float,
    val max: Float,
    val even: Float,
    val extentStart: Float,
    val extentLength: Float,
)

/** Where every cell and divider of a [PanelsFormat] is on a canvas, in reading order for cells, in the saved order for dividers. */
class PanelsGeometry(val cells: List<FormatRect>, val dividers: List<PanelDivider>)

/**
 * The cells and dividers of this format on [canvas]: the area inside the margin, cut by the layout's dividers, a gutter of
 * [PhotoFormatCatalog.PANELS_GUTTER_OF_SHORT_SIDE] between neighbours. Null for an unknown layout id.
 */
fun PanelsFormat.geometry(canvas: PixelSize): PanelsGeometry? {
    val spec = spec() ?: return null
    val positions = dividerPositions()
    val s = shortSideOf(canvas)
    val gutter = PhotoFormatCatalog.PANELS_GUTTER_OF_SHORT_SIDE * s
    val area = FormatRect(0f, 0f, canvas.widthPx.toFloat(), canvas.heightPx.toFloat()).inset(PhotoFormatCatalog.PANELS_MARGIN_OF_SHORT_SIDE * s)
    val outerIsColumns = spec.outerAxis == PanelAxis.COLUMNS
    // Dividers are shares of the area widened by half a gutter on each side, so a gutter sits centered on its divider and
    // every cell loses half a gutter on each side: even dividers give equal cells, edge cells included.
    val half = gutter / 2f
    val outerStart = (if (outerIsColumns) area.left else area.top) - half
    val outerLength = (if (outerIsColumns) area.width else area.height) + gutter
    val crossStart = (if (outerIsColumns) area.top else area.left) - half
    val crossLength = (if (outerIsColumns) area.height else area.width) + gutter
    val groupCount = spec.groups.size

    fun edge(k: Int): Float = when (k) {
        0 -> 0f
        groupCount -> 1f
        else -> positions[k - 1]
    }

    fun rectOf(slab: ClosedFloatingPointRange<Float>, cross: ClosedFloatingPointRange<Float>): FormatRect =
        if (outerIsColumns) {
            FormatRect(slab.start, cross.start, slab.endInclusive - slab.start, cross.endInclusive - cross.start)
        } else {
            FormatRect(cross.start, slab.start, cross.endInclusive - cross.start, slab.endInclusive - slab.start)
        }

    val cells = ArrayList<FormatRect>()
    val dividers = ArrayList<PanelDivider>()
    // The outer dividers: a gutter across the whole area, margin to margin.
    for (k in 1 until groupCount) {
        val center = outerStart + positions[k - 1] * outerLength
        val strip = rectOf((center - half)..(center + half), (crossStart + half)..(crossStart + crossLength - half))
        dividers += PanelDivider(
            index = k - 1, vertical = outerIsColumns, gutter = strip,
            position = positions[k - 1],
            min = (if (k == 1) 0f else positions[k - 2]) + PhotoFormatCatalog.DIVIDER_MIN_SIDE_OF_EXTENT,
            max = (if (k == groupCount - 1) 1f else positions[k]) - PhotoFormatCatalog.DIVIDER_MIN_SIDE_OF_EXTENT,
            even = k.toFloat() / groupCount, extentStart = outerStart, extentLength = outerLength,
        )
    }
    // Each group's own dividers, across its slab (the group's strip between its neighbours' gutters).
    var dividerIndex = spec.outerDividerCount
    for (k in 0 until groupCount) {
        val cellsInGroup = spec.groups[k]
        val slab = (outerStart + edge(k) * outerLength + half)..(outerStart + edge(k + 1) * outerLength - half)
        val own = (0 until cellsInGroup - 1).map { positions[dividerIndex + it] }
        fun crossEdge(j: Int): Float = when (j) {
            0 -> 0f
            cellsInGroup -> 1f
            else -> own[j - 1]
        }
        for (j in 0 until cellsInGroup) {
            val from = crossStart + crossEdge(j) * crossLength + half
            val to = crossStart + crossEdge(j + 1) * crossLength - half
            cells += rectOf(slab, from..to)
        }
        for (j in 1 until cellsInGroup) {
            val center = crossStart + own[j - 1] * crossLength
            val strip = rectOf(slab, (center - half)..(center + half))
            dividers += PanelDivider(
                index = dividerIndex + j - 1, vertical = !outerIsColumns, gutter = strip,
                position = own[j - 1],
                min = (if (j == 1) 0f else own[j - 2]) + PhotoFormatCatalog.DIVIDER_MIN_SIDE_OF_EXTENT,
                max = (if (j == cellsInGroup - 1) 1f else own[j]) - PhotoFormatCatalog.DIVIDER_MIN_SIDE_OF_EXTENT,
                even = j.toFloat() / cellsInGroup, extentStart = crossStart, extentLength = crossLength,
            )
        }
        dividerIndex += cellsInGroup - 1
    }
    return PanelsGeometry(cells, dividers)
}

/** Divider [index] at [position] (a share of its extent), kept within its limits; no change for an unknown divider. */
fun PanelsFormat.withDivider(canvas: PixelSize, index: Int, position: Float): PanelsFormat {
    val divider = geometry(canvas)?.dividers?.firstOrNull { it.index == index } ?: return this
    val moved = dividerPositions().toMutableList().apply { this[index] = position.coerceIn(divider.min, divider.max) }
    return copy(dividers = moved)
}

/** Every divider at its even position. */
fun PanelsFormat.withEvenDividers(): PanelsFormat = spec()?.let { copy(dividers = it.evenDividers()) } ?: this

/** A new Panels format in [spec], dividers even, with [cells] (padded with empty cells or cut to the layout's count). */
fun panelsFormatOf(spec: PanelLayoutSpec, cells: List<PanelCell>): PanelsFormat =
    PanelsFormat(spec.id, spec.evenDividers(), List(spec.cellCount) { cells.getOrNull(it) ?: PanelCell() })

/** Every photo in a cell, with its number (0 is cell 1). */
fun PanelsFormat.photos(): List<IndexedValue<MediaRef>> = cells.withIndex().mapNotNull { (index, cell) -> cell.source?.let { IndexedValue(index, it) } }

// ---- A cell's photo -------------------------------------------------------------------------------------------------

/**
 * The picture's rectangle for [this] cell in [cellRect]: scaled to cover the cell times the zoom, with the point ([centerX],
 * [centerY]) of it at the cell's center. Clip it to [cellRect] when drawing. [photo] is the picture's size as shown.
 */
fun PanelCell.pictureRect(cellRect: FormatRect, photo: PixelSize): FormatRect {
    if (photo.widthPx <= 0 || photo.heightPx <= 0) return cellRect
    val scale = coverScale(cellRect, photo) * zoom
    val width = photo.widthPx * scale
    val height = photo.heightPx * scale
    return FormatRect(cellRect.centerX - centerX * width, cellRect.centerY - centerY * height, width, height)
}

/** [this] cell with its zoom in range and its center where the cell stays covered: no gap ever. */
fun PanelCell.clampedToCover(cellRect: FormatRect, photo: PixelSize): PanelCell {
    val zoomed = zoom.coerceIn(PhotoFormatCatalog.CELL_ZOOM_RANGE)
    if (photo.widthPx <= 0 || photo.heightPx <= 0) return copy(zoom = zoomed)
    val scale = coverScale(cellRect, photo) * zoomed
    val halfAcross = cellRect.width / (2f * photo.widthPx * scale)
    val halfDown = cellRect.height / (2f * photo.heightPx * scale)
    return PanelCell(
        source = source,
        zoom = zoomed,
        centerX = centerX.coerceIn(halfAcross, 1f - halfAcross),
        centerY = centerY.coerceIn(halfDown, 1f - halfDown),
    )
}

private fun coverScale(cellRect: FormatRect, photo: PixelSize): Float =
    maxOf(cellRect.width / photo.widthPx, cellRect.height / photo.heightPx)

/** Whether two cells are the same within a hair (a drag that ended where it began commits nothing). */
fun PanelCell.isCloseTo(other: PanelCell): Boolean =
    source == other.source && abs(zoom - other.zoom) < CLOSE && abs(centerX - other.centerX) < CLOSE && abs(centerY - other.centerY) < CLOSE

private const val CLOSE = 0.0001f
