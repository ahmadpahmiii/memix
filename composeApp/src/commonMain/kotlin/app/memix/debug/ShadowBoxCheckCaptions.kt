package app.memix.debug

import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.BoxCorners
import app.memix.core.model.project.CaptionBox
import app.memix.core.model.project.CaptionShadow
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.ShadowSoftness
import app.memix.core.model.project.Transform

/** One caption of the P2-11 checks: the same words, style and place in the photo and video scenes. */
internal class ShadowBoxCheckCaption(val id: String, val text: String, val style: CaptionStyle, val transform: Transform)

/**
 * The captions of `photo-text-shadow-box` and the video's `text-shadow-check` (spec P2-11 → QA compares → Measured): all five
 * fonts, Hindi and emoji, accented Latin, a 30° turn, a three-line wrap and one against the canvas edge, with shadow only, box
 * only and both, in several colors, softness steps and corners. Compare the screen, the render hook and the export frame.
 */
internal object ShadowBoxCheckCaptions {
    private val WHITE = ArgbColor(0xFFFFFFFF)
    private val BLACK = ArgbColor(0xFF000000)
    private val YELLOW = ArgbColor(0xFFFFD60A)
    private val RED = ArgbColor(0xFFFF3B30)
    private val LIGHT_BLUE = ArgbColor(0xFF64D2FF)
    private val PURPLE = ArgbColor(0xFFAF52DE)

    val all: List<ShadowBoxCheckCaption> = listOf(
        // Hindi and emoji, Classic, white with a black outline: a soft shadow and a rounded black box.
        ShadowBoxCheckCaption(
            "sb-classic", "जब BUILD PASS हो 😂",
            CaptionStyle("anton", WHITE, BLACK, CaptionShadow(BLACK, ShadowSoftness.SOFT), CaptionBox(BLACK, BoxCorners.ROUNDED)),
            Transform(0.5f, 0.1f),
        ),
        // Accented Latin, Modern, no outline: a pill box only.
        ShadowBoxCheckCaption(
            "sb-modern", "Ação rápida, ¿sí? ¡Ñandú!",
            CaptionStyle("poppins", BLACK, null, box = CaptionBox(YELLOW, BoxCorners.PILL)),
            Transform(0.5f, 0.24f, scale = 0.7f),
        ),
        // Serif: a blurry shadow only.
        ShadowBoxCheckCaption(
            "sb-serif", "THE END",
            CaptionStyle("rozha", WHITE, null, shadow = CaptionShadow(BLACK, ShadowSoftness.BLURRY)),
            Transform(0.5f, 0.35f, scale = 1.4f),
        ),
        // Handwritten with Devanagari: a sharp shadow on a square box.
        ShadowBoxCheckCaption(
            "sb-handwritten", "me: हाँ, sure",
            CaptionStyle("kalam", LIGHT_BLUE, BLACK, CaptionShadow(RED, ShadowSoftness.SHARP), CaptionBox(WHITE, BoxCorners.SQUARE)),
            Transform(0.5f, 0.46f),
        ),
        // Bubbly, two lines, turned 30°: both, so the shadow turning with the caption shows.
        ShadowBoxCheckCaption(
            "sb-bubbly", "BUBBLY\nTWO LINES",
            CaptionStyle("baloo2", WHITE, PURPLE, CaptionShadow(BLACK, ShadowSoftness.SOFT), CaptionBox(BLACK, BoxCorners.ROUNDED)),
            Transform(0.5f, 0.62f, rotationDegrees = 30f),
        ),
        // Wraps to three lines at 100% on a 9:16 canvas: the box is one block, a little wider than the text.
        ShadowBoxCheckCaption(
            "sb-wrap", "WHEN THE BUILD FINALLY PASSES ON THE FIRST TRY AFTER THREE HOURS",
            CaptionStyle("anton", BLACK, null, CaptionShadow(BLACK, ShadowSoftness.SOFT), CaptionBox(WHITE, BoxCorners.ROUNDED)),
            Transform(0.5f, 0.86f),
        ),
        // Against the right edge: the box and the shadow clip there like the text.
        ShadowBoxCheckCaption(
            "sb-edge", "EDGE",
            CaptionStyle("anton", WHITE, BLACK, CaptionShadow(BLACK, ShadowSoftness.SOFT), CaptionBox(RED, BoxCorners.PILL)),
            Transform(0.97f, 0.95f),
        ),
    )
}
