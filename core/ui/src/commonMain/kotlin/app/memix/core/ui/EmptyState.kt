package app.memix.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.memix.core.designsystem.DisplayText
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text

/** Says why a screen is empty and, when there is one, the action that fills it. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space10),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
    ) {
        DisplayText(title, style = MemixTheme.type.displayXl, textAlign = TextAlign.Center)
        Text(body, MemixTheme.type.body, color = MemixColors.textSecondary, textAlign = TextAlign.Center)
        if (action != null) Column(Modifier.padding(top = MemixSpacing.space3)) { action() }
    }
}
