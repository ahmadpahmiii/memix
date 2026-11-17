package app.memix.engine.video

import app.memix.core.domain.image.EffectMath.glslFloat
import app.memix.core.domain.image.TransitionMath

/**
 * The fragment shaders of the 20 transitions (P4-08; spec docs/ux/specs/P4-08-transitions.md → The list), GLSL ES 1.00, the GPU twins of
 * `TransitionMath` (:core:domain), which gives them every number: each recipe below is the twin's `colorOf` and `weightOfIncoming` line by line.
 * One program per transition, all built from the same frame:
 *
 * - The pass reads one layer's own picture. `uState` = (1 for the incoming picture, 1 for the layer on top, progress, eased progress).
 * - `colorAt` is that picture as the recipe makes it, `weightAt` is how much of the incoming picture shows (m). The top layer is drawn with the alpha
 *   that makes the compositor's `src * a + dst * (1 - a)` equal `mix(outgoing, incoming, m)` over the opaque layer beneath it.
 * - Coordinates in pixels are y down from the top-left, as in the twin; pictures outside the frame are black. Every float literal has a point, no
 *   loop has a variable bound, every uniform is used, and the pass writes all of its channels with blending off.
 */
internal object TransitionShaders {
    const val SLICE_COUNT = TransitionMath.GLITCH_SLICES

    val PASSTHROUGH: String = EffectShaders.HEADER + "void main() { gl_FragColor = texture2D(uTexSampler, vTexSamplingCoord); }\n"

    /** The fragment shader of transition [id]; null for an id this build doesn't draw. */
    fun forId(id: String): String? {
        val recipe = RECIPES[id] ?: return null
        return buildString {
            append(EffectShaders.HEADER)
            append("uniform vec2 uSize;\nuniform vec4 uState;\n")
            if (id == "glitch_cut") {
                for (i in 0 until SLICE_COUNT) append("uniform vec3 uSlice$i;\n")
                append("uniform float uBurst;\n")
            }
            append(COMMON)
            append(recipe)
            append(MAIN)
        }
    }

    private val SOFT = glslFloat(TransitionMath.SOFT_EDGE)

    private const val COMMON = """
#define INCOMING (uState.x > 0.5)
#define P uState.z
#define Q uState.w
vec3 pic(vec2 px) {
  vec2 uv = px / uSize;
  if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) return vec3(0.0);
  return texture2D(uTexSampler, vec2(uv.x, 1.0 - uv.y)).rgb;
}
float away() { return INCOMING ? 1.0 - max(0.0, 2.0 * Q - 1.0) : min(1.0, 2.0 * Q); }
float swap() { return P >= 0.5 ? 1.0 : 0.0; }
"""

    private const val MAIN = """
void main() {
  vec2 px = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y) * uSize;
  vec3 c = colorAt(px);
  float a = 1.0;
  if (uState.y > 0.5) {
    float m = weightAt(px);
    a = INCOMING ? m : 1.0 - m;
  }
  gl_FragColor = vec4(c, a);
}
"""

    private fun recipe(color: String, weight: String) = "vec3 colorAt(vec2 px) {\n  $color\n}\nfloat weightAt(vec2 px) {\n  $weight\n}\n"

    private val PLAIN = "return pic(px);"
    private val BY_Q = "return Q;"
    private val BY_SWAP = "return swap();"
    private fun sweep(name: String) = "float $name = Q * ${glslFloat(1f + 2f * TransitionMath.SOFT_EDGE)} - $SOFT;"

    private val BLUR = """
  float sigma = ${glslFloat(TransitionMath.BLUR_SIGMA)} * min(uSize.x, uSize.y) * away();
  if (sigma < 0.5) return pic(px);
  float spacing = ${glslFloat(TransitionMath.BLUR_TAP_SPACING)} * sigma;
  vec3 sum = vec3(0.0);
  float total = 0.0;
  for (int j = 0; j < ${TransitionMath.BLUR_TAPS}; j++) {
    for (int i = 0; i < ${TransitionMath.BLUR_TAPS}; i++) {
      float dx = float(i) - ${glslFloat((TransitionMath.BLUR_TAPS / 2).toFloat())};
      float dy = float(j) - ${glslFloat((TransitionMath.BLUR_TAPS / 2).toFloat())};
      float w = exp(-0.5 * ${glslFloat(TransitionMath.BLUR_TAP_SPACING * TransitionMath.BLUR_TAP_SPACING)} * (dx * dx + dy * dy));
      sum += pic(px + vec2(dx, dy) * spacing) * w;
      total += w;
    }
  }
  return sum / total;""".trimIndent()

    private val TWIRL = """
  float turnDegrees = INCOMING ? -${glslFloat(TransitionMath.TWIRL_DEGREES)} * (1.0 - Q) : ${glslFloat(TransitionMath.TWIRL_DEGREES)} * Q;
  float scale = INCOMING ? ${glslFloat(TransitionMath.TWIRL_SMALL_SCALE)} + ${glslFloat(1f - TransitionMath.TWIRL_SMALL_SCALE)} * Q : 1.0 - ${glslFloat(1f - TransitionMath.TWIRL_SMALL_SCALE)} * Q;
  float turnRadians = turnDegrees * ${glslFloat((Math.PI / 180.0).toFloat())};
  vec2 d = px - uSize * 0.5;
  float cosine = cos(turnRadians);
  float sine = sin(turnRadians);
  return pic(uSize * 0.5 + vec2(d.x * cosine + d.y * sine, -d.x * sine + d.y * cosine) / scale);""".trimIndent()

    private val FLIP = """
  float squeeze = INCOMING ? -cos(Q * ${glslFloat(Math.PI.toFloat())}) : cos(Q * ${glslFloat(Math.PI.toFloat())});
  if (squeeze < ${glslFloat(TransitionMath.FLIP_MIN_WIDTH)}) return vec3(0.0);
  return pic(vec2(uSize.x * 0.5 + (px.x - uSize.x * 0.5) / squeeze, px.y));""".trimIndent()

    private val GLITCH = buildString {
        append("  if (uBurst < 0.5) return pic(px);\n  float shift = 0.0;\n")
        for (i in 0 until SLICE_COUNT) append("  if (px.y >= uSlice$i.x && px.y < uSlice$i.y) shift = uSlice$i.z;\n")
        append("  float split = ${glslFloat(TransitionMath.GLITCH_RGB_SPLIT)} * uSize.x;\n  float moved = px.x - shift;\n")
        append("  float red = pic(vec2(moved + split, px.y)).r;\n  float blue = pic(vec2(moved - split, px.y)).b;\n")
        append("  return vec3(red, pic(vec2(moved, px.y)).g, blue);")
    }

    private val PIXELS = """
  float cell = ${glslFloat(TransitionMath.PIXEL_CELL)} * min(uSize.x, uSize.y) * away();
  if (cell < 1.5) return pic(px);
  return pic((floor(px / cell) + 0.5) * cell);""".trimIndent()

    private fun zoom(out: String, into: String) = "float scale = INCOMING ? $into : $out;\n  return pic(uSize * 0.5 + (px - uSize * 0.5) / scale);"

    private val RECIPES: Map<String, String> = mapOf(
        "dissolve" to recipe(PLAIN, BY_Q),
        "dip_black" to recipe("return pic(px) * (1.0 - away());", BY_SWAP),
        "dip_white" to recipe("return mix(pic(px), vec3(1.0), away());", BY_SWAP),
        "blur_through" to recipe(BLUR, BY_SWAP),
        "push_left" to recipe("return pic(INCOMING ? px - vec2(uSize.x * (1.0 - Q), 0.0) : px + vec2(uSize.x * Q, 0.0));", "return px.x / uSize.x >= 1.0 - Q ? 1.0 : 0.0;"),
        "push_right" to recipe("return pic(INCOMING ? px + vec2(uSize.x * (1.0 - Q), 0.0) : px - vec2(uSize.x * Q, 0.0));", "return px.x / uSize.x < Q ? 1.0 : 0.0;"),
        "push_up" to recipe("return pic(INCOMING ? px - vec2(0.0, uSize.y * (1.0 - Q)) : px + vec2(0.0, uSize.y * Q));", "return px.y / uSize.y >= 1.0 - Q ? 1.0 : 0.0;"),
        "push_down" to recipe("return pic(INCOMING ? px + vec2(0.0, uSize.y * (1.0 - Q)) : px - vec2(0.0, uSize.y * Q));", "return px.y / uSize.y < Q ? 1.0 : 0.0;"),
        "cover" to recipe("return pic(INCOMING ? px - vec2(uSize.x * (1.0 - Q), 0.0) : px);", "return px.x / uSize.x >= 1.0 - Q ? 1.0 : 0.0;"),
        "uncover" to recipe("return pic(INCOMING ? px : px + vec2(uSize.x * Q, 0.0));", "return px.x / uSize.x >= 1.0 - Q ? 1.0 : 0.0;"),
        "wipe_right" to recipe(PLAIN, "${sweep("e")}\n  return 1.0 - smoothstep(e - $SOFT, e + $SOFT, px.x / uSize.x);"),
        "wipe_down" to recipe(PLAIN, "${sweep("e")}\n  return 1.0 - smoothstep(e - $SOFT, e + $SOFT, px.y / uSize.y);"),
        "iris" to recipe(
            PLAIN,
            "float soft = $SOFT * min(uSize.x, uSize.y);\n  float radius = Q * (0.5 * length(uSize) + 2.0 * soft) - soft;\n  return 1.0 - smoothstep(radius - soft, radius + soft, length(px - uSize * 0.5));",
        ),
        "clock" to recipe(
            PLAIN,
            "vec2 d = px - uSize * 0.5;\n  float turn = atan(d.x, -d.y) / ${glslFloat((2.0 * Math.PI).toFloat())};\n  if (turn < 0.0) turn += 1.0;\n  ${sweep("e")}\n  return 1.0 - smoothstep(e - $SOFT, e + $SOFT, turn);",
        ),
        "blinds" to recipe(
            PLAIN,
            "float band = px.y / uSize.y * ${glslFloat(TransitionMath.BLINDS)};\n  ${sweep("e")}\n  return 1.0 - smoothstep(e - $SOFT, e + $SOFT, band - floor(band));",
        ),
        "zoom_through" to recipe(
            zoom("1.0 + ${glslFloat(TransitionMath.ZOOM_OUT_SCALE - 1f)} * Q", "${glslFloat(TransitionMath.ZOOM_IN_SCALE)} + ${glslFloat(1f - TransitionMath.ZOOM_IN_SCALE)} * Q"),
            BY_Q,
        ),
        "twirl" to recipe(TWIRL, BY_Q),
        "flip" to recipe(FLIP, BY_SWAP),
        "glitch_cut" to recipe(GLITCH, BY_SWAP),
        "pixel_swap" to recipe(PIXELS, BY_SWAP),
    )
}
