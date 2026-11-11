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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import app.memix.core.designsystem.component.SoundRow
import app.memix.core.designsystem.component.Tabs
import app.memix.core.designsystem.component.Toast
import app.memix.core.ui.ScreenTitle
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.sound_meta
import memix.feature.sounds.generated.resources.Res
import memix.feature.sounds.generated.resources.sound_play_failed
import memix.feature.sounds.generated.resources.sounds_refresh_failed
import memix.feature.sounds.generated.resources.sounds_results_a11y
import memix.feature.sounds.generated.resources.sounds_search_hint
import memix.feature.sounds.generated.resources.sounds_search_hint_favorites
import memix.feature.sounds.generated.resources.sounds_search_hint_mine
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
 * state; only the search text, the scroll and a few animations live here.
 */
@Composable
fun SoundsScreen(state: SoundsUiState, onIntent: (SoundsIntent) -> Unit, modifier: Modifier = Modifier) {
    // Back clears the search first (the keyboard, when open, takes the first back itself); then the app's own back runs.
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = state.query.isNotEmpty()) {
        onIntent(SoundsIntent.ClearQuery)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onIntent(SoundsIntent.AppStopped) }
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

    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        SoundsList(
            state = state,
            listState = listState,
            field = field,
            searchFocus = searchFocus,
            allChipFocus = allChipFocus,
            onSearchFocusChange = { searchHasFocus = it },
            fade = fade,
            onIntent = onIntent,
            modifier = Modifier.fillMaxSize().nestedScroll(scrollEffects),
        )
        FadingToast(state.toast, Modifier.align(Alignment.BottomCenter).padding(MemixSpacing.space4)) { toast ->
            Toast(stringResource(if (toast == SoundsToast.PLAY_FAILED) Res.string.sound_play_failed else Res.string.sounds_refresh_failed))
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
        contentItems(state, content, language, fade, onBlockIntent)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.contentItems(
    state: SoundsUiState,
    content: SoundsContent,
    language: String,
    fade: Modifier,
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
                LibrarySoundRow(row, isPlaying = row.id == state.playingId, language = language, fade = fade, onIntent = onIntent)
            }
            item(key = "footer", contentType = "footer") { ListFooterItem(content.footer, { onIntent(SoundsIntent.TryAgain) }) }
        }
    }
}

@Composable
private fun LibrarySoundRow(row: SoundItemUi, isPlaying: Boolean, language: String, fade: Modifier, onIntent: (SoundsIntent) -> Unit) {
    SoundRow(
        name = row.title.inLanguage(language),
        meta = stringResource(UiRes.string.sound_meta, row.length, row.credit),
        isPlaying = isPlaying,
        isFavorite = false,
        onPlayToggle = { onIntent(SoundsIntent.TogglePlay(row.id)) },
        // TODO(P3-06): the heart appears with the Favorites list.
        onFavoriteChange = null,
        // TODO(P3-15): the blue Use block appears once the Use flow exists.
        onAdd = null,
        modifier = fade.padding(horizontal = MemixSpacing.space2, vertical = MemixSpacing.space2),
    )
}

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
