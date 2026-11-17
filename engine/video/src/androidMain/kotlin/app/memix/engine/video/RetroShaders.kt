package app.memix.engine.video

import app.memix.core.domain.image.EffectMath
import app.memix.core.domain.image.EffectNoise
import app.memix.core.domain.image.RetroMath

/** The fragment shaders of the Retro tab (P4-06b), GLSL ES 1.00, the GPU twins of `RetroMath` (:core:domain). Same conventions as `EffectShaders`; film grain is `GlitchShaders.NOISE`. */
internal object RetroShaders {
    private fun f(value: Float) = EffectMath.glslFloat(value)

    /**
     * VHS in one pass: colors read [uLook].x pixels apart (red right, blue left) and softened by two reads [uLook].y either side, the tracking band's rows
     * moved sideways (uBand = middle row, half height, jitter in pixels), every second row of [uLook].z pixels darkened by [uLook].w, then the noise
     * table's field added at [uAmplitude] (uField as in `GlitchShaders.NOISE`).
     */
    val VHS = EffectShaders.HEADER + """
uniform sampler2D uNoise;
uniform vec2 uSize;
uniform vec3 uBand;
uniform vec4 uLook;
uniform vec3 uField;
uniform float uAmplitude;
vec3 at(float x, float v) { return texture2D(uTexSampler, vec2(x / uSize.x, v)).rgb; }
void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  float row = (1.0 - vTexSamplingCoord.y) * uSize.y;
  float x = vTexSamplingCoord.x * uSize.x;
  if (abs(row - uBand.x) < uBand.y) x += uBand.z;
  float v = vTexSamplingCoord.y;
  float red = 0.5 * (at(x + uLook.x - uLook.y, v).r + at(x + uLook.x + uLook.y, v).r);
  float green = 0.5 * (at(x - uLook.y, v).g + at(x + uLook.y, v).g);
  float blue = 0.5 * (at(x - uLook.x - uLook.y, v).b + at(x - uLook.x + uLook.y, v).b);
  float dark = mod(floor(floor(row) / uLook.z), 2.0) >= 1.0 ? 1.0 - uLook.w : 1.0;
  vec2 pixel = floor(vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uSize);
  vec2 cell = floor(pixel / uField.x) + uField.yz;
  float n = texture2D(uNoise, (cell + 0.5) / ${f(EffectNoise.TABLE_SIZE.toFloat())}).r * 2.0 - 1.0;
  gl_FragColor = vec4(clamp(vec3(red, green, blue) * dark + n * uAmplitude, 0.0, 1.0), src.a);
}
"""

    /** Old screen: the picture bent by [uLook].x, a row of every 3 darkened and a stripe mask over the columns (both to [uLook].y), a vignette of [uLook].z; [uUnit] = `RetroMath.patternUnitPx`. */
    val OLD_SCREEN = EffectShaders.HEADER + """
uniform vec2 uSize;
uniform vec3 uLook;
uniform float uUnit;
void main() {
  vec2 halfSize = uSize * 0.5;
  vec2 p = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uSize;
  vec2 pixel = floor(p);
  vec2 n = (p - halfSize) / halfSize;
  float radiusSquared = dot(n, n);
  vec2 q = n * (1.0 + uLook.x * radiusSquared);
  float inside = clamp((1.0 - abs(q.x)) * halfSize.x + 0.5, 0.0, 1.0) * clamp((1.0 - abs(q.y)) * halfSize.y + 0.5, 0.0, 1.0);
  vec2 at = (q + 1.0) * halfSize;
  vec4 src = texture2D(uTexSampler, vec2(at.x / uSize.x, 1.0 - at.y / uSize.y));
  float rowDark = mod(floor(pixel.y / uUnit), 3.0) >= 2.0 ? uLook.y : 1.0;
  float column = mod(floor(pixel.x / uUnit), 3.0);
  vec3 stripe = vec3(column < 0.5 ? 1.0 : uLook.y, (column > 0.5 && column < 1.5) ? 1.0 : uLook.y, column > 1.5 ? 1.0 : uLook.y);
  float vignette = 1.0 - uLook.z * smoothstep(${f(RetroMath.SCREEN_VIGNETTE_NEAR)}, ${f(RetroMath.SCREEN_VIGNETTE_FAR)}, sqrt(radiusSquared));
  gl_FragColor = vec4(src.rgb * stripe * (rowDark * vignette * inside), src.a);
}
"""

    /** Fisheye: the pixel reads the picture at its distance from the center (in half-diagonals) x (1 + k r^2) / (1 + k). */
    val FISHEYE = EffectShaders.HEADER + """
uniform vec2 uSize;
uniform float uK;
void main() {
  vec2 halfSize = uSize * 0.5;
  float reach = length(halfSize);
  vec2 d = (vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uSize - halfSize) / reach;
  vec2 at = halfSize + d * ((1.0 + uK * dot(d, d)) / (1.0 + uK)) * reach;
  gl_FragColor = texture2D(uTexSampler, vec2(at.x / uSize.x, 1.0 - at.y / uSize.y));
}
"""

    /** Mirror: right of [uFold] (pixels from the left) the picture is read from the other side of the fold. */
    val MIRROR = EffectShaders.HEADER + """
uniform vec2 uSize;
uniform float uFold;
void main() {
  float x = vTexSamplingCoord.x * uSize.x;
  float at = x <= uFold ? x : 2.0 * uFold - x;
  gl_FragColor = texture2D(uTexSampler, vec2(at / uSize.x, vTexSamplingCoord.y));
}
"""
}
