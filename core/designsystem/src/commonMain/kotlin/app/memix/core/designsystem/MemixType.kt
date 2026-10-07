package app.memix.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import org.jetbrains.compose.resources.Font

@Immutable
class MemixType(
    val displayXl: TextStyle,
    val display: TextStyle,
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
    val display = FontFamily(Font(Res.font.anton_regular, FontWeight.Normal))
    val sans = FontFamily(
        Font(Res.font.space_grotesk_regular, FontWeight.Normal),
        Font(Res.font.space_grotesk_medium, FontWeight.Medium),
        Font(Res.font.space_grotesk_bold, FontWeight.Bold),
    )
    val mono = FontFamily(Font(Res.font.space_mono_regular, FontWeight.Normal))
    return MemixType(
        displayXl = style(display, FontWeight.Normal, 40.sp, 44.sp),
        display = style(display, FontWeight.Normal, 28.sp, 30.sp),
        memeCaption = style(display, FontWeight.Normal, 36.sp, 40.sp),
        titleL = style(sans, FontWeight.Bold, 22.sp, 28.sp),
        title = style(sans, FontWeight.Bold, 18.sp, 24.sp),
        body = style(sans, FontWeight.Normal, 15.sp, 22.sp),
        bodyStrong = style(sans, FontWeight.Medium, 15.sp, 22.sp),
        label = style(sans, FontWeight.Medium, 13.sp, 16.sp),
        caption = style(sans, FontWeight.Medium, 11.sp, 14.sp),
        timecode = style(mono, FontWeight.Normal, 12.sp, 16.sp),
    )
}

private fun style(family: FontFamily, weight: FontWeight, size: TextUnit, lineHeight: TextUnit) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    color = MemixColors.text,
)
