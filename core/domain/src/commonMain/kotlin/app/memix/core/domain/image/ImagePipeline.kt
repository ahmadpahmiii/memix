package app.memix.core.domain.image

import app.memix.core.model.project.FilterRef
import app.memix.core.model.project.ImageAdjust
import app.memix.core.model.project.ImageEdit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The image tools' color pipeline (spec docs/ux/specs/P2-04-image-tools.md → How it draws), the one definition every output
 * shares: the editor, the render hook and the export run these same rules on the picture after its geometry. Fixed order:
 * brightness, contrast, saturation, warmth, then the filter at its intensity, then deep-fry, then sharpen, then vignette.
 * Colors are sRGB gamma space on 8-bit values, clamped after each step. Pure Kotlin on ARGB [IntArray]s, so it runs on any
 * platform and in the desktop harness; the constants are fixed rules saved with every draft's look ([ImagePipelineRules]).
 */
object ImagePipeline {
    /**
     * Applies [edit]'s color tools (adjustments, [lut] for its filter, deep-fry) to [pixels], [width] x [height] ARGB, in place.
     * [lut] is the table of [ImageEdit.filter] (null leaves a filter out). [checkCancelled] runs once per row. Alpha is kept.
     */
    fun process(pixels: IntArray, width: Int, height: Int, edit: ImageEdit, lut: Lut?, checkCancelled: () -> Unit = {}) {
        if (!edit.hasColorEdits || width <= 0 || height <= 0) return
        val filter = edit.filter?.takeIf { lut != null && it.intensity > 0f }
        val shortSide = min(width, height).toFloat()
        val fry = edit.deepFry
        val pointwise = Pointwise(edit.adjust, filter, lut, fry, shortSide)
        val vignette = edit.adjust.vignette
        val sharpenAmount = edit.adjust.sharpen * ImagePipelineRules.SHARPEN_AMOUNT + fry * ImagePipelineRules.DEEP_FRY_SHARPEN
        val sharpens = sharpenAmount > 0f
        for (y in 0 until height) {
            checkCancelled()
            for (x in 0 until width) {
                val i = y * width + x
                pixels[i] = pointwise.apply(pixels[i], x, y)
            }
        }
        if (sharpens) sharpen(pixels, width, height, sharpenAmount, shortSide, checkCancelled)
        if (vignette > 0f) vignette(pixels, width, height, vignette)
    }

    // Steps 2 to 4 for one pixel, on floats 0 to 1, clamped after each step.
    private class Pointwise(
        private val adjust: ImageAdjust,
        private val filter: FilterRef?,
        private val lut: Lut?,
        private val fry: Float,
        private val shortSide: Float,
    ) {
        private val rgb = FloatArray(3)
        private val lutOut = FloatArray(3)
        private val grainCell = ImagePipelineRules.GRAIN_CELL_OF_SHORT_SIDE * shortSide

        fun apply(argb: Int, x: Int, y: Int): Int {
            rgb[0] = ((argb shr 16) and 0xFF) / 255f
            rgb[1] = ((argb shr 8) and 0xFF) / 255f
            rgb[2] = (argb and 0xFF) / 255f
            adjustments()
            if (filter != null && lut != null) {
                lut.sample(rgb[0], rgb[1], rgb[2], lutOut)
                val t = filter.intensity.coerceIn(0f, 1f)
                for (c in 0..2) rgb[c] = rgb[c] + (lutOut[c] - rgb[c]) * t
            }
            if (fry > 0f) deepFry(x, y)
            return (argb and ALPHA_MASK) or (to8(rgb[0]) shl 16) or (to8(rgb[1]) shl 8) or to8(rgb[2])
        }

        private fun adjustments() {
            if (adjust.brightness != 0f) for (c in 0..2) rgb[c] = clamp(rgb[c] + adjust.brightness * ImagePipelineRules.BRIGHTNESS_SPAN)
            if (adjust.contrast != 0f) for (c in 0..2) rgb[c] = clamp((rgb[c] - 0.5f) * (1f + adjust.contrast) + 0.5f)
            if (adjust.saturation != 0f) saturate(1f + adjust.saturation)
            if (adjust.warmth != 0f) {
                rgb[0] = clamp(rgb[0] * (1f + ImagePipelineRules.WARMTH_SPAN * adjust.warmth))
                rgb[2] = clamp(rgb[2] * (1f - ImagePipelineRules.WARMTH_SPAN * adjust.warmth))
            }
        }

        private fun saturate(k: Float) {
            val luma = LUMA_R * rgb[0] + LUMA_G * rgb[1] + LUMA_B * rgb[2]
            for (c in 0..2) rgb[c] = clamp(luma + (rgb[c] - luma) * k)
        }

        // One recipe scaled by the amount: saturation, contrast, a warm-red tint, then grain (the hard sharpen is step 5).
        private fun deepFry(x: Int, y: Int) {
            saturate(1f + ImagePipelineRules.DEEP_FRY_SATURATION * fry)
            for (c in 0..2) rgb[c] = clamp((rgb[c] - 0.5f) * (1f + ImagePipelineRules.DEEP_FRY_CONTRAST * fry) + 0.5f)
            rgb[0] = clamp(rgb[0] * (1f + ImagePipelineRules.DEEP_FRY_TINT * fry))
            rgb[2] = clamp(rgb[2] * (1f - ImagePipelineRules.DEEP_FRY_TINT * fry))
            val grain = grainAt(x, y) * ImagePipelineRules.DEEP_FRY_GRAIN * fry
            for (c in 0..2) rgb[c] = clamp(rgb[c] + grain)
        }

        // The grain's cells are a share of the shorter side and each cell's value a fixed hash, so every size lays out the same field.
        // A pixel takes the field's average over its own square (cells weighted by overlap), as if the finer picture were scaled down:
        // sampling one point instead makes a cell about two pixels wide land differently at 1080 and at 2160.
        private fun grainAt(x: Int, y: Int): Float {
            val left = x / grainCell
            val top = y / grainCell
            val span = 1f / grainCell
            val firstX = floor(left).toInt()
            val lastX = ceil(left + span).toInt() - 1
            val firstY = floor(top).toInt()
            val lastY = ceil(top + span).toInt() - 1
            var sum = 0f
            for (cy in firstY..lastY) {
                val weightY = overlap(cy, top, span)
                for (cx in firstX..lastX) sum += grainOf(cx, cy) * weightY * overlap(cx, left, span)
            }
            return sum
        }

        // The share of the pixel's extent [start, start + span) (in cells) that lies in cell [cell].
        private fun overlap(cell: Int, start: Float, span: Float): Float = (min(cell + 1f, start + span) - max(cell.toFloat(), start)).coerceAtLeast(0f) / span
    }

    /** A fixed pseudo-random value in -1 to 1 for grain cell ([cellX], [cellY]): the same on every platform and size. */
    fun grainOf(cellX: Int, cellY: Int): Float {
        var h = cellX * HASH_X xor cellY * HASH_Y
        h = (h xor (h ushr 13)) * HASH_MIX
        h = h xor (h ushr 16)
        return ((h and 0xFFFF) / 32767.5f) - 1f
    }

    // Unsharp mask: out = c + amount * (c - blur(c)), the blur a box blur of the radius, run twice per axis for a smoother kernel.
    private fun sharpen(pixels: IntArray, width: Int, height: Int, amount: Float, shortSide: Float, checkCancelled: () -> Unit) {
        // Not rounded to whole pixels: a rounded radius is a different blur at each size, so the look would change with the output size.
        val radius = max(MIN_SHARPEN_RADIUS_PX, ImagePipelineRules.SHARPEN_RADIUS_OF_SHORT_SIDE * shortSide)
        val blurred = IntArray(pixels.size)
        pixels.copyInto(blurred)
        repeat(2) {
            boxBlurRows(blurred, width, height, radius, checkCancelled)
            boxBlurColumns(blurred, width, height, radius, checkCancelled)
        }
        for (i in pixels.indices) {
            val p = pixels[i]
            val b = blurred[i]
            pixels[i] = (p and ALPHA_MASK) or
                (sharpened((p shr 16) and 0xFF, (b shr 16) and 0xFF, amount) shl 16) or
                (sharpened((p shr 8) and 0xFF, (b shr 8) and 0xFF, amount) shl 8) or
                sharpened(p and 0xFF, b and 0xFF, amount)
        }
    }

    private fun sharpened(value: Int, blur: Int, amount: Float): Int = (value + amount * (value - blur)).roundToInt().coerceIn(0, 255)

    private fun boxBlurRows(pixels: IntArray, width: Int, height: Int, radius: Float, checkCancelled: () -> Unit) {
        val line = IntArray(width)
        for (y in 0 until height) {
            checkCancelled()
            for (x in 0 until width) line[x] = pixels[y * width + x]
            for (x in 0 until width) pixels[y * width + x] = boxAt(line, x, radius, width)
        }
    }

    private fun boxBlurColumns(pixels: IntArray, width: Int, height: Int, radius: Float, checkCancelled: () -> Unit) {
        val line = IntArray(height)
        for (x in 0 until width) {
            if (x % CANCEL_EVERY_COLUMNS == 0) checkCancelled()
            for (y in 0 until height) line[y] = pixels[y * width + x]
            for (y in 0 until height) pixels[y * width + x] = boxAt(line, y, radius, height)
        }
    }

    // The mean of [line] over [at] - [radius] to [at] + [radius], each channel: whole pixels count fully, the pixel just past a
    // fractional radius by its fraction, the ends repeating their edge pixel.
    private fun boxAt(line: IntArray, at: Int, radius: Float, length: Int): Int {
        val whole = radius.toInt()
        val edgeWeight = radius - whole
        var r = 0f
        var g = 0f
        var b = 0f
        for (k in -whole - 1..whole + 1) {
            val weight = if (k < -whole || k > whole) edgeWeight else 1f
            if (weight == 0f) continue
            val p = line[(at + k).coerceIn(0, length - 1)]
            r += weight * ((p shr 16) and 0xFF)
            g += weight * ((p shr 8) and 0xFF)
            b += weight * (p and 0xFF)
        }
        val total = 2f * radius + 1f
        return (line[at] and ALPHA_MASK) or ((r / total).roundToInt() shl 16) or ((g / total).roundToInt() shl 8) or (b / total).roundToInt()
    }

    // Multiplies by 1 - v * smoothstep(0.35, 1, r), r the distance from the center as a share of the half-diagonal.
    private fun vignette(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val centerX = (width - 1) / 2f
        val centerY = (height - 1) / 2f
        val halfDiagonal = sqrt(centerX * centerX + centerY * centerY).coerceAtLeast(1f)
        for (y in 0 until height) {
            val dy = y - centerY
            for (x in 0 until width) {
                val dx = x - centerX
                val r = sqrt(dx * dx + dy * dy) / halfDiagonal
                val t = ((r - ImagePipelineRules.VIGNETTE_START) / (1f - ImagePipelineRules.VIGNETTE_START)).coerceIn(0f, 1f)
                val factor = 1f - strength * (t * t * (3f - 2f * t))
                val p = pixels[y * width + x]
                pixels[y * width + x] = (p and ALPHA_MASK) or
                    (scaled((p shr 16) and 0xFF, factor) shl 16) or (scaled((p shr 8) and 0xFF, factor) shl 8) or scaled(p and 0xFF, factor)
            }
        }
    }

    private fun scaled(value: Int, factor: Float): Int = (value * factor).roundToInt().coerceIn(0, 255)

    private fun clamp(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

    private fun to8(v: Float): Int = (clamp(v) * 255f + 0.5f).toInt()

    private const val ALPHA_MASK = 0xFF000000.toInt()
    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f
    private const val HASH_X = 73856093
    private const val HASH_Y = 19349663
    private const val HASH_MIX = 0x45D9F3B
    private const val CANCEL_EVERY_COLUMNS = 64
    private const val MIN_SHARPEN_RADIUS_PX = 0.5f
}

/** The pipeline's fixed numbers (spec P2-04 → How it draws; design/tokens.json → photo.imageTools). Changing one after the closed test needs a schema bump. */
object ImagePipelineRules {
    /** Brightness +1 adds this to each channel. */
    const val BRIGHTNESS_SPAN = 0.5f

    /** Warmth +1 scales red up and blue down by this share. */
    const val WARMTH_SPAN = 0.12f

    const val DEEP_FRY_SATURATION = 1.2f
    const val DEEP_FRY_CONTRAST = 0.8f
    const val DEEP_FRY_TINT = 0.10f
    const val DEEP_FRY_GRAIN = 0.12f

    /** Deep-fry's hard sharpen: an unsharp mask of this amount at deep-fry 1 (strength 2a). */
    const val DEEP_FRY_SHARPEN = 2f

    /** Grain cells are this share of the shorter side wide. */
    const val GRAIN_CELL_OF_SHORT_SIDE = 0.002f

    const val SHARPEN_RADIUS_OF_SHORT_SIDE = 0.0015f

    /** Sharpen 1 is an unsharp mask of this amount. */
    const val SHARPEN_AMOUNT = 1.5f

    const val VIGNETTE_START = 0.35f
}
