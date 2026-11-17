package app.memix.engine.video

import app.memix.core.domain.image.ImagePipelineRules
import app.memix.core.domain.image.SharpenTaps
import app.memix.core.domain.image.EffectMath

/**
 * The fragment shaders of the 15 effects of P4-06a (P4-06b's are in `GlitchShaders`, `BlurShaders` and `RetroShaders`; spec docs/ux/specs/P4-06-video-effects.md → The list), GLSL ES 1.00: the GPU twins of
 * `EffectMath` (:core:domain), which gives them every number. Each pass reads `uTexSampler`, the frame so far, and writes opaque 8-bit
 * color with the alpha it read. Coordinates in pixels are y down from the top-left, as in the twin: `pixel()` turns the sampler's
 * coordinate into them. No loop has a variable bound (GLSL ES 1.00 can't), so a blur's reach and a sharpen's taps are written into the
 * source when the frame size is known, as `LookDetailEffect` does.
 */
internal object EffectShaders {
    const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

    internal const val HEADER = """
precision highp float;
uniform sampler2D uTexSampler;
varying vec2 vTexSamplingCoord;
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
"""

    val PASSTHROUGH = HEADER + """
void main() { gl_FragColor = texture2D(uTexSampler, vTexSamplingCoord); }
"""

    // Motion: the output pixel looks up the picture scaled, turned and moved (EffectMath.motion): q = c + R(-turn) (p - c - shift) / scale.
    val MOTION = HEADER + """
uniform vec2 uSize;
uniform vec4 uPose;
uniform vec2 uShift;
void main() {
  vec2 p = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uSize;
  vec2 d = p - uSize * 0.5 - uShift;
  vec2 q = uSize * 0.5 + vec2(d.x * uPose.y + d.y * uPose.z, -d.x * uPose.z + d.y * uPose.y) / uPose.x;
  gl_FragColor = texture2D(uTexSampler, vec2(q.x / uSize.x, 1.0 - q.y / uSize.y));
}
"""

    // Flash, Light pulse, Negative, Mono and Duotone: out = clamp(M * rgb + shift), the rows of M in uRow0 to uRow2 (EffectMath.colorTransform).
    val COLOR_MIX = HEADER + """
uniform vec3 uRow0;
uniform vec3 uRow1;
uniform vec3 uRow2;
uniform vec3 uShift;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 c = vec3(dot(uRow0, src.rgb), dot(uRow1, src.rgb), dot(uRow2, src.rgb)) + uShift;
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), src.a);
}
"""

    val POSTER = HEADER + """
uniform float uSteps;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  gl_FragColor = vec4(floor(src.rgb * uSteps + 0.5) / uSteps, src.a);
}
"""

    // uParams: the bright circle's radius (a share of the half-diagonal), the outside's darkening (0.85 x s), the feather's share of the radius.
    val SPOTLIGHT = HEADER + """
uniform vec2 uSize;
uniform vec3 uParams;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec2 p = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uSize;
  float fromCenter = length(p - uSize * 0.5) / (0.5 * length(uSize));
  float outside = smoothstep(uParams.x * (1.0 - uParams.z * 0.5), uParams.x * (1.0 + uParams.z * 0.5), fromCenter);
  gl_FragColor = vec4(src.rgb * (1.0 - uParams.y * outside), src.a);
}
"""

    // uSun: the glow's center x along the top edge, its radius in pixels, its amount (45% x s).
    val SUN_LEAK = HEADER + """
uniform vec2 uSize;
uniform vec3 uSun;
uniform vec3 uSunColor;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec2 p = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uSize;
  float falloff = 1.0 - min(length(p - vec2(uSun.x, 0.0)) / uSun.y, 1.0);
  vec3 layer = uSunColor * (falloff * falloff * uSun.z);
  gl_FragColor = vec4(1.0 - (1.0 - src.rgb) * (1.0 - layer), src.a);
}
"""

    /** Deep-fry, pass 1: the photo editor's recipe (`ImagePipeline.deepFryRgb`), the grain read from a texture of the photo's own cell hash. */
    val FRY_COLOR = HEADER + """
uniform sampler2D uGrain;
uniform vec2 uSize;
uniform vec2 uGrainSize;
uniform float uGrainCell;
uniform float uFry;
const float SATURATION = ${EffectMath.glslFloat(ImagePipelineRules.DEEP_FRY_SATURATION)};
const float CONTRAST = ${EffectMath.glslFloat(ImagePipelineRules.DEEP_FRY_CONTRAST)};
const float TINT = ${EffectMath.glslFloat(ImagePipelineRules.DEEP_FRY_TINT)};
const float GRAIN = ${EffectMath.glslFloat(ImagePipelineRules.DEEP_FRY_GRAIN)};
float overlap(float cell, float start, float span) {
  return max(min(cell + 1.0, start + span) - max(cell, start), 0.0) / span;
}
// ImagePipeline.grainAt: the field's average over the pixel's own square, cells weighted by overlap (at most three each way from 360 px up).
float grainAt(vec2 pixel) {
  vec2 start = pixel / uGrainCell;
  float span = 1.0 / uGrainCell;
  vec2 first = floor(start);
  float sum = 0.0;
  for (int j = 0; j < 3; j++) {
    float weightY = overlap(first.y + float(j), start.y, span);
    for (int i = 0; i < 3; i++) {
      float cell = (texture2D(uGrain, (first + vec2(float(i), float(j)) + 0.5) / uGrainSize).r) * 2.0 - 1.0;
      sum += cell * weightY * overlap(first.x + float(i), start.x, span);
    }
  }
  return sum;
}
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec2 pixel = floor(vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uSize);
  vec3 c = src.rgb;
  float luma = dot(c, LUMA);
  c = clamp(vec3(luma) + (c - vec3(luma)) * (1.0 + SATURATION * uFry), 0.0, 1.0);
  c = clamp((c - 0.5) * (1.0 + CONTRAST * uFry) + 0.5, 0.0, 1.0);
  c = clamp(c * vec3(1.0 + TINT * uFry, 1.0, 1.0 - TINT * uFry), 0.0, 1.0);
  c = clamp(c + grainAt(pixel) * GRAIN * uFry, 0.0, 1.0);
  gl_FragColor = vec4(c, src.a);
}
"""

    /** Deep-fry, pass 2: the hard sharpen, an unsharp mask whose blur is the video look's separable kernel (`VideoLookMath.sharpenKernel`). */
    fun fryDetail(taps: SharpenTaps, width: Int, height: Int): String {
        val reads = StringBuilder()
        for (j in 0 until taps.count) for (i in 0 until taps.count) {
            reads.append("  blur += ${taps.weights[i] * taps.weights[j]} * texture2D(uTexSampler, vTexSamplingCoord + vec2(${taps.offsets[i] / width}, ${taps.offsets[j] / height})).rgb;\n")
        }
        return HEADER + """
uniform float uAmount;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 blur = vec3(0.0);
$reads  gl_FragColor = vec4(clamp(src.rgb + uAmount * (src.rgb - blur), 0.0, 1.0), src.a);
}
"""
    }

    /** Glow, pass 1: the frame's pixels above the luma threshold, averaged over 4 x 4 blocks, into the quarter-size copy. uSize is the frame's, uLowSize the copy's. */
    val GLOW_DOWN = HEADER + """
uniform vec2 uSize;
uniform vec2 uLowSize;
void main() {
  vec2 low = floor(vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uLowSize);
  vec3 sum = vec3(0.0);
  for (int b = 0; b < ${EffectMath.GLOW_DOWNSCALE}; b++) {
    for (int a = 0; a < ${EffectMath.GLOW_DOWNSCALE}; a++) {
      vec2 p = low * ${EffectMath.glslFloat(EffectMath.GLOW_DOWNSCALE.toFloat())} + vec2(float(a), float(b)) + 0.5;
      vec3 c = texture2D(uTexSampler, vec2(p.x / uSize.x, 1.0 - p.y / uSize.y)).rgb;
      if (dot(c, LUMA) > ${EffectMath.glslFloat(EffectMath.GLOW_THRESHOLD_LUMA)}) sum += c;
    }
  }
  gl_FragColor = vec4(sum / ${EffectMath.glslFloat((EffectMath.GLOW_DOWNSCALE * EffectMath.GLOW_DOWNSCALE).toFloat())}, 1.0);
}
"""

    /** Glow's passes 2 and 3 and the blur effects' (`BlurShaders`): a Gaussian along [uStep] (one texel along one axis) of [reach] texels each side, the edge repeating. */
    fun gaussianBlur(reach: Int): String = HEADER + """
uniform vec2 uStep;
uniform float uSigma;
void main() {
  vec3 sum = vec3(0.0);
  float total = 0.0;
  for (int k = -$reach; k <= $reach; k++) {
    float offset = float(k);
    float weight = exp(-offset * offset / (2.0 * uSigma * uSigma));
    sum += weight * texture2D(uTexSampler, vTexSamplingCoord + uStep * offset).rgb;
    total += weight;
  }
  gl_FragColor = vec4(sum / total, 1.0);
}
"""

    /** Glow, pass 4: the blurred copy, scaled up by the sampler's linear filter, screen-added to the frame ([uTexSampler]) at uAmount. */
    val GLOW_FINAL = HEADER + """
uniform sampler2D uGlow;
uniform float uAmount;
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 layer = texture2D(uGlow, vTexSamplingCoord).rgb * uAmount;
  gl_FragColor = vec4(1.0 - (1.0 - src.rgb) * (1.0 - layer), src.a);
}
"""
}
