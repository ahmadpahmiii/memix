package app.memix.engine.video

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import app.memix.core.domain.image.ImagePipelineRules
import app.memix.core.domain.image.Lut
import app.memix.core.model.project.ClipLook

/**
 * Pass 1 of a clip's look (P4-07; spec docs/ux/specs/P4-07-video-filters-addendum.md → How it draws): per pixel, the photo editor's steps 2
 * to 4 in their order: brightness, contrast, saturation, warmth, then the filter's color table at its intensity. One fragment pass, no
 * neighbours, straight alpha kept. The shader is the GPU twin of `VideoLookMath.pointwise` (:core:domain) and of the photo path's
 * `ImagePipeline`; [lutTable] is the filter's table from [LutTables] (null when the look has no filter).
 */
@OptIn(UnstableApi::class)
internal class LookColorEffect(private val live: LiveLook, private val lutTable: Bitmap?) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram = LookColorProgram(live, lutTable, useHdr)
}

@OptIn(UnstableApi::class)
private class LookColorProgram(private val live: LiveLook, lutTable: Bitmap?, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(VERTEX_SHADER, if (lutTable != null) fragmentShader(withLut = true) else fragmentShader(withLut = false))
    private val lutTexture: Int = if (lutTable != null) GlUtil.createTexture(lutTable) else NO_TEXTURE

    // Set only when the look changes: drawing allocates nothing and the uniforms of an unchanged look are not recomputed.
    private var lastLook: ClipLook? = null
    private var brightness = 0f
    private var contrast = 1f
    private var saturation = 1f
    private var redGain = 1f
    private var blueGain = 1f
    private var intensity = 0f

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val look = live.look
        if (look !== lastLook) {
            lastLook = look
            updateUniforms(look)
        }
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            if (lutTexture != NO_TEXTURE) {
                program.setSamplerTexIdUniform("uLut", lutTexture, 1)
                program.setFloatUniform("uLutLength", Lut.SIZE.toFloat())
                program.setFloatUniform("uIntensity", intensity)
            }
            program.setFloatUniform("uBrightness", brightness)
            program.setFloatUniform("uContrast", contrast)
            program.setFloatUniform("uSaturation", saturation)
            program.setFloatUniform("uRedGain", redGain)
            program.setFloatUniform("uBlueGain", blueGain)
            program.bindAttributesAndUniforms()
            // The pass writes every channel itself: a blend state left on by an earlier effect would mix it with the old texture contents.
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    // Each adjustment is the number the photo path's step multiplies or adds with; zero adjustments are the identity (shift 0, factors 1).
    private fun updateUniforms(look: ClipLook) {
        val adjust = look.adjust
        brightness = adjust.brightness * ImagePipelineRules.BRIGHTNESS_SPAN
        contrast = 1f + adjust.contrast
        saturation = 1f + adjust.saturation
        redGain = 1f + ImagePipelineRules.WARMTH_SPAN * adjust.warmth
        blueGain = 1f - ImagePipelineRules.WARMTH_SPAN * adjust.warmth
        intensity = look.filter?.intensity?.coerceIn(0f, 1f) ?: 0f
    }

    override fun release() {
        super.release()
        try {
            program.delete()
            if (lutTexture != NO_TEXTURE) GlUtil.deleteTexture(lutTexture)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val VERTEX_COUNT = 4
        const val NO_TEXTURE = -1

        const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        // The lookup is Media3's own (fragment_shader_lut_es2.glsl): the table is N wide and N * N tall, x is blue, the red plane picks a
        // band, green the row in it; two bilinear reads and a mix by the red fraction make the trilinear sample Lut.sample does.
        const val LUT_FUNCTION = """
uniform sampler2D uLut;
uniform float uLutLength;
uniform float uIntensity;
vec3 applyLookup(vec3 color) {
  float redCoord = color.r * (uLutLength - 1.0);
  float redCoordLow = clamp(floor(redCoord), 0.0, uLutLength - 2.0);
  float lowerY = (0.5 + redCoordLow * uLutLength + color.g * (uLutLength - 1.0)) / (uLutLength * uLutLength);
  float upperY = lowerY + 1.0 / uLutLength;
  float x = (0.5 + color.b * (uLutLength - 1.0)) / uLutLength;
  vec3 lowerRgb = texture2D(uLut, vec2(x, lowerY)).rgb;
  vec3 upperRgb = texture2D(uLut, vec2(x, upperY)).rgb;
  return mix(lowerRgb, upperRgb, redCoord - redCoordLow);
}
"""

        // Mirrors VideoLookMath.pointwise: each step clamps, as the photo path does.
        fun fragmentShader(withLut: Boolean): String = """
precision highp float;
uniform sampler2D uTexSampler;
uniform float uBrightness;
uniform float uContrast;
uniform float uSaturation;
uniform float uRedGain;
uniform float uBlueGain;
varying vec2 vTexSamplingCoord;
${if (withLut) LUT_FUNCTION else ""}
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 c = clamp(src.rgb + uBrightness, 0.0, 1.0);
  c = clamp((c - 0.5) * uContrast + 0.5, 0.0, 1.0);
  float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
  c = clamp(vec3(luma) + (c - vec3(luma)) * uSaturation, 0.0, 1.0);
  c = clamp(c * vec3(uRedGain, 1.0, uBlueGain), 0.0, 1.0);
${if (withLut) "  if (uIntensity > 0.0) c = mix(c, applyLookup(c), uIntensity);" else ""}
  gl_FragColor = vec4(c, src.a);
}
"""
    }
}
