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
import app.memix.core.domain.image.TransitionMath

/**
 * One layer of the transitions of a clip (P4-08; spec docs/ux/specs/P4-08-transitions.md → What a transition is), the last effect on the clip's own
 * chain, after Fit or Fill and every look. A clip is drawn through up to two [windows] (the end of the clip going out and its start coming in);
 * outside them the frame is copied across, inside them it is drawn by the transition's shader, `TransitionShaders`.
 *
 * [onTop] says which layer this is. The main video is the top layer: its frames are the outgoing picture before the cut and the incoming one after
 * it, drawn with the alpha that lets the picture beneath show through. The partner sequence is the layer beneath and shows the other picture,
 * opaque. Frame times are composition times in the preview and the export alike (Media3 adds each item's place in its sequence).
 */
@OptIn(UnstableApi::class)
internal class TransitionEffect(private val windows: List<TransitionWindow>, private val onTop: Boolean) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram = TransitionProgram(windows, onTop, useHdr)
}

@OptIn(UnstableApi::class)
private class TransitionProgram(private val windows: List<TransitionWindow>, private val onTop: Boolean, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    // Compiled when a window first draws: a clip's frames outside its windows never build a transition program.
    private val programs = HashMap<String, GlProgram>()
    private var passthrough: GlProgram? = null
    private val sizeUniform = FloatArray(2)
    private val stateUniform = FloatArray(4)
    private val sliceUniform = FloatArray(TransitionShaders.SLICE_COUNT * TransitionMath.GLITCH_SLICE_FLOATS)
    private val oneSlice = FloatArray(TransitionMath.GLITCH_SLICE_FLOATS)
    private val burstUniform = FloatArray(1)

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        sizeUniform[0] = inputWidth.toFloat()
        sizeUniform[1] = inputHeight.toFloat()
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val window = windowAt(presentationTimeUs)
            val program = if (window == null) null else programFor(window.id)
            if (window == null || program == null) {
                draw(passthroughProgram(), inputTexId)
                return
            }
            val progress = window.progressAt(presentationTimeUs)
            // Before the cut the top layer is the outgoing picture and the layer beneath the incoming one; after it, the other way round.
            val beforeCut = presentationTimeUs < window.cutUs
            stateUniform[0] = if (beforeCut == onTop) 0f else 1f
            stateUniform[1] = if (onTop) 1f else 0f
            stateUniform[2] = progress
            stateUniform[3] = TransitionMath.eased(progress)
            program.setFloatsUniform("uSize", sizeUniform)
            program.setFloatsUniform("uState", stateUniform)
            if (window.id == GLITCH_CUT) setGlitch(program, progress, window.durationUs)
            draw(program, inputTexId)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    // An indexed loop: no iterator is made per frame.
    private fun windowAt(timeUs: Long): TransitionWindow? {
        for (i in windows.indices) if (timeUs in windows[i]) return windows[i]
        return null
    }

    private fun setGlitch(program: GlProgram, progress: Float, durationUs: Long) {
        val burst = TransitionMath.glitchBurstAt(progress, durationUs)
        TransitionMath.glitchSlices(burst, sizeUniform[0].toInt(), sizeUniform[1].toInt(), sliceUniform)
        burstUniform[0] = if (burst >= 0) 1f else 0f
        program.setFloatUniform("uBurst", burstUniform[0])
        for (i in 0 until TransitionShaders.SLICE_COUNT) {
            System.arraycopy(sliceUniform, i * TransitionMath.GLITCH_SLICE_FLOATS, oneSlice, 0, TransitionMath.GLITCH_SLICE_FLOATS)
            program.setFloatsUniform(SLICE_NAMES[i], oneSlice)
        }
    }

    private fun draw(program: GlProgram, inputTexId: Int) {
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        program.bindAttributesAndUniforms()
        // The pass writes every channel itself: a blend state left on by an earlier effect would mix it with what the target held.
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
        GlUtil.checkGlError()
    }

    // A transition id this build can't draw has no shader: the frame is copied (the planner leaves such a cut out, so this is only a guard).
    private fun programFor(id: String): GlProgram? {
        programs[id]?.let { return it }
        val fragment = TransitionShaders.forId(id) ?: return null
        return compile(fragment).also { programs[id] = it }
    }

    private fun passthroughProgram(): GlProgram = passthrough ?: compile(TransitionShaders.PASSTHROUGH).also { passthrough = it }

    private fun compile(fragment: String): GlProgram = GlProgram(EffectShaders.VERTEX, fragment).also {
        it.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun release() {
        super.release()
        try {
            programs.values.forEach { it.delete() }
            programs.clear()
            passthrough?.delete()
            passthrough = null
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val VERTEX_COUNT = 4
        const val GLITCH_CUT = "glitch_cut"
        val SLICE_NAMES = Array(TransitionShaders.SLICE_COUNT) { "uSlice$it" }
    }
}
