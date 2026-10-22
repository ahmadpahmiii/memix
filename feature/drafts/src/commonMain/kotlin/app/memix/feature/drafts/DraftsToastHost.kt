package app.memix.feature.drafts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import app.memix.core.designsystem.component.FadingToast
import app.memix.core.designsystem.component.Toast
import app.memix.core.designsystem.component.ToastAction
import kotlinx.coroutines.delay
import memix.feature.drafts.generated.resources.Res
import memix.feature.drafts.generated.resources.drafts_cant_open
import memix.feature.drafts.generated.resources.drafts_deleted
import memix.feature.drafts.generated.resources.drafts_duplicated
import memix.feature.drafts.generated.resources.drafts_undo
import org.jetbrains.compose.resources.stringResource

/**
 * The Drafts tab's toast: "Draft deleted." with Undo for as long as the delete can be undone, "Draft duplicated.", and
 * "This draft can't be opened" (also after an editor couldn't open one, wherever the user is). The app places it above
 * the bottom navigation, centered, `space-4` from the edges.
 */
@Composable
fun DraftsToastHost(state: DraftsUiState, onIntent: (DraftsIntent) -> Unit, modifier: Modifier = Modifier) {
    val toast = state.toast
    if (toast != null) ToastTimer(toast, onIntent)
    FadingToast(toast, modifier) { shown ->
        when (shown) {
            is DraftsToast.Deleted -> Toast(
                stringResource(Res.string.drafts_deleted),
                action = ToastAction(stringResource(Res.string.drafts_undo)) { onIntent(DraftsIntent.Undo(shown.draftId)) },
            )
            is DraftsToast.Duplicated -> Toast(stringResource(Res.string.drafts_duplicated))
            is DraftsToast.CantOpen -> Toast(stringResource(Res.string.drafts_cant_open))
        }
    }
}

/**
 * Hides a toast after its time: 2 s for what happened, 4 s for an explanation, or longer when the user's "Time to take
 * action" setting asks for it. The undo toast has no timer here: it goes when its delete can't be undone any more.
 */
@Composable
private fun ToastTimer(toast: DraftsToast, onIntent: (DraftsIntent) -> Unit) {
    val accessibilityManager = LocalAccessibilityManager.current
    val currentOnIntent by rememberUpdatedState(onIntent)
    LaunchedEffect(toast.serial) {
        val baseMillis = when (toast) {
            is DraftsToast.Deleted -> return@LaunchedEffect
            is DraftsToast.Duplicated -> WHAT_HAPPENED_MILLIS
            is DraftsToast.CantOpen -> EXPLANATION_MILLIS
        }
        delay(accessibilityManager?.calculateRecommendedTimeoutMillis(baseMillis, containsText = true) ?: baseMillis)
        currentOnIntent(DraftsIntent.ToastTimedOut(toast.serial))
    }
}

private const val WHAT_HAPPENED_MILLIS = 2_000L
private const val EXPLANATION_MILLIS = 4_000L
