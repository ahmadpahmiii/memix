package app.memix.engine.video

import app.memix.core.domain.image.EffectMath
import app.memix.core.domain.image.EffectNoise
import app.memix.core.domain.image.GlitchMath

/**
 * The fragment shaders of the Glitch tab (P4-06b), GLSL ES 1.00, the GPU twins of `GlitchMath` (:core:domain), which gives them every number.
 * Same conventions as `EffectShaders`: coordinates in pixels are y down, every float literal has a point, no loop has a variable bound.
 */
internal object GlitchShaders {
    /**
     * Glitch, RGB split and Tear: row `row` is moved sideways by the slice that covers it (later slices cover earlier ones) plus the tear band's
     * shift, then red is read [uSplit] pixels to the right and blue to the left. uTear = top row, height, amplitude; uSliceN = top, bottom, shift.
     *
     * It declares 13 uniform vectors (uSize, uTear, uSplit and ten slices) of the 16 that GLSL ES 1.00 guarantees a fragment shader: don't add more.
     */
    val SHIFT: String = EffectShaders.HEADER + buildString {
        append("uniform vec2 uSize;\nuniform vec3 uTear;\nuniform float uSplit;\n")
        for (i in 0 until GlitchMath.MAX_SLICES) append("uniform vec3 uSlice$i;\n")
        append("void main() {\n  float row = (1.0 - vTexSamplingCoord.y) * uSize.y;\n  float shift = 0.0;\n")
        for (i in 0 until GlitchMath.MAX_SLICES) append("  if (row >= uSlice$i.x && row < uSlice$i.y) shift = uSlice$i.z;\n")
        append(
            """  float along = (row - uTear.x) / uTear.y;
  if (along >= 0.0 && along < 1.0) shift += uTear.z * sin(${EffectMath.glslFloat((2.0 * Math.PI).toFloat())} * along);
  float at = vTexSamplingCoord.x * uSize.x - shift;
  vec4 middle = texture2D(uTexSampler, vec2(at / uSize.x, vTexSamplingCoord.y));
  float red = texture2D(uTexSampler, vec2((at + uSplit) / uSize.x, vTexSamplingCoord.y)).r;
  float blue = texture2D(uTexSampler, vec2((at - uSplit) / uSize.x, vTexSamplingCoord.y)).b;
  gl_FragColor = vec4(red, middle.g, blue, middle.a);
}
""",
        )
    }

    /** Pixelate: each block of [uBlock] pixels shows the picture at its middle, faded in by [uMix] (`GlitchMath.pixelMix`). */
    val PIXELATE = EffectShaders.HEADER + """
uniform vec2 uSize;
uniform float uBlock;
uniform float uMix;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec2 p = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uSize;
  vec2 middle = (floor(p / uBlock) + 0.5) * uBlock;
  vec3 block = texture2D(uTexSampler, vec2(middle.x / uSize.x, 1.0 - middle.y / uSize.y)).rgb;
  gl_FragColor = vec4(mix(src.rgb, block, uMix), src.a);
}
"""

    /**
     * Static and Film grain: adds the noise table (`EffectNoise`, a 256 x 256 texture) to every channel, [uAmplitude] x -1..1. uField = the cell's
     * size in pixels and the frame's shift of the table in cells along x and y; the texture repeats, so the shift wraps as the twin's `and 255` does.
     */
    val NOISE = EffectShaders.HEADER + """
uniform sampler2D uNoise;
uniform vec2 uSize;
uniform vec3 uField;
uniform float uAmplitude;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec2 pixel = floor(vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uSize);
  vec2 cell = floor(pixel / uField.x) + uField.yz;
  float n = texture2D(uNoise, (cell + 0.5) / ${EffectMath.glslFloat(EffectNoise.TABLE_SIZE.toFloat())}).r * 2.0 - 1.0;
  gl_FragColor = vec4(clamp(src.rgb + n * uAmplitude, 0.0, 1.0), src.a);
}
"""
}
