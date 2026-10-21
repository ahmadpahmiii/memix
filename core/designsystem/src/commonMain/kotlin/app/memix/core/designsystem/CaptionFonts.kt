package app.memix.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import memix.core.designsystem.generated.resources.Res
import memix.core.designsystem.generated.resources.anton_regular
import memix.core.designsystem.generated.resources.baloo2_extrabold
import memix.core.designsystem.generated.resources.kalam_bold
import memix.core.designsystem.generated.resources.poppins_bold
import memix.core.designsystem.generated.resources.rozha_one_regular
import memix.core.designsystem.generated.resources.teko_bold
import org.jetbrains.compose.resources.Font

/*
 * The caption fonts (spec P1-10 → Fonts) live with the interface fonts, so the video engine's caption renderer and the
 * Style panel's tiles load the same files. Which font is which (ids, files, fallback) is CaptionCatalog in :core:model.
 */

/**
 * The family a FontTile writes caption font [fontId]'s name in. Classic in Hindi is Teko Bold, the face that draws
 * Classic's Devanagari; an unknown id gets Classic.
 */
@Composable
fun captionFontFamily(fontId: String): FontFamily = when (fontId) {
    "poppins" -> FontFamily(Font(Res.font.poppins_bold, FontWeight.Bold))
    "baloo2" -> FontFamily(Font(Res.font.baloo2_extrabold, FontWeight.ExtraBold))
    "kalam" -> FontFamily(Font(Res.font.kalam_bold, FontWeight.Bold))
    "rozha" -> FontFamily(Font(Res.font.rozha_one_regular, FontWeight.Normal))
    else -> if (usesHindiDisplayFace()) FontFamily(Font(Res.font.teko_bold, FontWeight.Bold)) else FontFamily(Font(Res.font.anton_regular, FontWeight.Normal))
}

/** The bytes of caption font file [fileName] (a CaptionCatalog file name), for the engine's renderer. Reads a file. */
suspend fun readCaptionFontFile(fileName: String): ByteArray = Res.readBytes("font/$fileName")
