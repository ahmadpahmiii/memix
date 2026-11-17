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
import app.memix.core.domain.project.ChromaKeyMath
import app.memix.core.model.project.ChromaKey
import app.memix.core.model.project.ChromaKeyCatalog
import kotlin.math.sqrt

/**
 * The green screen (P4-04; spec docs/ux/specs/P4-04-green-screen.md → How it draws) as one fragment pass over the clip's source
 * picture, before anything scales or colors it. Preview and export build the same effect from the same [live] key.
 *
 * The shader is the GPU twin of [ChromaKeyMath]: Cb and Cr only (BT.601 full range), a smoothstep from the tolerance to the tolerance
 * plus the feather, then the spill taken out along the key's direction. The output is straight alpha, the convention of Media3's
 * scaling and compositor (`GL_SRC_ALPHA`), so a keyed overlay's alpha reaches the compositor and the opacity slider multiplies it.
 * The constants come from [ChromaKeyCatalog], the one place the spec's numbers live.
 */
@OptIn(UnstableApi::class)
internal class ChromaKeyEffect(private val live: LiveChromaKey) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram = ChromaKeyProgram(live, useHdr)
}

@OptIn(UnstableApi::class)
private class ChromaKeyProgram(private val live: LiveChromaKey, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)

    // Reused every frame: drawing allocates nothing.
    private val keyChroma = FloatArray(2)
    private val keyDirection = FloatArray(2)
    private val toleranceAndFeather = FloatArray(2)
    private var lastKey: ChromaKey? = null

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val key = live.key
        // The uniforms change only when a slider or swatch changes the key: nothing is computed or allocated per frame otherwise.
        if (key !== lastKey) {
            lastKey = key
            ChromaKeyMath.chromaInto(key.colorArgb, keyChroma)
            val length = sqrt(keyChroma[0] * keyChroma[0] + keyChroma[1] * keyChroma[1])
            // A gray key has no direction, so there is no spill to take out.
            keyDirection[0] = if (length > MIN_KEY_CHROMA) keyChroma[0] / length else 0f
            keyDirection[1] = if (length > MIN_KEY_CHROMA) keyChroma[1] / length else 0f
            toleranceAndFeather[0] = ChromaKeyCatalog.toleranceOf(key.strength)
            toleranceAndFeather[1] = ChromaKeyCatalog.featherOf(key.strength, key.softness)
        }
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uKeyChroma", keyChroma)
            program.setFloatsUniform("uKeyDirection", keyDirection)
            program.setFloatsUniform("uToleranceAndFeather", toleranceAndFeather)
            program.bindAttributesAndUniforms()
            // The pass writes alpha itself: a blend state left on by an earlier effect would mix it with the old texture contents.
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
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val VERTEX_COUNT = 4
        const val MIN_KEY_CHROMA = 1e-4f

        const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        // Mirrors ChromaKeyMath.keyPixel line by line.
        val FRAGMENT_SHADER = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uKeyChroma;
uniform vec2 uKeyDirection;
uniform vec2 uToleranceAndFeather;
varying vec2 vTexSamplingCoord;
const float SPILL_REDUCTION = ${ChromaKeyCatalog.SPILL_REDUCTION};
const float BLEED_GUARD_ALPHA = ${ChromaKeyCatalog.BLEED_GUARD_ALPHA};
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec2 chroma = vec2(
      -0.168736 * src.r - 0.331264 * src.g + 0.5 * src.b,
      0.5 * src.r - 0.418688 * src.g - 0.081312 * src.b);
  float keyAlpha = smoothstep(uToleranceAndFeather.x, uToleranceAndFeather.x + uToleranceAndFeather.y, distance(chroma, uKeyChroma));
  float along = dot(chroma, uKeyDirection);
  float share = mix(1.0, SPILL_REDUCTION, min(keyAlpha / BLEED_GUARD_ALPHA, 1.0)) * (1.0 - keyAlpha);
  chroma -= uKeyDirection * (max(along, 0.0) * share);
  float luma = 0.299 * src.r + 0.587 * src.g + 0.114 * src.b;
  vec3 rgb = vec3(luma + 1.402 * chroma.y, luma - 0.344136 * chroma.x - 0.714136 * chroma.y, luma + 1.772 * chroma.x);
  gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), src.a * keyAlpha);
}
"""
    }
}
