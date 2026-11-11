package app.memix.feature.sounds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.component.FadingToast
import app.memix.core.designsystem.component.SoundProgress
import app.memix.core.designsystem.component.SoundRow
import app.memix.core.designsystem.component.SoundRowLook
import app.memix.core.designsystem.component.Tabs
import app.memix.core.designsystem.component.Toast
import app.memix.core.ui.ScreenTitle
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import memix.feature.sounds.generated.resources.Res
import memix.feature.sounds.generated.resources.sounds_empty_favorites_body
import memix.feature.sounds.generated.resources.sounds_results_a11y
import memix.feature.sounds.generated.resources.sounds_search_hint
import memix.feature.sounds.generated.resources.sounds_search_hint_favorites
import memix.feature.sounds.generated.resources.sounds_search_hint_mine
import memix.feature.sounds.generated.resources.sounds_section_favorites
import memix.feature.sounds.generated.resources.sounds_section_recent
import memix.feature.sounds.generated.resources.sounds_starter_heading
import memix.feature.sounds.generated.resources.sounds_tab_favorites
import memix.feature.sounds.generated.resources.sounds_tab_global
import memix.feature.sounds.generated.resources.sounds_tab_local
import memix.feature.sounds.generated.resources.sounds_tab_mine
import memix.feature.sounds.generated.resources.sounds_tab_trending
import memix.feature.sounds.generated.resources.sounds_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Sounds tab (spec P3-02): title, search and tabs (pinned once the title scrolls off), category chips, a status
 * strip and the rows, all in one lazy list so they scroll together. Stateless: the ViewModel one level up owns the
 * state; only the search text, the scroll and a few animations live here. The details sheet is [SoundDetailsSheet], which
 * the app places over the bottom nav; [clock] says where the playing sound is, read in draw code for the row's waveform.
 * [onFreeUpSpace] opens the phone's storage screen from the phone-is-full toast (P3-04); the app says when there is none.
 */
@Composable
fun SoundsScreen(
    state: SoundsUiState,
    onIntent: (SoundsIntent) -> Unit,
    clock: PlaybackClock,
    onFreeUpSpace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Back clears the search first (the keyboard, when open, takes the first back itself); then the app's own back runs.
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = state.query.isNotEmpty()) {
        onIntent(SoundsIntent.ClearQuery)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onIntent(SoundsIntent.AppStopped) }
    // After the first frame, so opening the tab never waits for the catalog or the player.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        onIntent(SoundsIntent.ScreenShown)
    }
    val currentOnIntent by rememberUpdatedState(onIntent)

    val listState = rememberLazyListState()
    val field = remember { TextFieldState(state.query) }
    val searchFocus = remember { FocusRequester() }
    val allChipFocus = remember { FocusRequester() }
    var searchHasFocus by remember { mutableStateOf(false) }
    SearchTextSync(field, state.query, listState, onIntent)

    val focusManager = LocalFocusManager.current
    val refreshDistancePx = with(LocalDensity.current) { (MemixSize.touchTarget * 2).toPx() }
    val scrollEffects = remember(refreshDistancePx) {
        PullToRefresh(
            refreshDistancePx,
            onRefresh = { currentOnIntent(SoundsIntent.Refresh) },
            onUserScroll = { if (searchHasFocus) focusManager.clearFocus() },
        )
    }
    val fade = rememberTabFade(state.tab)
    val position = rememberPlaybackPosition(clock, state.activeId, isPlaying = state.playingId != null)

    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        SoundsList(
            state = state,
            listState = listState,
            field = field,
            searchFocus = searchFocus,
            allChipFocus = allChipFocus,
            onSearchFocusChange = { searchHasFocus = it },
            fade = fade,
            position = position,
            onIntent = onIntent,
            modifier = Modifier.fillMaxSize().nestedScroll(scrollEffects),
        )
        // While the details sheet is open it shows the toasts itself, above its scrim.
        FadingToast(state.toast.takeIf { state.details == null }, Modifier.align(Alignment.BottomCenter).padding(MemixSpacing.space4)) { toast ->
            Toast(soundsToastText(toast))
        }
        if (state.storageFullNote && state.details == null) {
            StorageFullToast(
                canFreeUpSpace = state.canFreeUpSpace,
                onFreeUpSpace = onFreeUpSpace,
                onDismiss = { onIntent(SoundsIntent.DismissStorageNote) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(MemixSpacing.space4),
            )
        }
    }
}

@Composable
private fun SoundsList(
    state: SoundsUiState,
    listState: LazyListState,
    field: TextFieldState,
    searchFocus: FocusRequester,
    allChipFocus: FocusRequester,
    onSearchFocusChange: (Boolean) -> Unit,
    fade: Modifier,
    position: State<Long>,
    onIntent: (SoundsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalLocale.current.language
    val content = state.content
    LoadMoreWhenNearEnd(listState, content, onIntent)
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(bottom = MemixSpacing.space4)) {
        item(key = "title", contentType = "title") { ScreenTitle(stringResource(Res.string.sounds_title)) }
        stickyHeader(key = "search-and-tabs", contentType = "search-and-tabs") {
            SearchAndTabs(state.tab, field, searchFocus, onSearchFocusChange, onIntent)
        }
        state.spokenResultCount?.let { count ->
            item(key = "result-count", contentType = "spoken") {
                val spoken = pluralStringResource(Res.plurals.sounds_results_a11y, count, count)
                // Real bounds, nothing drawn: TalkBack ignores live regions on a node with no size.
                Box(Modifier.fillMaxWidth().height(MemixStroke.strokeHairline).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = spoken })
            }
        }
        if (state.showsChips) {
            item(key = "chips", contentType = "chips") {
                CategoryChips(state.categories, state.categoryId, language, { onIntent(SoundsIntent.SelectCategory(it)) }, allChipFocus, fade)
            }
        }
        state.notice?.let { notice -> item(key = "notice", contentType = "notice") { StatusStrip(notice) } }
        if (state.tab == SoundsTab.LOCAL && state.query.isEmpty() && state.regionName != null) {
            item(key = "region", contentType = "region") { RegionLine(state.regionName, fade) }
        }
        // After "Clear search" focus goes to the search box, after "Show all" to the "All" chip (spec P3-02 → Accessibility).
        val onBlockIntent = { intent: SoundsIntent ->
            when {
                intent == SoundsIntent.ClearQuery -> searchFocus.requestFocus()
                intent is SoundsIntent.SelectCategory && intent.categoryId == null -> allChipFocus.requestFocus()
            }
            onIntent(intent)
        }
        contentItems(state, content, language, fade, position, onBlockIntent)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.contentItems(
    state: SoundsUiState,
    content: SoundsContent,
    language: String,
    fade: Modifier,
    position: State<Long>,
    onIntent: (SoundsIntent) -> Unit,
) {
    when (content) {
        is SoundsContent.Loading -> if (content.showPlaceholders) {
            item(key = "loading-status", contentType = "spoken") { LoadingAnnouncement() }
            items(PLACEHOLDER_COUNT, key = { "placeholder-$it" }, contentType = { "placeholder" }) { PlaceholderRow() }
        }
        SoundsContent.LoadError -> item(key = "error", contentType = "block") {
            LoadErrorBlock({ onIntent(SoundsIntent.TryAgain) }, fade)
        }
        is SoundsContent.Empty -> item(key = "empty", contentType = "block") {
            EmptyBlock(content.reason, state.query, state.regionName, onIntent, fade)
        }
        is SoundsContent.Rows -> {
            items(content.rows, key = { it.id }, contentType = { "sound" }) { row ->
                LibrarySoundRow(row, state.playbackOf(row.id), state.isFavorite(row.id), state.peaksFor(row.id), state.returnsFocusTo(row.id, fromRecent = false), state.lookOf(row.id), state.downloadProgressOf(row.id), language, fade, position, onIntent)
            }
            item(key = "footer", contentType = "footer") { ListFooterItem(content.footer, { onIntent(SoundsIntent.TryAgain) }) }
        }
        is SoundsContent.Starters -> {
            item(key = "starters-heading", contentType = "section") { SectionHeading(Res.string.sounds_starter_heading, isFirst = true, modifier = fade) }
            items(content.rows, key = { it.id }, contentType = { "sound" }) { row ->
                LibrarySoundRow(row, state.playbackOf(row.id), state.isFavorite(row.id), state.peaksFor(row.id), state.returnsFocusTo(row.id, fromRecent = false), state.lookOf(row.id), state.downloadProgressOf(row.id), language, fade, position, onIntent)
            }
        }
        is SoundsContent.Favorites -> favoriteSections(content, state, language, fade, position, onIntent)
    }
}

/** Recently used, then Favorites; a sound in both gets a row in each, so the keys carry the section. */
private fun androidx.compose.foundation.lazy.LazyListScope.favoriteSections(
    content: SoundsContent.Favorites,
    state: SoundsUiState,
    language: String,
    fade: Modifier,
    position: State<Long>,
    onIntent: (SoundsIntent) -> Unit,
) {
    if (content.recent.isNotEmpty()) {
        item(key = "recent-heading", contentType = "section") { SectionHeading(Res.string.sounds_section_recent, isFirst = true, modifier = fade) }
        items(content.recent, key = { "recent-${it.id}" }, contentType = { "sound" }) { row ->
            LibrarySoundRow(row, state.playbackOf(row.id), state.isFavorite(row.id), state.peaksFor(row.id), state.returnsFocusTo(row.id, fromRecent = true), state.lookOf(row.id), state.downloadProgressOf(row.id), language, fade, position, onIntent, fromRecent = true)
        }
    }
    val showsHint = content.favorites.isEmpty() && state.query.isBlank()
    if (content.favorites.isNotEmpty() || showsHint) {
        item(key = "favorites-heading", contentType = "section") {
            SectionHeading(Res.string.sounds_section_favorites, isFirst = content.recent.isEmpty(), modifier = fade)
        }
    }
    if (showsHint) item(key = "favorites-hint", contentType = "hint") { SectionHint(Res.string.sounds_empty_favorites_body, fade) }
    items(content.favorites, key = { "favorite-${it.id}" }, contentType = { "sound" }) { row ->
        LibrarySoundRow(row, state.playbackOf(row.id), state.isFavorite(row.id), state.peaksFor(row.id), state.returnsFocusTo(row.id, fromRecent = false), state.lookOf(row.id), state.downloadProgressOf(row.id), language, fade, position, onIntent)
    }
}

@Composable
private fun LibrarySoundRow(
    row: SoundItemUi,
    playback: RowPlayback,
    isFavorite: Boolean,
    peaks: List<Float>?,
    returnsFocus: Boolean,
    look: SoundRowLook,
    downloadProgress: Float?,
    language: String,
    fade: Modifier,
    position: State<Long>,
    onIntent: (SoundsIntent) -> Unit,
    fromRecent: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val progress = remember(playback == RowPlayback.IDLE, peaks, position, row.durationUs, look == SoundRowLook.DOWNLOADING, downloadProgress) {
        when {
            // The download's rail: determinate, in the place the playing waveform takes.
            look == SoundRowLook.DOWNLOADING -> SoundProgress(null) { downloadProgress ?: 0f }
            playback == RowPlayback.IDLE -> null
            else -> SoundProgress(peaks) { playedFraction(position.value, row.durationUs) }
        }
    }
    // After the details sheet closes, the row that opened it takes focus back (spec P3-02 → Accessibility → Focus).
    val detailsFocus = remember { FocusRequester() }
    LaunchedEffect(returnsFocus) {
        if (!returnsFocus) return@LaunchedEffect
        // The row can have scrolled out of the list while the sheet was up; then there is nothing to focus.
        runCatching { detailsFocus.requestFocus() }
        onIntent(SoundsIntent.FocusReturned)
    }
    SoundRow(
        name = row.title.inLanguage(language),
        meta = soundMetaText(row, look),
        isPlaying = playback == RowPlayback.PLAYING,
        isFavorite = isFavorite,
        onPlayToggle = { onIntent(SoundsIntent.TogglePlay(row.id, fromRecent)) },
        onFavoriteChange = { on ->
            // One tick when the heart fills; taking it out is quiet (spec P3-02 → Interactions).
            if (on) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onIntent(SoundsIntent.ToggleFavorite(row.id))
        },
        // TODO(P3-15): the blue Use block appears once the Use flow exists.
        onAdd = null,
        modifier = fade.padding(horizontal = MemixSpacing.space2, vertical = MemixSpacing.space2),
        isPaused = playback == RowPlayback.PAUSED,
        progress = progress,
        onShowDetails = { onIntent(SoundsIntent.OpenDetails(row.id, fromRecent)) },
        detailsFocus = detailsFocus,
        look = look,
    )
}

// What a row needs from the state, as plain values, so a row whose own values are unchanged is skipped.
private fun SoundsUiState.isFavorite(soundId: String) = soundId in favoriteIds
private fun SoundsUiState.peaksFor(soundId: String): List<Float>? = if (playbackOf(soundId) == RowPlayback.IDLE) null else waveforms[soundId]
private fun SoundsUiState.returnsFocusTo(soundId: String, fromRecent: Boolean) = focusReturn == RowFocusTarget(soundId, fromRecent)

/** The pinned pair under the title: the search box, then the five tabs. */
@Composable
private fun SearchAndTabs(
    tab: SoundsTab,
    field: TextFieldState,
    searchFocus: FocusRequester,
    onSearchFocusChange: (Boolean) -> Unit,
    onIntent: (SoundsIntent) -> Unit,
) {
    val labels = listOf(
        stringResource(Res.string.sounds_tab_trending),
        stringResource(Res.string.sounds_tab_local),
        stringResource(Res.string.sounds_tab_global),
        stringResource(Res.string.sounds_tab_favorites),
        stringResource(Res.string.sounds_tab_mine),
    )
    val hint = stringResource(
        when (tab) {
            SoundsTab.FAVORITES -> Res.string.sounds_search_hint_favorites
            SoundsTab.MINE -> Res.string.sounds_search_hint_mine
            else -> Res.string.sounds_search_hint
        },
    )
    Column(Modifier.fillMaxWidth().background(MemixColors.canvas)) {
        SoundsSearchField(
            field = field,
            hint = hint,
            focusRequester = searchFocus,
            onFocusChange = onSearchFocusChange,
            onClear = {
                field.clearText()
                searchFocus.requestFocus()
            },
            modifier = Modifier.padding(top = MemixSpacing.space2),
        )
        Tabs(
            labels,
            selectedIndex = tab.ordinal,
            onSelect = { onIntent(SoundsIntent.SelectTab(SoundsTab.entries[it])) },
            contentPadding = MemixSpacing.space4,
        )
    }
}

/**
 * Keeps the typed text and the ViewModel's query in step. The text is the source while typing; the ViewModel clears it
 * (Back, "Clear search", leaving the tab). The scroll position of the tab's own list is kept while a search shows its
 * results and restored when the search ends (spec P3-02 → Search).
 */
@Composable
private fun SearchTextSync(field: TextFieldState, query: String, listState: LazyListState, onIntent: (SoundsIntent) -> Unit) {
    var scrollBeforeSearch by remember { mutableStateOf<ScrollSpot?>(null) }
    LaunchedEffect(field) {
        var previous = field.text.toString()
        snapshotFlow { field.text.toString() }.drop(1).collect { text ->
            if (previous.isEmpty() && text.isNotEmpty()) {
                scrollBeforeSearch = ScrollSpot(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                onIntent(SoundsIntent.QueryChanged(text))
                listState.scrollToItem(0)
            } else {
                onIntent(SoundsIntent.QueryChanged(text))
            }
            previous = text
        }
    }
    LaunchedEffect(query) {
        if (query.isNotEmpty()) return@LaunchedEffect
        if (field.text.isNotEmpty()) field.clearText()
        scrollBeforeSearch?.let { listState.scrollToItem(it.index, it.offset) }
        scrollBeforeSearch = null
    }
}

private data class ScrollSpot(val index: Int, val offset: Int)

/** Asks for the next page when the last rows come into view. */
@Composable
private fun LoadMoreWhenNearEnd(listState: LazyListState, content: SoundsContent, onIntent: (SoundsIntent) -> Unit) {
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_MARGIN
        }
    }
    val rowCount = (content as? SoundsContent.Rows)?.rows?.size ?: 0
    LaunchedEffect(nearEnd, rowCount) {
        if (nearEnd && rowCount > 0) onIntent(SoundsIntent.LoadMore)
    }
}

/** A tab change cross-fades the list in over `duration-sheet` (120 ms with reduce motion, which reads as a swap). */
@Composable
private fun rememberTabFade(tab: SoundsTab): Modifier {
    val alpha = remember { Animatable(1f) }
    val millis = if (MemixTheme.reduceMotion) MemixMotion.durationPress else MemixMotion.durationSheet
    var shownTab by remember { mutableStateOf(tab) }
    LaunchedEffect(tab) {
        if (tab == shownTab) return@LaunchedEffect
        shownTab = tab
        alpha.snapTo(0f)
        alpha.animateTo(1f, tween(millis))
    }
    return remember(alpha) { Modifier.graphicsLayer { this.alpha = alpha.value } }
}

private const val PLACEHOLDER_COUNT = 6
private const val LOAD_MORE_MARGIN = 6
