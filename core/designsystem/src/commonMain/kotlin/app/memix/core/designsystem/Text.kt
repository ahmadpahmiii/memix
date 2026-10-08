package app.memix.core.designsystem

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase

@Composable
fun Text(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MemixColors.text,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    // MiddleEllipsis keeps a file name's extension visible; it needs maxLines = 1.
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign == null) style.copy(color = color) else style.copy(color = color, textAlign = textAlign),
        overflow = overflow,
        maxLines = maxLines,
    )
}

/** Anton display text. Strings stay sentence case in resources; the uppercase comes from here, per locale. */
@Composable
fun DisplayText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MemixTheme.type.display,
    color: Color = MemixColors.text,
    textAlign: TextAlign? = null,
) {
    Text(text.toUpperCase(Locale.current), style, modifier, color, textAlign)
}
