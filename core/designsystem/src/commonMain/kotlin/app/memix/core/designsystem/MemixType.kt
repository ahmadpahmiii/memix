package app.memix.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import memix.core.designsystem.generated.resources.Res
import memix.core.designsystem.generated.resources.anton_regular
import memix.core.designsystem.generated.resources.space_grotesk_bold
import memix.core.designsystem.generated.resources.space_grotesk_medium
import memix.core.designsystem.generated.resources.space_grotesk_regular
import memix.core.designsystem.generated.resources.space_mono_regular
import memix.core.designsystem.generated.resources.teko_bold
import org.jetbrains.compose.resources.Font

/**
 * The type roles of `design/tokens.json` → type. In Hindi, [displayXl] and [display] use the Hindi display face
 * (Teko Bold), because Anton has no Devanagari; every other role is the same in all five languages.
 */
@Immutable
class MemixType(
    val displayXl: TextStyle,
    val display: TextStyle,
    /** The name "Memix" (Home top bar): always Anton, in every language, unlike [display]. */
    val wordmark: TextStyle,
    val memeCaption: TextStyle,
    val titleL: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val timecode: TextStyle,
)

@Composable
internal fun memixType(): MemixType {
    val anton = FontFamily(Font(Res.font.anton_regular, FontWeight.Normal))
    val sans = FontFamily(
        Font(Res.font.space_grotesk_regular, FontWeight.Normal),
        Font(Res.font.space_grotesk_medium, FontWeight.Medium),
        Font(Res.font.space_grotesk_bold, FontWeight.Bold),
    )
    val mono = FontFamily(Font(Res.font.space_mono_regular, FontWeight.Normal))
    val antonDisplay = style(anton, FontWeight.Normal, 28.sp, 30.sp)
    // Loaded only in Hindi. The whole string is set in Teko, Latin letters included, so a headline never mixes two
    // display faces (DESIGN_SYSTEM → Hindi display face).
    val hindiDisplayFace = if (usesHindiDisplayFace()) FontFamily(Font(Res.font.teko_bold, FontWeight.Bold)) else null
    return MemixType(
        displayXl = if (hindiDisplayFace == null) style(anton, FontWeight.Normal, 40.sp, 44.sp) else hindiDisplay(hindiDisplayFace, 40.sp, 60.sp),
        display = if (hindiDisplayFace == null) antonDisplay else hindiDisplay(hindiDisplayFace, 28.sp, 44.sp),
        wordmark = antonDisplay,
        memeCaption = style(anton, FontWeight.Normal, 36.sp, 40.sp),
        titleL = style(sans, FontWeight.Bold, 22.sp, 28.sp),
        title = style(sans, FontWeight.Bold, 18.sp, 24.sp),
        body = style(sans, FontWeight.Normal, 15.sp, 22.sp),
        bodyStrong = style(sans, FontWeight.Medium, 15.sp, 22.sp),
        label = style(sans, FontWeight.Medium, 13.sp, 16.sp),
        caption = style(sans, FontWeight.Medium, 11.sp, 14.sp),
        timecode = style(mono, FontWeight.Normal, 12.sp, 16.sp),
    )
}

/**
 * The display roles follow the app language (the per-app language, or the system language the app resolved to), read
 * from the composition so a language change restyles them on the next composition.
 */
@Composable
internal fun usesHindiDisplayFace(): Boolean = LocalLocale.current.language == HINDI_LANGUAGE

/**
 * Teko Bold with no letter spacing, because tracking breaks the headline bar that joins Devanagari letters. The line
 * height is Teko's own line spacing (1.433 em: candrabindu and the vocalic-rr sign reach its full ascent and descent),
 * rounded up to the 4 dp grid, so no mark is cut off: 40 sp → 60, 28 sp → 44.
 */
private fun hindiDisplay(family: FontFamily, size: TextUnit, lineHeight: TextUnit) = style(family, FontWeight.Bold, size, lineHeight)

private fun style(family: FontFamily, weight: FontWeight, size: TextUnit, lineHeight: TextUnit) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    color = MemixColors.text,
)

private const val HINDI_LANGUAGE = "hi"
