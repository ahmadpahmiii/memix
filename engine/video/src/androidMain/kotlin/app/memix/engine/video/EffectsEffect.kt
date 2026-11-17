package app.memix.engine.video

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import app.memix.core.domain.image.EffectMath
import app.memix.core.domain.image.ImagePipeline
import app.memix.core.domain.image.VideoLookMath
import app.memix.core.domain.project.effectStrength
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The video effects of P4-06a as one composition-level pass (spec docs/ux/specs/P4-06-video-effects.md → Where effects sit): after the compositor
 * and the captions, before the export's watermark, so a Deep-fry fries the caption and the mark stays clean. Every frame it reads the items in
 * [live] that are active at the frame's presentation time (composition time), works out each one's strength from its envelope, and draws one
 * pass per item in lane order (at most [MAX_ACTIVE]; spec: three at once), the picture ping-ponging between two targets of its own, the last pass
 * into the frame Media3 handed over. With nothing active the frame is copied across. Preview and export run the same code, and every number comes
 * from `EffectMath`, which is also the CPU twin the reference renders are made with.
 */
@OptIn(UnstableApi::class)
internal class EffectsEffect(private val live: LiveEffects) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram = EffectsProgram(live, useHdr)
}

/** A color texture of its own with the framebuffer that draws into it. */
@OptIn(UnstableApi::class)
private class Target(val width: Int, val height: Int) {
    val texture: Int = GlUtil.createTexture(width, height, /* useHighPrecisionColorComponents= */ false)
    val framebuffer: Int = GlUtil.createFboForTexture(texture)

    fun release() {
        GlUtil.deleteFbo(framebuffer)
        GlUtil.deleteTexture(texture)
    }
}

@OptIn(UnstableApi::class)
private class EffectsProgram(private val live: LiveEffects, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private var width = 0
    private var height = 0

    // The glow's quarter-size copy and its blur's reach for this size, made when the size is set.
    private var glowLowWidth = 0
    private var glowLowHeight = 0
    private var glowReach = 0
    private var glowBlurKey = GLOW_BLUR

    // Compiled when first needed and kept until the size changes (the blur and sharpen kernels depend on it).
    private val programs = HashMap<String, GlProgram>()

    // Full-size targets for the passes between effects and inside Deep-fry, the glow's quarter-size ones; made on first use.
    private var ping0: Target? = null
    private var ping1: Target? = null
    private var scratch: Target? = null
    private var low0: Target? = null
    private var low1: Target? = null
    private var grainTexture = NO_TEXTURE
    private var grainWidth = 0
    private var grainHeight = 0

    // What is active this frame, refilled in place: drawing allocates nothing.
    private val activeIds = arrayOfNulls<String>(MAX_ACTIVE)
    private val activeStrengths = FloatArray(MAX_ACTIVE)
    private val activeTaus = FloatArray(MAX_ACTIVE)
    private val pose = EffectMath.MotionPose()
    private val matrix = FloatArray(MATRIX_FLOATS)
    private val poseUniform = FloatArray(4)
    private val shiftUniform = FloatArray(2)
    private val sizeUniform = FloatArray(2)
    private val lowSizeUniform = FloatArray(2)
    private val grainSizeUniform = FloatArray(2)
    private val rowUniform = FloatArray(3)
    private val vec3Uniform = FloatArray(3)
    private val outputFramebuffer = IntArray(1)

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        if (inputWidth != width || inputHeight != height) {
            try {
                releaseSizeBound()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
            width = inputWidth
            height = inputHeight
            sizeUniform[0] = inputWidth.toFloat()
            sizeUniform[1] = inputHeight.toFloat()
            val (lowWidth, lowHeight) = EffectMath.glowSize(inputWidth, inputHeight)
            glowLowWidth = lowWidth
            glowLowHeight = lowHeight
            lowSizeUniform[0] = lowWidth.toFloat()
            lowSizeUniform[1] = lowHeight.toFloat()
            glowReach = EffectMath.glowReach(min(inputWidth, inputHeight))
            glowBlurKey = "$GLOW_BLUR$glowReach"
        }
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            // The frame Media3 handed over is the last pass's target (and the passthrough's): its framebuffer is bound now.
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, outputFramebuffer, 0)
            val active = collectActive(presentationTimeUs)
            if (active == 0) {
                drawPass(program(PASSTHROUGH) { EffectShaders.PASSTHROUGH }, inputTexId, null)
                return
            }
            var source = inputTexId
            for (k in 0 until active) {
                val last = k == active - 1
                val destination = if (last) null else pingTarget(k % 2)
                drawEffect(activeIds[k].orEmpty(), activeStrengths[k], activeTaus[k], source, destination)
                if (destination != null) source = destination.texture
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    // The items in lane order that are on at [timeUs] with a strength above 0, up to MAX_ACTIVE; returns how many.
    private fun collectActive(timeUs: Long): Int {
        val spans = live.spans
        var count = 0
        for (index in spans.indices) {
            if (count == MAX_ACTIVE) break
            val span = spans[index]
            val elapsedUs = timeUs - span.startUs
            val strength = effectStrength(span.envelope, span.intensity, span.durationUs, elapsedUs)
            if (strength <= 0f) continue
            activeIds[count] = span.effectId
            activeStrengths[count] = strength
            activeTaus[count] = elapsedUs / MICROS_PER_SECOND
            count++
        }
        return count
    }

    // Draws one effect from [source] into [destination] (null: the frame Media3 handed over).
    private fun drawEffect(effectId: String, strength: Float, tau: Float, source: Int, destination: Target?) {
        when {
            EffectMath.motionPose(effectId, strength, tau, width, height, pose) -> drawMotion(source, destination)
            EffectMath.colorTransform(effectId, strength, tau, matrix) -> drawColorMix(source, destination)
            effectId == "poster" -> drawPoster(strength, source, destination)
            effectId == "spotlight" -> drawSpotlight(strength, source, destination)
            effectId == "sun_leak" -> drawSunLeak(strength, tau, source, destination)
            effectId == "glow" -> drawGlow(strength, source, destination)
            effectId == "deep_fry" -> drawDeepFry(strength, source, destination)
            // An id the catalog builds but this program doesn't draw would be a bug: show the picture untouched rather than a hole.
            else -> drawPass(program(PASSTHROUGH) { EffectShaders.PASSTHROUGH }, source, destination)
        }
    }

    private fun drawMotion(source: Int, destination: Target?) {
        val program = program(MOTION) { EffectShaders.MOTION }
        poseUniform[0] = pose.scale
        poseUniform[1] = cos(pose.rotationRadians)
        poseUniform[2] = sin(pose.rotationRadians)
        shiftUniform[0] = pose.dx
        shiftUniform[1] = pose.dy
        program.setFloatsUniform("uSize", sizeUniform)
        program.setFloatsUniform("uPose", poseUniform)
        program.setFloatsUniform("uShift", shiftUniform)
        drawPass(program, source, destination)
    }

    private fun drawColorMix(source: Int, destination: Target?) {
        val program = program(COLOR_MIX) { EffectShaders.COLOR_MIX }
        for (row in 0..2) {
            for (column in 0..2) rowUniform[column] = matrix[row * 3 + column]
            program.setFloatsUniform(ROW_NAMES[row], rowUniform)
        }
        for (row in 0..2) vec3Uniform[row] = matrix[9 + row]
        program.setFloatsUniform("uShift", vec3Uniform)
        drawPass(program, source, destination)
    }

    private fun drawPoster(strength: Float, source: Int, destination: Target?) {
        val program = program(POSTER) { EffectShaders.POSTER }
        program.setFloatUniform("uSteps", EffectMath.posterLevels(strength) - 1f)
        drawPass(program, source, destination)
    }

    private fun drawSpotlight(strength: Float, source: Int, destination: Target?) {
        val program = program(SPOTLIGHT) { EffectShaders.SPOTLIGHT }
        vec3Uniform[0] = EffectMath.spotlightRadius(strength)
        vec3Uniform[1] = EffectMath.SPOTLIGHT_DARKEN * strength
        vec3Uniform[2] = EffectMath.SPOTLIGHT_EDGE
        program.setFloatsUniform("uSize", sizeUniform)
        program.setFloatsUniform("uParams", vec3Uniform)
        drawPass(program, source, destination)
    }

    private fun drawSunLeak(strength: Float, tau: Float, source: Int, destination: Target?) {
        val program = program(SUN_LEAK) { EffectShaders.SUN_LEAK }
        vec3Uniform[0] = EffectMath.sunCenterX(tau, width)
        vec3Uniform[1] = EffectMath.SUN_RADIUS * min(width, height)
        vec3Uniform[2] = EffectMath.SUN_AMOUNT * strength
        program.setFloatsUniform("uSize", sizeUniform)
        program.setFloatsUniform("uSun", vec3Uniform)
        rowUniform[0] = ((EffectMath.SUN_RGB shr 16) and 0xFF) / 255f
        rowUniform[1] = ((EffectMath.SUN_RGB shr 8) and 0xFF) / 255f
        rowUniform[2] = (EffectMath.SUN_RGB and 0xFF) / 255f
        program.setFloatsUniform("uSunColor", rowUniform)
        drawPass(program, source, destination)
    }

    // Four passes: the thresholded quarter-size copy, two blurs of it, the screen-add over the frame.
    private fun drawGlow(strength: Float, source: Int, destination: Target?) {
        val first = lowTarget(0)
        val second = lowTarget(1)
        val down = program(GLOW_DOWN) { EffectShaders.GLOW_DOWN }
        down.setFloatsUniform("uSize", sizeUniform)
        down.setFloatsUniform("uLowSize", lowSizeUniform)
        drawPass(down, source, first)
        val blur = program(glowBlurKey) { EffectShaders.glowBlur(glowReach) }
        blur.setFloatUniform("uSigma", EffectMath.glowSigma(min(width, height)))
        shiftUniform[0] = 1f / glowLowWidth
        shiftUniform[1] = 0f
        blur.setFloatsUniform("uStep", shiftUniform)
        drawPass(blur, first.texture, second)
        shiftUniform[0] = 0f
        shiftUniform[1] = 1f / glowLowHeight
        blur.setFloatsUniform("uStep", shiftUniform)
        drawPass(blur, second.texture, first)
        val final = program(GLOW_FINAL) { EffectShaders.GLOW_FINAL }
        final.setFloatUniform("uAmount", EffectMath.GLOW_AMOUNT * strength)
        drawPass(final, source, destination, secondTexture = first.texture, secondName = "uGlow")
    }

    // Two passes: the photo editor's color recipe with its grain, then the hard sharpen.
    private fun drawDeepFry(strength: Float, source: Int, destination: Target?) {
        val grain = grainTexture()
        val color = program(FRY_COLOR) { EffectShaders.FRY_COLOR }
        color.setFloatsUniform("uSize", sizeUniform)
        grainSizeUniform[0] = grainWidth.toFloat()
        grainSizeUniform[1] = grainHeight.toFloat()
        color.setFloatsUniform("uGrainSize", grainSizeUniform)
        color.setFloatUniform("uGrainCell", ImagePipeline.grainCellPx(min(width, height).toFloat()))
        color.setFloatUniform("uFry", strength)
        val mid = scratchTarget()
        drawPass(color, source, mid, secondTexture = grain, secondName = "uGrain")
        val detail = program(FRY_DETAIL) { EffectShaders.fryDetail(VideoLookMath.sharpenTaps(VideoLookMath.sharpenKernel(VideoLookMath.sharpenRadiusPx(min(width, height).toFloat()))), width, height) }
        detail.setFloatUniform("uAmount", EffectMath.FRY_SHARPEN * strength)
        drawPass(detail, mid.texture, destination)
    }

    // One full-screen pass of [program] reading [source] (and a second texture when asked), into [destination] or the frame Media3 handed over.
    private fun drawPass(program: GlProgram, source: Int, destination: Target?, secondTexture: Int = NO_TEXTURE, secondName: String = "") {
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", source, 0)
        if (secondTexture != NO_TEXTURE) program.setSamplerTexIdUniform(secondName, secondTexture, 1)
        if (destination == null) {
            GlUtil.focusFramebufferUsingCurrentContext(outputFramebuffer[0], width, height)
        } else {
            GlUtil.focusFramebufferUsingCurrentContext(destination.framebuffer, destination.width, destination.height)
        }
        program.bindAttributesAndUniforms()
        // Every pass writes all of its channels: a blend state left on by an earlier effect would mix it with what the target held.
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
        GlUtil.checkGlError()
    }

    // The source is only made when the program isn't there yet, so a frame builds no strings.
    private inline fun program(key: String, fragment: () -> String): GlProgram = programs[key] ?: compile(fragment()).also { programs[key] = it }

    private fun compile(fragment: String): GlProgram = GlProgram(EffectShaders.VERTEX, fragment).also {
        it.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    private fun pingTarget(index: Int): Target {
        val existing = if (index == 0) ping0 else ping1
        if (existing != null) return existing
        return Target(width, height).also { if (index == 0) ping0 = it else ping1 = it }
    }

    private fun scratchTarget(): Target = scratch ?: Target(width, height).also { scratch = it }

    private fun lowTarget(index: Int): Target {
        val existing = if (index == 0) low0 else low1
        if (existing != null) return existing
        return Target(glowLowWidth, glowLowHeight).also { if (index == 0) low0 = it else low1 = it }
    }

    // The photo editor's grain, one byte per cell of the field (`ImagePipeline.grainOf`, -1 to 1 stored as 0 to 255), made once per frame size.
    private fun grainTexture(): Int {
        if (grainTexture != NO_TEXTURE) return grainTexture
        val cell = ImagePipeline.grainCellPx(min(width, height).toFloat())
        grainWidth = ceil(width / cell).toInt() + GRAIN_MARGIN_CELLS
        grainHeight = ceil(height / cell).toInt() + GRAIN_MARGIN_CELLS
        val bytes = ByteBuffer.allocateDirect(grainWidth * grainHeight)
        for (cy in 0 until grainHeight) for (cx in 0 until grainWidth) bytes.put(((ImagePipeline.grainOf(cx, cy) + 1f) * 0.5f * 255f + 0.5f).toInt().toByte())
        bytes.rewind()
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, grainWidth, grainHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, bytes)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, DEFAULT_UNPACK_ALIGNMENT)
        GlUtil.checkGlError()
        grainTexture = ids[0]
        return grainTexture
    }

    // Everything that depends on the frame's size: the kernels' programs, the targets, the grain.
    private fun releaseSizeBound() {
        programs.values.forEach { it.delete() }
        programs.clear()
        listOfNotNull(ping0, ping1, scratch, low0, low1).forEach { it.release() }
        ping0 = null
        ping1 = null
        scratch = null
        low0 = null
        low1 = null
        if (grainTexture != NO_TEXTURE) GLES20.glDeleteTextures(1, intArrayOf(grainTexture), 0)
        grainTexture = NO_TEXTURE
    }

    override fun release() {
        super.release()
        try {
            releaseSizeBound()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val MAX_ACTIVE = 3
        const val DEFAULT_UNPACK_ALIGNMENT = 4
        val ROW_NAMES = arrayOf("uRow0", "uRow1", "uRow2")
        const val VERTEX_COUNT = 4
        const val NO_TEXTURE = -1
        const val MICROS_PER_SECOND = 1_000_000f
        const val MATRIX_FLOATS = 12

        // A cell or two past the picture's edge: the grain's weights reach the neighbouring cell.
        const val GRAIN_MARGIN_CELLS = 3

        const val PASSTHROUGH = "passthrough"
        const val MOTION = "motion"
        const val COLOR_MIX = "colorMix"
        const val POSTER = "poster"
        const val SPOTLIGHT = "spotlight"
        const val SUN_LEAK = "sunLeak"
        const val GLOW_DOWN = "glowDown"
        const val GLOW_BLUR = "glowBlur"
        const val GLOW_FINAL = "glowFinal"
        const val FRY_COLOR = "fryColor"
        const val FRY_DETAIL = "fryDetail"
    }
}
