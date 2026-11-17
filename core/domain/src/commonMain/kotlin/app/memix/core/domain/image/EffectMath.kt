package app.memix.core.domain.image

import app.memix.core.model.project.EffectCatalog
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The 15 effects of Motion, Light and Color (P4-06a; spec docs/ux/specs/P4-06-video-effects.md → The list) as numbers and as a CPU twin.
 * The shaders (`EffectsProgram` in `:engine:video`) take every parameter from here ([motionPose], [colorTransform], the constants), so the
 * GPU and this twin are written from one source; the twin is what the reference renders in docs/qa/references/P4-06-effects/ and the sheet's
 * tiles are made with. Each effect is one pass over 8-bit pixels (the shader's RGBA8 target rounds the same way), except Deep-fry (a
 * pointwise pass, then a sharpen) and Glow (a thresholded quarter-size copy, two blurs on it, then the screen-add). Parameters are shares
 * of the output's shorter side, so a 720p preview and a 1080p export look the same. Strength [strength] is intensity times the envelope
 * (`effectStrength`); 0 is exactly no effect. Pure Kotlin on ARGB [IntArray]s; coordinates are pixels, y down, a pixel's center at +0.5.
 */
object EffectMath {
    // Motion (shares of the shorter side, degrees, scale added at strength 1).
    const val ZOOM_PUNCH_SCALE = 0.5f
    const val SLOW_PUSH_SCALE = 0.3f
    const val SHAKE_OFFSET = 0.025f
    const val SHAKE_ROTATION_DEGREES = 1.2f
    const val WOBBLE_ROTATION_DEGREES = 6f
    const val BOUNCE_OFFSET = 0.06f

    // Light.
    const val FLASH_WHITE = 0.9f
    const val PULSE_SWELL = 0.5f
    const val PULSE_HZ = 2f
    const val GLOW_THRESHOLD_LUMA = 0.6f
    const val GLOW_BLUR_SIGMA = 0.025f
    const val GLOW_AMOUNT = 0.8f

    /** The glow's thresholded copy and its blurs are made at 1/4 of the frame's size each way. */
    const val GLOW_DOWNSCALE = 4
    const val SPOTLIGHT_RADIUS_FAR = 0.9f
    const val SPOTLIGHT_RADIUS_NEAR = 0.35f
    const val SPOTLIGHT_DARKEN = 0.85f
    const val SPOTLIGHT_EDGE = 0.2f
    const val SUN_RADIUS = 0.45f
    const val SUN_AMOUNT = 0.45f
    const val SUN_CYCLES_PER_SECOND = 0.15f
    const val SUN_RGB = 0xFF9A3C

    // Color.
    const val MONO_CONTRAST = 0.1f
    const val DUOTONE_SHADOW = 0x241B4B
    const val DUOTONE_HIGHLIGHT = 0xF6B73C
    const val POSTER_LEVELS_FAR = 16f
    const val POSTER_LEVELS_SPAN = 13f

    /** Deep-fry's sharpen amount at strength 1: [ImagePipelineRules.DEEP_FRY_SHARPEN], the same unsharp mask as the photo editor. */
    const val FRY_SHARPEN = ImagePipelineRules.DEEP_FRY_SHARPEN

    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f

    /** [value] as a GLSL ES float literal (always with a decimal point), for constants the shaders share with this twin. */
    fun glslFloat(value: Float): String = value.toString().let { if ('.' in it || 'E' in it || 'e' in it) it else "$it.0" }

    /** Which effects this twin draws. */
    fun draws(effectId: String): Boolean = EffectCatalog.isBuilt(effectId)

    // ---- Parameters the shaders read ---------------------------------------------------------------------------------

    /** A frame's geometry at one moment: the picture scaled by [scale] about the center, turned [rotationRadians] (clockwise on screen), then moved [dx], [dy] pixels. */
    class MotionPose {
        var scale = 1f
        var rotationRadians = 0f
        var dx = 0f
        var dy = 0f
    }

    /**
     * Fills [pose] for a Motion effect at strength [strength], [tauSeconds] into the item, on a frame [width] x [height]; false for any other
     * effect. Effects that move the frame scale it up just enough that no canvas edge shows ("cover"), by this strength's biggest move, not
     * the current one, so the zoom doesn't pump as the frame shakes.
     */
    fun motionPose(effectId: String, strength: Float, tauSeconds: Float, width: Int, height: Int, pose: MotionPose): Boolean {
        val short = min(width, height).toFloat()
        pose.scale = 1f
        pose.rotationRadians = 0f
        pose.dx = 0f
        pose.dy = 0f
        when (effectId) {
            "zoom_punch" -> pose.scale = 1f + ZOOM_PUNCH_SCALE * strength
            "slow_push" -> pose.scale = 1f + SLOW_PUSH_SCALE * strength
            "shake" -> {
                val amplitude = SHAKE_OFFSET * short * strength
                pose.dx = amplitude * (sin(TAU * 13f * tauSeconds) + 0.5f * sin(TAU * 29f * tauSeconds)) / 1.5f
                pose.dy = amplitude * (sin(TAU * 11f * tauSeconds) + 0.5f * sin(TAU * 31f * tauSeconds)) / 1.5f
                pose.rotationRadians = radians(SHAKE_ROTATION_DEGREES * strength) * sin(TAU * 17f * tauSeconds)
                pose.scale = coverScale(width, height, radians(SHAKE_ROTATION_DEGREES * strength), amplitude * SHAKE_REACH_X, amplitude * SHAKE_REACH_Y)
            }
            "wobble" -> {
                pose.rotationRadians = radians(WOBBLE_ROTATION_DEGREES * strength) * sin(TAU * 1.2f * tauSeconds)
                pose.scale = coverScale(width, height, radians(WOBBLE_ROTATION_DEGREES * strength), 0f, 0f)
            }
            "bounce" -> {
                val amplitude = BOUNCE_OFFSET * short * strength
                pose.dy = -amplitude * abs(sin(TAU * 2f * tauSeconds))
                pose.scale = coverScale(width, height, 0f, 0f, amplitude)
            }
            else -> return false
        }
        return true
    }

    // Shake's offsets are (sin + 0.5 sin) / 1.5: at most 1 either way.
    private const val SHAKE_REACH_X = 1f
    private const val SHAKE_REACH_Y = 1f

    // The smallest scale at which the picture, turned by up to [turn] and moved by up to [moveX], [moveY], still covers the frame.
    private fun coverScale(width: Int, height: Int, turn: Float, moveX: Float, moveY: Float): Float {
        val turned = abs(turn)
        // The move is along the screen's axes but the picture's edges are turned: each axis also feels the other's move by sin(turn).
        val alongX = moveX + moveY * sin(turned)
        val alongY = moveY + moveX * sin(turned)
        val wide = max(cos(turned) + height.toFloat() / width * sin(turned) + 2f * alongX / width, cos(turned) + width.toFloat() / height * sin(turned) + 2f * alongY / height)
        return max(1f, wide)
    }

    /**
     * Fills [out] (12 floats) with a color effect that is a straight mix of the channels: rows of a 3 x 3 matrix, then three offsets, so
     * `out = clamp(matrix * rgb + offset)`; false for an effect that isn't one. Flash, Light pulse, Negative, Mono and Duotone are.
     */
    fun colorTransform(effectId: String, strength: Float, tauSeconds: Float, out: FloatArray): Boolean {
        identity(out)
        when (effectId) {
            "flash" -> {
                val white = FLASH_WHITE * strength
                scaleAndShift(out, 1f - white, white)
            }
            "light_pulse" -> scaleAndShift(out, 1f + PULSE_SWELL * strength * (0.5f - 0.5f * cos(TAU * PULSE_HZ * tauSeconds)), 0f)
            "negative" -> scaleAndShift(out, 1f - 2f * strength, strength)
            "mono" -> mono(strength, out)
            "duotone" -> duotone(strength, out)
            else -> return false
        }
        return true
    }

    private fun identity(out: FloatArray) {
        out.fill(0f)
        out[0] = 1f
        out[4] = 1f
        out[8] = 1f
    }

    private fun scaleAndShift(out: FloatArray, scale: Float, shift: Float) {
        out[0] = scale
        out[4] = scale
        out[8] = scale
        out[9] = shift
        out[10] = shift
        out[11] = shift
    }

    // Saturation x (1 - s) about the Rec. 709 luma, then contrast +10% x s about mid-gray: one matrix.
    private fun mono(strength: Float, out: FloatArray) {
        val keep = 1f - strength
        val contrast = 1f + MONO_CONTRAST * strength
        val luma = floatArrayOf(LUMA_R, LUMA_G, LUMA_B)
        for (row in 0..2) for (column in 0..2) {
            out[row * 3 + column] = contrast * ((if (row == column) keep else 0f) + strength * luma[column])
        }
        for (row in 0..2) out[9 + row] = 0.5f * (1f - contrast)
    }

    // Luma runs from the shadow color to the highlight color; the picture is mixed with that by the strength.
    private fun duotone(strength: Float, out: FloatArray) {
        val shadow = rgbOf(DUOTONE_SHADOW)
        val highlight = rgbOf(DUOTONE_HIGHLIGHT)
        val luma = floatArrayOf(LUMA_R, LUMA_G, LUMA_B)
        for (row in 0..2) {
            for (column in 0..2) out[row * 3 + column] = (if (row == column) 1f - strength else 0f) + strength * (highlight[row] - shadow[row]) * luma[column]
            out[9 + row] = strength * shadow[row]
        }
    }

    /** Poster's number of levels per channel at strength [strength]: 16 down to 3. */
    fun posterLevels(strength: Float): Float = (POSTER_LEVELS_FAR - POSTER_LEVELS_SPAN * strength).roundToInt().toFloat()

    /** The spotlight's bright circle radius (a share of the half-diagonal) at [strength]. */
    fun spotlightRadius(strength: Float): Float = SPOTLIGHT_RADIUS_FAR - (SPOTLIGHT_RADIUS_FAR - SPOTLIGHT_RADIUS_NEAR) * strength

    /** Where the sun leak's center is along the top edge, in pixels, [tauSeconds] into the item. */
    fun sunCenterX(tauSeconds: Float, width: Int): Float {
        // Out and back along the edge (a triangle wave), starting at the left edge.
        val cycle = SUN_CYCLES_PER_SECOND * tauSeconds
        return width * (1f - abs(1f - 2f * (cycle - floor(cycle))))
    }

    /** The glow's blur sigma in pixels of its quarter-size copy, for a frame whose shorter side is [shortSidePx]. */
    fun glowSigma(shortSidePx: Int): Float = GLOW_BLUR_SIGMA * shortSidePx / GLOW_DOWNSCALE

    /** How many texels each side of a pixel the glow's blur reads: three sigma. */
    fun glowReach(shortSidePx: Int): Int = ceil(3f * glowSigma(shortSidePx)).toInt().coerceAtLeast(1)

    /** The size of the glow's quarter-size copy of a [width] x [height] frame. */
    fun glowSize(width: Int, height: Int): Pair<Int, Int> = Pair(ceil(width / GLOW_DOWNSCALE.toFloat()).toInt(), ceil(height / GLOW_DOWNSCALE.toFloat()).toInt())

    // ---- The CPU twin ------------------------------------------------------------------------------------------------

    /**
     * Draws effect [effectId] at strength [strength], [tauUs] into its item, on [pixels] ([width] x [height] ARGB), in place. Strength 0 and
     * ids this twin doesn't draw change nothing.
     */
    fun apply(effectId: String, strength: Float, tauUs: Long, pixels: IntArray, width: Int, height: Int) {
        if (strength <= 0f || width <= 0 || height <= 0 || !draws(effectId)) return
        val tau = tauUs / MICROS_PER_SECOND
        val pose = MotionPose()
        val matrix = FloatArray(MATRIX_FLOATS)
        when {
            motionPose(effectId, strength, tau, width, height, pose) -> motion(pixels, width, height, pose)
            colorTransform(effectId, strength, tau, matrix) -> colorMix(pixels, matrix)
            effectId == "poster" -> poster(pixels, posterLevels(strength))
            effectId == "spotlight" -> spotlight(pixels, width, height, strength)
            effectId == "sun_leak" -> sunLeak(pixels, width, height, strength, tau)
            effectId == "glow" -> glow(pixels, width, height, strength)
            effectId == "deep_fry" -> deepFry(pixels, width, height, strength)
        }
    }

    private fun motion(pixels: IntArray, width: Int, height: Int, pose: MotionPose) {
        val source = pixels.copyOf()
        val centerX = width / 2f
        val centerY = height / 2f
        val cosine = cos(pose.rotationRadians)
        val sine = sin(pose.rotationRadians)
        val rgb = FloatArray(3)
        for (y in 0 until height) for (x in 0 until width) {
            val offsetX = x + 0.5f - centerX - pose.dx
            val offsetY = y + 0.5f - centerY - pose.dy
            val sourceX = centerX + (offsetX * cosine + offsetY * sine) / pose.scale
            val sourceY = centerY + (-offsetX * sine + offsetY * cosine) / pose.scale
            bilinear(source, width, height, sourceX, sourceY, rgb)
            pixels[y * width + x] = pack(pixels[y * width + x], rgb[0], rgb[1], rgb[2])
        }
    }

    private fun colorMix(pixels: IntArray, m: FloatArray) {
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = red(p)
            val g = green(p)
            val b = blue(p)
            pixels[i] = pack(p, m[0] * r + m[1] * g + m[2] * b + m[9], m[3] * r + m[4] * g + m[5] * b + m[10], m[6] * r + m[7] * g + m[8] * b + m[11])
        }
    }

    private fun poster(pixels: IntArray, levels: Float) {
        val steps = levels - 1f
        for (i in pixels.indices) {
            val p = pixels[i]
            pixels[i] = pack(p, floor(red(p) * steps + 0.5f) / steps, floor(green(p) * steps + 0.5f) / steps, floor(blue(p) * steps + 0.5f) / steps)
        }
    }

    private fun spotlight(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val centerX = width / 2f
        val centerY = height / 2f
        val halfDiagonal = 0.5f * sqrt((width.toFloat() * width + height.toFloat() * height))
        val radius = spotlightRadius(strength)
        for (y in 0 until height) for (x in 0 until width) {
            val distance = sqrt((x + 0.5f - centerX) * (x + 0.5f - centerX) + (y + 0.5f - centerY) * (y + 0.5f - centerY)) / halfDiagonal
            val outside = smoothstep(radius * (1f - SPOTLIGHT_EDGE / 2f), radius * (1f + SPOTLIGHT_EDGE / 2f), distance)
            val factor = 1f - SPOTLIGHT_DARKEN * strength * outside
            val p = pixels[y * width + x]
            pixels[y * width + x] = pack(p, red(p) * factor, green(p) * factor, blue(p) * factor)
        }
    }

    private fun sunLeak(pixels: IntArray, width: Int, height: Int, strength: Float, tauSeconds: Float) {
        val centerX = sunCenterX(tauSeconds, width)
        val radius = SUN_RADIUS * min(width, height)
        val sun = rgbOf(SUN_RGB)
        for (y in 0 until height) for (x in 0 until width) {
            val dx = x + 0.5f - centerX
            val dy = y + 0.5f
            val falloff = 1f - min(sqrt(dx * dx + dy * dy) / radius, 1f)
            val amount = falloff * falloff * SUN_AMOUNT * strength
            val p = pixels[y * width + x]
            pixels[y * width + x] = pack(p, screen(red(p), sun[0] * amount), screen(green(p), sun[1] * amount), screen(blue(p), sun[2] * amount))
        }
    }

    // Screen of the picture and a layer: 1 - (1 - c)(1 - layer).
    private fun screen(c: Float, layer: Float): Float = 1f - (1f - c) * (1f - layer)

    /** Pixels above [GLOW_THRESHOLD_LUMA] go to a quarter-size copy, which is blurred (rows, then columns) and screen-added back at 80% x s. */
    private fun glow(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val (lowWidth, lowHeight) = glowSize(width, height)
        val low = IntArray(lowWidth * lowHeight)
        val rgb = FloatArray(3)
        for (j in 0 until lowHeight) for (i in 0 until lowWidth) {
            var r = 0f
            var g = 0f
            var b = 0f
            for (b4 in 0 until GLOW_DOWNSCALE) for (a4 in 0 until GLOW_DOWNSCALE) {
                val p = pixels[min(j * GLOW_DOWNSCALE + b4, height - 1) * width + min(i * GLOW_DOWNSCALE + a4, width - 1)]
                val luma = LUMA_R * red(p) + LUMA_G * green(p) + LUMA_B * blue(p)
                if (luma > GLOW_THRESHOLD_LUMA) {
                    r += red(p)
                    g += green(p)
                    b += blue(p)
                }
            }
            val share = 1f / (GLOW_DOWNSCALE * GLOW_DOWNSCALE)
            low[j * lowWidth + i] = pack(OPAQUE, r * share, g * share, b * share)
        }
        val reach = glowReach(min(width, height))
        val sigma = glowSigma(min(width, height))
        val blurred = blur(blur(low, lowWidth, lowHeight, reach, sigma, horizontal = true), lowWidth, lowHeight, reach, sigma, horizontal = false)
        for (y in 0 until height) for (x in 0 until width) {
            bilinear(blurred, lowWidth, lowHeight, (x + 0.5f) / width * lowWidth, (y + 0.5f) / height * lowHeight, rgb)
            val p = pixels[y * width + x]
            val amount = GLOW_AMOUNT * strength
            pixels[y * width + x] = pack(p, screen(red(p), rgb[0] * amount), screen(green(p), rgb[1] * amount), screen(blue(p), rgb[2] * amount))
        }
    }

    private fun blur(source: IntArray, width: Int, height: Int, reach: Int, sigma: Float, horizontal: Boolean): IntArray {
        val weights = FloatArray(2 * reach + 1) { exp(-((it - reach) * (it - reach)) / (2f * sigma * sigma)) }
        val total = weights.sum()
        val out = IntArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            var r = 0f
            var g = 0f
            var b = 0f
            for (k in -reach..reach) {
                val p = if (horizontal) source[y * width + (x + k).coerceIn(0, width - 1)] else source[(y + k).coerceIn(0, height - 1) * width + x]
                val w = weights[k + reach] / total
                r += w * red(p)
                g += w * green(p)
                b += w * blue(p)
            }
            out[y * width + x] = pack(OPAQUE, r, g, b)
        }
        return out
    }

    /**
     * The photo editor's Deep-fry at amount [strength] (`ImagePipeline.deepFryRgb` with its grain), stored as 8 bits, then its hard
     * sharpen (an unsharp mask of [FRY_SHARPEN] x [strength], the blur `VideoLookMath.sharpenKernel`), as the video look's second pass does.
     */
    private fun deepFry(pixels: IntArray, width: Int, height: Int, strength: Float) {
        val grainCell = ImagePipeline.grainCellPx(min(width, height).toFloat())
        val rgb = FloatArray(3)
        for (y in 0 until height) for (x in 0 until width) {
            val p = pixels[y * width + x]
            rgb[0] = red(p)
            rgb[1] = green(p)
            rgb[2] = blue(p)
            ImagePipeline.deepFryRgb(rgb, ImagePipeline.grainAt(x, y, grainCell), strength)
            pixels[y * width + x] = pack(p, rgb[0], rgb[1], rgb[2])
        }
        val source = pixels.copyOf()
        val kernel = VideoLookMath.sharpenKernel(VideoLookMath.sharpenRadiusPx(min(width, height).toFloat()))
        val reach = kernel.size / 2
        val amount = FRY_SHARPEN * strength
        val blur = FloatArray(3)
        for (y in 0 until height) for (x in 0 until width) {
            val own = source[y * width + x]
            VideoLookMath.blurAt(source, width, height, x, y, kernel, reach, blur)
            pixels[y * width + x] = pack(own, red(own) + amount * (red(own) - blur[0]), green(own) + amount * (green(own) - blur[1]), blue(own) + amount * (blue(own) - blur[2]))
        }
    }

    // ---- Pixels ------------------------------------------------------------------------------------------------------

    // The picture at the continuous point (x, y) (a pixel's center at +0.5) by bilinear filtering, the edge repeating: the sampler's linear filter and clamp-to-edge.
    private fun bilinear(source: IntArray, width: Int, height: Int, x: Float, y: Float, out: FloatArray) {
        val fx = x - 0.5f
        val fy = y - 0.5f
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val tx = fx - x0
        val ty = fy - y0
        val a = source[y0.coerceIn(0, height - 1) * width + x0.coerceIn(0, width - 1)]
        val b = source[y0.coerceIn(0, height - 1) * width + (x0 + 1).coerceIn(0, width - 1)]
        val c = source[(y0 + 1).coerceIn(0, height - 1) * width + x0.coerceIn(0, width - 1)]
        val d = source[(y0 + 1).coerceIn(0, height - 1) * width + (x0 + 1).coerceIn(0, width - 1)]
        out[0] = mix(mix(red(a), red(b), tx), mix(red(c), red(d), tx), ty)
        out[1] = mix(mix(green(a), green(b), tx), mix(green(c), green(d), tx), ty)
        out[2] = mix(mix(blue(a), blue(b), tx), mix(blue(c), blue(d), tx), ty)
    }

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun red(p: Int): Float = ((p shr 16) and 0xFF) / 255f
    private fun green(p: Int): Float = ((p shr 8) and 0xFF) / 255f
    private fun blue(p: Int): Float = (p and 0xFF) / 255f

    private fun pack(alphaFrom: Int, r: Float, g: Float, b: Float): Int = (alphaFrom and OPAQUE) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)

    private fun to8(v: Float): Int = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

    private fun rgbOf(rgb: Int): FloatArray = floatArrayOf(((rgb shr 16) and 0xFF) / 255f, ((rgb shr 8) and 0xFF) / 255f, (rgb and 0xFF) / 255f)

    private fun radians(degrees: Float): Float = degrees * (PI / 180.0).toFloat()

    private const val TAU = (2.0 * PI).toFloat()
    private const val MICROS_PER_SECOND = 1_000_000f
    private const val MATRIX_FLOATS = 12
    private const val OPAQUE = 0xFF000000.toInt()
}
