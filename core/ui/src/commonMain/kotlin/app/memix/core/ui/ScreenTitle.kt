package app.memix.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text

/** The `title-l` heading at the top of each browse tab. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        MemixTheme.type.titleL,
        modifier.padding(horizontal = MemixSpacing.space4).padding(top = MemixSpacing.space5).semantics { heading() },
    )
}
