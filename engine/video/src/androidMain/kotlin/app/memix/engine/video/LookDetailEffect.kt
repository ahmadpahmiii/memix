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
import app.memix.core.domain.image.ImagePipelineRules
import app.memix.core.domain.image.SharpenTaps
import app.memix.core.domain.image.VideoLookMath
import app.memix.core.model.project.ClipLook
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pass 2 of a clip's look (P4-07; spec docs/ux/specs/P4-07-video-filters-addendum.md → How it draws): sharpen, then vignette, on the
 * picture pass 1 left. Both are measured on the clip's own picture, which is this pass's whole input (the key's alpha is kept as it is).
 * Sharpen is the photo path's unsharp mask: the blur is the one separable kernel of `VideoLookMath.sharpenKernel`, read through the
 * sampler's linear filtering in merged taps, so a 1080 clip costs 25 reads where the photo path box-blurs twice. The kernel depends only
 * on the picture's size, so its offsets and weights are written into the shader's source when the size is known ([configure]) and
 * nothing is uploaded per frame; the amounts are uniforms that follow [LiveLook]. GPU twin of `VideoLookMath.process`.
 */
@OptIn(UnstableApi::class)
internal class LookDetailEffect(private val live: LiveLook) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram = LookDetailProgram(live, useHdr)
}

@OptIn(UnstableApi::class)
private class LookDetailProgram(private val live: LiveLook, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private var program: GlProgram? = null
    private val textureSize = FloatArray(2)
    private var halfDiagonal = 1f

    private var lastLook: ClipLook? = null
    private var amount = 0f
    private var vignette = 0f

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        textureSize[0] = inputWidth.toFloat()
        textureSize[1] = inputHeight.toFloat()
        val centerX = (inputWidth - 1) / 2f
        val centerY = (inputHeight - 1) / 2f
        halfDiagonal = sqrt(centerX * centerX + centerY * centerY).coerceAtLeast(1f)
        // The kernel is a function of the picture's size, so a new size is a new shader.
        val radius = VideoLookMath.sharpenRadiusPx(min(inputWidth, inputHeight).toFloat())
        val taps = VideoLookMath.sharpenTaps(VideoLookMath.sharpenKernel(radius))
        try {
            program?.delete()
            program = GlProgram(VERTEX_SHADER, fragmentShader(taps, inputWidth, inputHeight)).also {
                it.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
        lastLook = null
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = program ?: return
        val look = live.look
        if (look !== lastLook) {
            lastLook = look
            amount = look.adjust.sharpen * ImagePipelineRules.SHARPEN_AMOUNT
            vignette = look.adjust.vignette
        }
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTextureSize", textureSize)
            program.setFloatUniform("uHalfDiagonal", halfDiagonal)
            program.setFloatUniform("uAmount", amount)
            program.setFloatUniform("uVignette", vignette)
            program.bindAttributesAndUniforms()
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun release() {
        super.release()
        try {
            program?.delete()
            program = null
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val VERTEX_COUNT = 4

        const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        // The blur is the sum over the taps of weight_x * weight_y * the picture at that offset, written out tap by tap (no loop, no uniform
        // arrays): Media3's GlProgram has no array uniforms. Mirrors VideoLookMath.process; vignette is applied after sharpen, as there.
        fun fragmentShader(taps: SharpenTaps, width: Int, height: Int): String {
            val reads = StringBuilder()
            for (j in 0 until taps.count) for (i in 0 until taps.count) {
                val weight = taps.weights[i] * taps.weights[j]
                reads.append("    blur += ${weight}")
                reads.append(" * texture2D(uTexSampler, vTexSamplingCoord + vec2(${taps.offsets[i] / width}, ${taps.offsets[j] / height})).rgb;\n")
            }
            return """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTextureSize;
uniform float uHalfDiagonal;
uniform float uAmount;
uniform float uVignette;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 c = src.rgb;
  if (uAmount > 0.0) {
    vec3 blur = vec3(0.0);
$reads    c = clamp(c + uAmount * (c - blur), 0.0, 1.0);
  }
  if (uVignette > 0.0) {
    vec2 d = vTexSamplingCoord * uTextureSize - 0.5 - (uTextureSize - 1.0) * 0.5;
    float r = length(d) / uHalfDiagonal;
    float t = clamp((r - ${ImagePipelineRules.VIGNETTE_START}) / ${1f - ImagePipelineRules.VIGNETTE_START}, 0.0, 1.0);
    c = c * (1.0 - uVignette * (t * t * (3.0 - 2.0 * t)));
  }
  gl_FragColor = vec4(c, src.a);
}
"""
        }
    }
}
