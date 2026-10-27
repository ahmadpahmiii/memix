package app.memix.feature.templates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.memix.core.ui.EmptyState
import app.memix.core.ui.ScreenTitle
import memix.feature.templates.generated.resources.Res
import memix.feature.templates.generated.resources.templates_empty_body
import memix.feature.templates.generated.resources.templates_empty_title
import memix.feature.templates.generated.resources.templates_title
import org.jetbrains.compose.resources.stringResource

// Until the catalog is live the screen says so; search, tabs and chips arrive with the data.
@Composable
fun TemplatesScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        ScreenTitle(stringResource(Res.string.templates_title))
        Box(Modifier.weight(1f).verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            EmptyState(stringResource(Res.string.templates_empty_title), stringResource(Res.string.templates_empty_body))
        }
    }
}
