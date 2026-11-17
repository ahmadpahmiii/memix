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
import app.memix.core.model.project.ClipMask
import app.memix.core.model.project.MaskCatalog
import app.memix.core.model.project.MaskShape
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.fittedImageSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A clip's shape mask (P4-03b; spec docs/ux/specs/P4-03b-shape-masks-and-blend.md → Mask: how it draws) as one fragment pass: the
 * picture is kept as it is and its alpha is multiplied by the shape's (1 inside, 0 outside, a smoothstep across the softness; Invert
 * swaps). The output is straight alpha, the convention of Media3's scaling and compositor, so the opacity slider and the key's alpha
 * multiply with it. Preview and export build the same effect from the same [live] mask.
 *
 * The mask lives in the clip's own box. On an overlay the texture is exactly that box ([fittedPicture] null). On a main clip the effect
 * runs on the frame after Fit, so the box is the picture fitted inside the frame ([fittedPicture] set); with Fill or a picture of the
 * frame's shape the box is the whole frame. The shader is the GPU twin of `MaskMath` (:core:domain).
 */
@OptIn(UnstableApi::class)
internal class MaskEffect(private val live: LiveMask, private val fittedPicture: PixelSize? = null) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram = MaskProgram(live, fittedPicture, useHdr)
}

@OptIn(UnstableApi::class)
private class MaskProgram(private val live: LiveMask, private val fittedPicture: PixelSize?, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)

    // Reused every frame: drawing allocates nothing.
    private val textureSize = FloatArray(2)
    private val center = FloatArray(2)
    private val half = FloatArray(2)
    private val turn = FloatArray(2)
    private val boxSize = FloatArray(2)
    private var shape = 0f
    private var cornerRadius = 0f
    private var edgeWidth = 1f
    private var invert = 0f
    private var lastMask: ClipMask? = null

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        textureSize[0] = inputWidth.toFloat()
        textureSize[1] = inputHeight.toFloat()
        val box = fittedPicture?.let { fittedImageSize(it, PixelSize(inputWidth, inputHeight)) }
        boxSize[0] = min(box?.width ?: inputWidth.toFloat(), inputWidth.toFloat())
        boxSize[1] = min(box?.height ?: inputHeight.toFloat(), inputHeight.toFloat())
        // A new size changes every uniform that is in pixels.
        lastMask = null
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val mask = live.mask
        // The uniforms change only when the mask changes: nothing is computed or allocated per frame otherwise.
        if (mask !== lastMask) {
            lastMask = mask
            updateUniforms(mask)
        }
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTextureSize", textureSize)
            program.setFloatsUniform("uCenter", center)
            program.setFloatsUniform("uHalf", half)
            program.setFloatsUniform("uTurn", turn)
            program.setFloatUniform("uShape", shape)
            program.setFloatUniform("uCornerRadius", cornerRadius)
            program.setFloatUniform("uEdgeWidth", edgeWidth)
            program.setFloatUniform("uInvert", invert)
            program.bindAttributesAndUniforms()
            // The pass writes alpha itself: a blend state left on by an earlier effect would mix it with the old texture contents.
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    // The mask's numbers are fractions of the box; the shader works in pixels of the texture, the box centered in it.
    private fun updateUniforms(mask: ClipMask) {
        val boxWidth = boxSize[0]
        val boxHeight = boxSize[1]
        val shorter = min(boxWidth, boxHeight)
        center[0] = textureSize[0] / 2f + (mask.centerX - MaskCatalog.CENTER) * boxWidth
        center[1] = textureSize[1] / 2f + (mask.centerY - MaskCatalog.CENTER) * boxHeight
        val radians = mask.rotationDegrees * (PI / 180.0).toFloat()
        turn[0] = cos(radians)
        turn[1] = sin(radians)
        shape = shaderId(mask.shape)
        invert = if (mask.invert) 1f else 0f
        edgeWidth = maxOf(mask.softness * MaskCatalog.FEATHER_SHARE * shorter, MaskCatalog.MIN_EDGE_PX)
        when (mask.shape) {
            MaskShape.RECTANGLE -> {
                half[0] = mask.width * boxWidth / 2f
                half[1] = mask.height * boxHeight / 2f
                cornerRadius = mask.cornerRoundness * MaskCatalog.CORNER_RADIUS_SHARE * min(half[0], half[1])
            }
            MaskShape.CIRCLE -> {
                half[0] = mask.width * shorter / 2f
                half[1] = mask.height * shorter / 2f
                cornerRadius = 0f
            }
            MaskShape.LINEAR -> {
                half[0] = 0f
                half[1] = 0f
                cornerRadius = 0f
            }
            MaskShape.MIRROR -> {
                half[0] = mask.width * shorter / 2f
                half[1] = 0f
                cornerRadius = 0f
            }
        }
    }

    private fun shaderId(shape: MaskShape): Float = when (shape) {
        MaskShape.RECTANGLE -> SHAPE_RECTANGLE
        MaskShape.CIRCLE -> SHAPE_CIRCLE
        MaskShape.LINEAR -> SHAPE_LINEAR
        MaskShape.MIRROR -> SHAPE_MIRROR
    }

    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val VERTEX_COUNT = 4

        // The ids the fragment shader branches on.
        const val SHAPE_RECTANGLE = 0f
        const val SHAPE_CIRCLE = 1f
        const val SHAPE_LINEAR = 2f
        const val SHAPE_MIRROR = 3f

        const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        // Mirrors MaskMath.signedDistance and alphaAt line by line. The shape ids are the SHAPE_ constants above (rectangle 0, circle 1, linear 2, mirror 3).
        // The texture's first row is its bottom, the mask's y runs down: the point is flipped before anything else.
        const val FRAGMENT_SHADER = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTextureSize;
uniform vec2 uCenter;
uniform vec2 uHalf;
uniform vec2 uTurn;
uniform float uShape;
uniform float uCornerRadius;
uniform float uEdgeWidth;
uniform float uInvert;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec2 d = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uTextureSize - uCenter;
  vec2 local = vec2(uTurn.x * d.x + uTurn.y * d.y, -uTurn.y * d.x + uTurn.x * d.y);
  float across = uTurn.y * d.x - uTurn.x * d.y;
  float sd;
  if (uShape < 0.5) {
    vec2 q = abs(local) - (uHalf - uCornerRadius);
    sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - uCornerRadius;
  } else if (uShape < 1.5) {
    sd = (length(local / uHalf) - 1.0) * min(uHalf.x, uHalf.y);
  } else if (uShape < 2.5) {
    sd = -across;
  } else {
    sd = abs(across) - uHalf.x;
  }
  float inside = 1.0 - smoothstep(-uEdgeWidth * 0.5, uEdgeWidth * 0.5, sd);
  float keep = mix(inside, 1.0 - inside, uInvert);
  gl_FragColor = vec4(src.rgb, src.a * keep);
}
"""
    }
}
