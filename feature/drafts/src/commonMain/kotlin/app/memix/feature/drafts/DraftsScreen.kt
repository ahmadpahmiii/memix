package app.memix.feature.drafts

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.memix.core.model.project.ProjectType
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.ui.EmptyState
import app.memix.core.ui.ScreenTitle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import memix.feature.drafts.generated.resources.Res
import memix.feature.drafts.generated.resources.drafts_empty_action
import memix.feature.drafts.generated.resources.drafts_empty_body
import memix.feature.drafts.generated.resources.drafts_empty_title
import memix.feature.drafts.generated.resources.drafts_load_error
import memix.feature.drafts.generated.resources.drafts_title
import memix.feature.drafts.generated.resources.drafts_try_again
import org.jetbrains.compose.resources.stringResource

/**
 * The Drafts tab (spec docs/ux/specs/P1-14-drafts.md): "Your drafts", then the drafts newest edit first, the empty
 * state, or the load error. Its sheets ([DraftsSheets]) and toast ([DraftsToastHost]) are placed by the app, over the
 * navigation. [onMakeMeme] opens Create; [onOpenDraft] opens a draft in its editor, by its kind.
 */
@Composable
fun DraftsScreen(
    state: DraftsUiState,
    onIntent: (DraftsIntent) -> Unit,
    onMakeMeme: () -> Unit,
    onOpenDraft: (draftId: String, type: ProjectType) -> Unit,
    modifier: Modifier = Modifier,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) { onIntent(DraftsIntent.Shown) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onIntent(DraftsIntent.Hidden) }
    OpenDraftWhenAsked(state.openDraftId, state.openDraftType, onOpenDraft, onIntent)
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        ScreenTitle(stringResource(Res.string.drafts_title))
        when (val list = state.list) {
            // Rare (the database is on the phone) and short: only the title, no spinner (spec → States).
            DraftsList.Loading -> Unit
            DraftsList.LoadFailed -> LoadError(onTryAgain = { onIntent(DraftsIntent.TryAgain) }, Modifier.weight(1f))
            is DraftsList.Loaded ->
                if (list.rows.isEmpty()) {
                    NoDrafts(onMakeMeme, Modifier.weight(1f))
                } else {
                    DraftList(list.rows, state.thumbnails, state.focus, onIntent, Modifier.weight(1f))
                }
        }
    }
}

@Composable
private fun DraftList(
    rows: ImmutableList<DraftRowUi>,
    thumbnails: DraftThumbnailImages,
    focus: FocusRequestUi?,
    onIntent: (DraftsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val requesters = remember { HashMap<String, FocusRequester>() }
    FocusRows(focus, rows, listState, requesters, onIntent)
    val undoWindowMillis = undoWindowMillis()
    val reduceMotion = MemixTheme.reduceMotion
    LazyColumn(modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = MemixSpacing.space2)) {
        items(rows, key = { it.id }, contentType = { DRAFT_ROW }) { row ->
            val requester = remember { FocusRequester() }
            DisposableEffect(row.id) {
                requesters[row.id] = requester
                onDispose { if (requesters[row.id] === requester) requesters.remove(row.id) }
            }
            DraftRow(
                row,
                thumbnails,
                undoWindowMillis,
                requester,
                onIntent,
                // A deleted row collapses and the rows below slide up; an undone or new one fades in as the rows part.
                Modifier.animateItem(
                    fadeInSpec = if (reduceMotion) null else tween(MemixMotion.durationSheet),
                    placementSpec = if (reduceMotion) null else tween(MemixMotion.durationSheet),
                    fadeOutSpec = if (reduceMotion) null else tween(MemixMotion.durationSheet),
                ),
            )
        }
    }
}

/**
 * Moves focus to the asked row: after Rename, Delete (the next row), Undo, and coming back from an editor, first
 * scrolling to the top when the draft just edited is now first. A row off screen is scrolled into view first.
 */
@Composable
private fun FocusRows(
    focus: FocusRequestUi?,
    rows: ImmutableList<DraftRowUi>,
    listState: LazyListState,
    requesters: Map<String, FocusRequester>,
    onIntent: (DraftsIntent) -> Unit,
) {
    val currentRows by rememberUpdatedState(rows)
    LaunchedEffect(focus?.serial) {
        val request = focus ?: return@LaunchedEffect
        if (request.scrollToTop) listState.scrollToItem(0)
        // An undone delete's row arrives with the next list update.
        val index = withTimeoutOrNull(FOCUS_WAIT_MILLIS) {
            snapshotFlow { currentRows.indexOfFirst { it.id == request.draftId } }.first { it >= 0 }
        }
        if (index != null) {
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
            // A row scrolled into view is composed in the next frames.
            repeat(FOCUS_WAIT_FRAMES) { if (request.draftId !in requesters) withFrameNanos { } }
            requesters[request.draftId]?.requestFocus()
        }
        onIntent(DraftsIntent.FocusDone(request.serial))
    }
}

/** Navigates once per request; the ViewModel decides (a draft from a newer app explains instead). */
@Composable
private fun OpenDraftWhenAsked(draftId: String?, type: ProjectType?, onOpenDraft: (String, ProjectType) -> Unit, onIntent: (DraftsIntent) -> Unit) {
    val currentOnOpenDraft by rememberUpdatedState(onOpenDraft)
    val currentOnIntent by rememberUpdatedState(onIntent)
    LaunchedEffect(draftId) {
        if (draftId == null || type == null) return@LaunchedEffect
        currentOnOpenDraft(draftId, type)
        currentOnIntent(DraftsIntent.DraftOpened)
    }
}

/** P0-05's empty state; the button is secondary: Create stays the blue action. */
@Composable
private fun NoDrafts(onMakeMeme: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(Res.string.drafts_empty_title),
            body = stringResource(Res.string.drafts_empty_body),
            action = { Button(stringResource(Res.string.drafts_empty_action), onMakeMeme) },
        )
    }
}

@Composable
private fun LoadError(onTryAgain: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space10),
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.drafts_load_error), MemixTheme.type.body, color = MemixColors.textSecondary, textAlign = TextAlign.Center)
            Button(stringResource(Res.string.drafts_try_again), onTryAgain)
        }
    }
}

/** The undo toast's time: 8 s, or longer when the user's "Time to take action" setting asks for it (it has a button). */
@Composable
private fun undoWindowMillis(): Long {
    val accessibilityManager = LocalAccessibilityManager.current
    return accessibilityManager?.calculateRecommendedTimeoutMillis(UNDO_WINDOW_MILLIS, containsText = true, containsControls = true)
        ?: UNDO_WINDOW_MILLIS
}

private const val DRAFT_ROW = "draft"
private const val UNDO_WINDOW_MILLIS = 8_000L
private const val FOCUS_WAIT_MILLIS = 1_000L
private const val FOCUS_WAIT_FRAMES = 3
