package app.memix.engine.video

import app.memix.core.domain.image.BlurMath
import app.memix.core.domain.image.EffectMath

/**
 * The fragment shaders of the Blur tab (P4-06b), GLSL ES 1.00, the GPU twins of `BlurMath` (:core:domain). Blur, Tilt blur and Soft focus are four
 * passes each: [DOWN] into the quarter-size copy, `EffectShaders.gaussianBlur` along each axis of it, then a final mix; Radial blur and Smear are
 * one pass of 16 taps. Same conventions as `EffectShaders`.
 */
internal object BlurShaders {
    private val LAST_TAP = EffectMath.glslFloat((BlurMath.LINE_TAPS - 1).toFloat())

    /** Pass 1: each pixel of the quarter-size copy is its 4 x 4 block's average, as four reads at the block's inner corners (the linear filter averages 2 x 2 each). */
    val DOWN = EffectShaders.HEADER + """
uniform vec2 uSize;
uniform vec2 uLowSize;
vec3 at(vec2 p) { return texture2D(uTexSampler, vec2(p.x / uSize.x, 1.0 - p.y / uSize.y)).rgb; }
void main() {
  vec2 origin = floor(vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uLowSize) * ${EffectMath.glslFloat(BlurMath.DOWNSCALE.toFloat())};
  vec3 sum = at(origin + vec2(1.0, 1.0)) + at(origin + vec2(3.0, 1.0)) + at(origin + vec2(1.0, 3.0)) + at(origin + vec2(3.0, 3.0));
  gl_FragColor = vec4(sum * 0.25, 1.0);
}
"""

    /** Last pass of Blur and Soft focus: the frame mixed with the blurred copy ([uBlurred], scaled up by the linear filter) by [uMix], times [uBrightness]. */
    val MIX = EffectShaders.HEADER + """
uniform sampler2D uBlurred;
uniform float uMix;
uniform float uBrightness;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 blurred = texture2D(uBlurred, vTexSamplingCoord).rgb;
  gl_FragColor = vec4(clamp(mix(src.rgb, blurred, uMix) * uBrightness, 0.0, 1.0), src.a);
}
"""

    /** Last pass of Tilt blur: the blurred copy shows beyond the sharp band; uBand = the smoothstep's two edges in distances from the middle row (a share of the height), [uFade] = `BlurMath.fadeIn`. */
    val TILT_MIX = EffectShaders.HEADER + """
uniform sampler2D uBlurred;
uniform vec2 uBand;
uniform float uFade;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 blurred = texture2D(uBlurred, vTexSamplingCoord).rgb;
  float weight = smoothstep(uBand.x, uBand.y, abs((1.0 - vTexSamplingCoord.y) - 0.5)) * uFade;
  gl_FragColor = vec4(mix(src.rgb, blurred, weight), src.a);
}
"""

    /** Radial blur: 16 taps from the pixel toward the center, as far as [uLength] of the way. */
    val RADIAL = EffectShaders.HEADER + """
uniform vec2 uSize;
uniform float uLength;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec2 p = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uSize;
  vec2 toCenter = uSize * 0.5 - p;
  vec3 sum = vec3(0.0);
  for (int i = 0; i < ${BlurMath.LINE_TAPS}; i++) {
    vec2 q = p + toCenter * (uLength * float(i) / $LAST_TAP);
    sum += texture2D(uTexSampler, vec2(q.x / uSize.x, 1.0 - q.y / uSize.y)).rgb;
  }
  gl_FragColor = vec4(sum / ${EffectMath.glslFloat(BlurMath.LINE_TAPS.toFloat())}, src.a);
}
"""

    /** Smear: 16 taps along the row, over [uLength] pixels centered on the pixel. */
    val SMEAR = EffectShaders.HEADER + """
uniform vec2 uSize;
uniform float uLength;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 sum = vec3(0.0);
  for (int i = 0; i < ${BlurMath.LINE_TAPS}; i++) {
    float along = (float(i) / $LAST_TAP - 0.5) * uLength;
    sum += texture2D(uTexSampler, vec2(vTexSamplingCoord.x + along / uSize.x, vTexSamplingCoord.y)).rgb;
  }
  gl_FragColor = vec4(sum / ${EffectMath.glslFloat(BlurMath.LINE_TAPS.toFloat())}, src.a);
}
"""
}
