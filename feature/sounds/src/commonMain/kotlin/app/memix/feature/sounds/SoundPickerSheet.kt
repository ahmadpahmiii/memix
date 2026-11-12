package app.memix.feature.sounds

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.FadingToast
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.Sheet
import app.memix.core.designsystem.component.SoundFavoriteHeart
import app.memix.core.designsystem.component.SoundRowLook
import app.memix.core.designsystem.component.Toast
import kotlinx.coroutines.delay
import memix.feature.sounds.generated.resources.Res
import memix.feature.sounds.generated.resources.picker_back_a11y
import memix.feature.sounds.generated.resources.sound_detail_add_to_video
import memix.feature.sounds.generated.resources.sound_detail_report
import memix.feature.sounds.generated.resources.sound_report_back_a11y
import org.jetbrains.compose.resources.stringResource

/**
 * The sound picker in the video editor (spec P3-05): one Sheet whose panes swap inside it, the list (search, the five
 * tabs, rows with a blue Add block) and a sound's details with "Add to video". No chips, so a quick pick stays quick.
 * Stateless: the picker's [SoundsViewModel] ([SoundsMode.PICKER]) one level up owns [state], and the editor lands the
 * sound it hands over ([SoundsViewModel.picks]).
 *
 * [visible] is the editor's: the picker stops its sound and starts fresh when it turns false. System back and Escape step
 * back a pane first; the close button, the scrim and a drag down close the sheet ([onDismiss]). [onFreeUpSpace] opens the
 * phone's storage screen from the full-phone strip (the app says when there is none, [SoundsUiState.canFreeUpSpace]).
 */
@Composable
fun SoundPickerSheet(
    visible: Boolean,
    title: String,
    state: State<SoundsUiState>,
    clock: PlaybackClock,
    onIntent: (SoundsIntent) -> Unit,
    onDismiss: () -> Unit,
    onFreeUpSpace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnIntent by rememberUpdatedState(onIntent)
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(visible) {
        if (visible) return@LaunchedEffect
        // Closing stops the sound and the downloads at once; the list and the pane stay put while the sheet slides out, and
        // reset once it is gone (a reopening before that cancels the reset).
        searchFocused = false
        currentOnIntent(SoundsIntent.PickerClosed)
        delay(SHEET_EXIT_MILLIS)
        currentOnIntent(SoundsIntent.PickerExited)
    }
    val details by remember(state) { derivedStateOf { state.value.details } }
    val reportOpen by remember(state) { derivedStateOf { state.value.reportOpen } }
    val language = LocalLocale.current.language
    val haptics = LocalHapticFeedback.current
    val titleFocus = remember { FocusRequester() }
    ReportTitleFocus(reportOpen, titleFocus)
    val name = details?.title?.inLanguage(language)
    Box(modifier.fillMaxSize()) {
        Sheet(
            visible = visible,
            onDismiss = onDismiss,
            title = if (reportOpen) stringResource(Res.string.sound_detail_report) else name ?: title,
            opensAtHalfHeight = true,
            minOpeningHeight = PickerMinHeight,
            grow = searchFocused,
            onBack = {
                when {
                    // Escape or back with the search box focused only puts the keyboard away.
                    searchFocused -> focusManager.clearFocus()
                    reportOpen -> onIntent(SoundsIntent.CloseReport)
                    details != null -> onIntent(SoundsIntent.CloseDetails)
                    else -> onDismiss()
                }
            },
            titleLeading = details?.let {
                if (reportOpen) {
                    { IconButton(MemixIcons.ArrowBack, stringResource(Res.string.sound_report_back_a11y), { onIntent(SoundsIntent.CloseReport) }) }
                } else {
                    { IconButton(MemixIcons.ArrowBack, stringResource(Res.string.picker_back_a11y), { onIntent(SoundsIntent.CloseDetails) }) }
                }
            },
            titleMaxLines = TITLE_MAX_LINES,
            titleFocus = titleFocus,
            titleTrailing = details?.takeUnless { reportOpen }?.let { shown ->
                {
                    val isFavorite by remember(state, shown.id) { derivedStateOf { shown.id in state.value.favoriteIds } }
                    SoundFavoriteHeart(name.orEmpty(), isFavorite, { on ->
                        if (on) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onIntent(SoundsIntent.ToggleFavorite(shown.id))
                    })
                }
            },
        ) {
            PickerPanes(state, clock, onIntent, onFreeUpSpace) { searchFocused = it }
        }
        val toast by remember(state) { derivedStateOf { state.value.toast.takeIf { visible } } }
        ReportSentHaptic(toast)
        FadingToast(toast, Modifier.align(Alignment.BottomCenter).padding(MemixSpacing.space4)) { shown ->
            Toast(soundsToastText(shown))
        }
    }
}

@Composable
private fun ColumnScope.PickerPanes(
    state: State<SoundsUiState>,
    clock: PlaybackClock,
    onIntent: (SoundsIntent) -> Unit,
    onFreeUpSpace: () -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
) {
    // After the first frame, so opening the sheet never waits for the catalog or the player.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        onIntent(SoundsIntent.ScreenShown)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onIntent(SoundsIntent.AppStopped) }
    // Kept above the panes, so the list comes back where it was after a visit to the details.
    val listState = rememberLazyListState()
    val field = remember { TextFieldState(state.value.query) }
    val pane by remember(state) {
        derivedStateOf {
            when {
                state.value.details == null -> PickerPane.LIST
                state.value.reportOpen -> PickerPane.REPORT
                else -> PickerPane.DETAILS
            }
        }
    }
    // Held above the panes: the details pane leaves composition while the Report pane is up, and focus returns to its row.
    val reportRow = remember { FocusRequester() }
    val shownDetails by remember(state) { derivedStateOf { state.value.details?.id } }
    val reported by remember(state) { derivedStateOf { shownDetails != null && shownDetails in state.value.reportedIds } }
    ReportFocusReturn(pane == PickerPane.REPORT, reported, reportRow)
    val millis = if (MemixTheme.reduceMotion) MemixMotion.durationPress else MemixMotion.durationSheet
    Crossfade(pane, Modifier.weight(1f, fill = false), animationSpec = tween(millis), label = "picker-pane") { shown ->
        when (shown) {
            PickerPane.LIST -> ListPane(state, clock, listState, field, onIntent, onFreeUpSpace, onSearchFocusChange)
            PickerPane.DETAILS -> DetailsPane(state, clock, reportRow, onIntent)
            PickerPane.REPORT -> ReportHost(state, onIntent)
        }
    }
}

/** The picker's panes (spec P3-05): the list, a sound's details, and the Report pane (P3-10) that opens from the details. */
private enum class PickerPane { LIST, DETAILS, REPORT }

@Composable
private fun ReportHost(stateHolder: State<SoundsUiState>, onIntent: (SoundsIntent) -> Unit) {
    val state by stateHolder
    val details = state.details ?: return
    val name = details.title.inLanguage(LocalLocale.current.language)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4)) {
        ReportPane(name, state.report, onIntent)
    }
}

@Composable
private fun ListPane(
    stateHolder: State<SoundsUiState>,
    clock: PlaybackClock,
    listState: LazyListState,
    field: TextFieldState,
    onIntent: (SoundsIntent) -> Unit,
    onFreeUpSpace: () -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
) {
    val state by stateHolder
    val language = LocalLocale.current.language
    val searchFocus = remember { FocusRequester() }
    val allChipFocus = remember { FocusRequester() }
    SearchTextSync(field, state.query, listState, onIntent)
    LoadMoreWhenNearEnd(listState, state.content, onIntent)
    val fade = rememberTabFade(state.tab)
    val position = rememberPlaybackPosition(clock, state.activeId, isPlaying = state.playingId != null)
    val focusManager = LocalFocusManager.current
    val refreshDistancePx = with(LocalDensity.current) { (MemixSize.touchTarget * 2).toPx() }
    val currentOnIntent by rememberUpdatedState(onIntent)
    val scrollEffects = remember(refreshDistancePx) {
        PullToRefresh(refreshDistancePx, onRefresh = { currentOnIntent(SoundsIntent.Refresh) }, onUserScroll = { focusManager.clearFocus() })
    }
    // At a large font the pinned search box would take too much of the sheet: it scrolls with the list instead (spec P3-05 → Height).
    val searchScrolls = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    Column(Modifier.fullBleed(SHEET_SIDE_PADDING)) {
        SearchAndTabs(state.tab, field, searchFocus, onSearchFocusChange, onIntent, background = MemixColors.surface, showSearch = !searchScrolls)
        LazyColumn(
            Modifier.weight(1f, fill = false).nestedScroll(scrollEffects),
            state = listState,
            contentPadding = PaddingValues(bottom = MemixSpacing.space2),
        ) {
            if (searchScrolls) {
                item(key = "search", contentType = "search") { SearchField(state.tab, field, searchFocus, onSearchFocusChange) }
            }
            soundsBody(state, language, searchFocus, allChipFocus, fade, position, onIntent, picker = true, onFreeUpSpace = onFreeUpSpace)
        }
    }
}

@Composable
private fun DetailsPane(stateHolder: State<SoundsUiState>, clock: PlaybackClock, reportRow: FocusRequester, onIntent: (SoundsIntent) -> Unit) {
    val state by stateHolder
    val details = state.details ?: return
    val language = LocalLocale.current.language
    val position = rememberPlaybackPosition(clock, state.activeId, isPlaying = state.playingId != null)
    val look = state.lookOf(details.id)
    val name = details.title.inLanguage(language)
    val reportEntry = reportEntryOf(look, details.id in state.reportedIds, reportRow, onIntent)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4)) {
        DetailsBody(
            details = details,
            name = name,
            playback = state.playbackOf(details.id),
            peaks = state.waveforms[details.id],
            look = look,
            downloadProgress = state.downloadProgressOf(details.id),
            onPhone = state.onPhoneOf(details.id),
            position = position,
            language = language,
            onIntent = onIntent,
            reportEntry = reportEntry,
        )
        // A sound that is not on the phone with no way to get it, or dropped from the catalog, has nothing to add (P3-03's line says why).
        if (look != SoundRowLook.OFFLINE_NOT_SAVED && look != SoundRowLook.UNAVAILABLE) {
            Button(
                stringResource(Res.string.sound_detail_add_to_video),
                { onIntent(SoundsIntent.AddFromDetails) },
                Modifier.fillMaxWidth(),
                variant = ButtonVariant.Primary,
                busy = look == SoundRowLook.DOWNLOADING || look == SoundRowLook.FAILED_STORAGE,
            )
        }
        if (!reportScrollsWithContent()) reportEntry()
    }
}

/**
 * Lets the content use the sheet's side padding too, so the list's rows, strips and tabs line up exactly as they do on
 * the Sounds tab, which has no such padding. The width grows by [inset] on each side and the content is placed that far left.
 */
private fun Modifier.fullBleed(inset: Dp): Modifier = layout { measurable, constraints ->
    val extraPx = inset.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extraPx, maxWidth = constraints.maxWidth + extraPx))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extraPx / 2, 0) }
}

// The Sheet's own side padding (Sheet.kt: start and end `space-4`).
private val SHEET_SIDE_PADDING = MemixSpacing.space4

// The sheet's slide-out (`duration-sheet`) plus a frame, after which the visit is reset.
private const val SHEET_EXIT_MILLIS = MemixMotion.durationSheet + 50L

// One row as the list lays it out: the 48 dp touch target, the row's own padding and the list's gap (about 70 dp measured in the render).
private val PICKER_ROW_HEIGHT: Dp = MemixSize.touchTarget + MemixSpacing.space4 + MemixSpacing.space2

/**
 * The least height the sheet opens at (spec P3-05 → Height): the frame (grabber, title row, gaps, bottom padding), the
 * pinned search and tabs, and three rows, so the Add block of a row is always in reach on a short phone (about 436 dp; the spec's 364 dp counted 48 dp rows, and a row measures about 70 dp in the render).
 */
private val PickerMinHeight: Dp =
    MemixSpacing.space2 + MemixSpacing.space1 + // grabber and its top padding
        MemixSpacing.space4 + MemixSize.touchTarget + MemixSpacing.space4 + // gap, title row, gap
        MemixSpacing.space2 + MemixSize.touchTarget + // search box
        MemixSize.touchTarget + // tabs
        PICKER_ROW_HEIGHT * PICKER_MIN_ROWS +
        MemixSpacing.space6 // bottom padding

private const val PICKER_MIN_ROWS = 3

