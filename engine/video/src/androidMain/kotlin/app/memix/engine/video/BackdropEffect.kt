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

/**
 * Lays a keyed main clip over the canvas background (P4-04; spec → The selected item on the stage): where the clip is transparent
 * (keyed out, or the space Fit leaves) the frame shows the background, and the result is opaque. The background is one color, or
 * the picture of P2-12 at frame size ([picture], decoded by the caller); black when it is neither. It runs last, on the frame-sized
 * result, so it costs one cheap pass, and only for a keyed main clip.
 */
@OptIn(UnstableApi::class)
internal class BackdropEffect(private val argb: Long, private val picture: Bitmap?) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram = BackdropProgram(argb, picture, useHdr)
}

@OptIn(UnstableApi::class)
private class BackdropProgram(argb: Long, private val picture: Bitmap?, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    private val color = floatArrayOf(
        ((argb shr RED_SHIFT) and BYTE) / BYTE_MAX,
        ((argb shr GREEN_SHIFT) and BYTE) / BYTE_MAX,
        (argb and BYTE) / BYTE_MAX,
    )
    private var pictureTexture = NO_TEXTURE

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            if (pictureTexture == NO_TEXTURE && picture != null) pictureTexture = GlUtil.createTexture(picture)
            val hasPicture = pictureTexture != NO_TEXTURE
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setSamplerTexIdUniform("uPicture", if (hasPicture) pictureTexture else inputTexId, 1)
            program.setFloatsUniform("uColor", color)
            program.setFloatUniform("uUsePicture", if (hasPicture) 1f else 0f)
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
            if (pictureTexture != NO_TEXTURE) GlUtil.deleteTexture(pictureTexture)
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val VERTEX_COUNT = 4
        const val NO_TEXTURE = -1
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val BYTE = 0xFFL
        const val BYTE_MAX = 255f

        const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        // A bitmap's first row is its top, a frame texture's first row is its bottom: the picture is sampled upside down to stand upright.
        const val FRAGMENT_SHADER = """
precision highp float;
uniform sampler2D uTexSampler;
uniform sampler2D uPicture;
uniform vec3 uColor;
uniform float uUsePicture;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 clip = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 picture = texture2D(uPicture, vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y)).rgb;
  vec3 background = mix(uColor, picture, uUsePicture);
  gl_FragColor = vec4(clip.rgb * clip.a + background * (1.0 - clip.a), 1.0);
}
"""
    }
}
