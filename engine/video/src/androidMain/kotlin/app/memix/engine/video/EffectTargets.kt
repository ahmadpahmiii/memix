package app.memix.engine.video

import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** A color texture of its own with the framebuffer that draws into it: one of the effects pass's ping-pong or quarter-size targets. */
@OptIn(UnstableApi::class)
internal class Target(val width: Int, val height: Int) {
    val texture: Int = GlUtil.createTexture(width, height, /* useHighPrecisionColorComponents= */ false)
    val framebuffer: Int = GlUtil.createFboForTexture(texture)

    fun release() {
        GlUtil.deleteFbo(framebuffer)
        GlUtil.deleteTexture(texture)
    }
}

/**
 * A one-byte-per-texel texture the shaders read as a table (Deep-fry's grain, the noise table of `EffectNoise`): nearest filtering, [repeats] or
 * clamped at the edge. Made on the GL thread from [bytes] (rewound by the caller); [release] deletes it.
 */
@OptIn(UnstableApi::class)
internal class ByteTexture(val width: Int, val height: Int, bytes: ByteBuffer, repeats: Boolean) {
    val id: Int

    init {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        val wrap = if (repeats) GLES20.GL_REPEAT else GLES20.GL_CLAMP_TO_EDGE
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width, height, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, bytes)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, DEFAULT_UNPACK_ALIGNMENT)
        GlUtil.checkGlError()
        id = ids[0]
    }

    fun release() {
        GLES20.glDeleteTextures(1, intArrayOf(id), 0)
    }

    private companion object {
        const val DEFAULT_UNPACK_ALIGNMENT = 4
    }
}
