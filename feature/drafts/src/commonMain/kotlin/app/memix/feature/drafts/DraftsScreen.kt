package app.memix.feature.drafts

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
import app.memix.core.designsystem.component.Button
import app.memix.core.ui.EmptyState
import app.memix.core.ui.ScreenTitle
import memix.feature.drafts.generated.resources.Res
import memix.feature.drafts.generated.resources.drafts_empty_action
import memix.feature.drafts.generated.resources.drafts_empty_body
import memix.feature.drafts.generated.resources.drafts_empty_title
import memix.feature.drafts.generated.resources.drafts_title
import org.jetbrains.compose.resources.stringResource

/** Drafts can't be saved before P1-01, so this empty state is the real one. The button is secondary: Create stays the blue action. */
@Composable
fun DraftsScreen(onMakeMeme: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        ScreenTitle(stringResource(Res.string.drafts_title))
        Box(Modifier.weight(1f).verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            EmptyState(
                title = stringResource(Res.string.drafts_empty_title),
                body = stringResource(Res.string.drafts_empty_body),
                action = { Button(stringResource(Res.string.drafts_empty_action), onMakeMeme) },
            )
        }
    }
}
