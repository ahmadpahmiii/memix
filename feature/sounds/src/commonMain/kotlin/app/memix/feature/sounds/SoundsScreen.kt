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
import androidx.compose.ui.graphics.Color
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
 * [pickers] open the system pickers of My sounds' two actions (P3-07).
 */
@Composable
fun SoundsScreen(
    state: SoundsUiState,
    onIntent: (SoundsIntent) -> Unit,
    clock: PlaybackClock,
    onFreeUpSpace: () -> Unit,
    modifier: Modifier = Modifier,
    pickers: MySoundPickers = MySoundPickers.None,
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
    ReportSentHaptic(state.toast)

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
            pickers = pickers,
            modifier = Modifier.fillMaxSize().nestedScroll(scrollEffects),
        )
        // While the details sheet is open it shows the toasts itself, above its scrim.
        FadingToast(state.toast.takeIf { state.details == null }, Modifier.align(Alignment.BottomCenter).padding(MemixSpacing.space4)) { toast ->
            SoundsToastItem(toast, state.addedName)
        }
        // The Undo toast keeps its window: another toast shows for its own time, and this one returns for the rest of it (spec P3-07).
        FadingToast(state.deletedMine.takeIf { state.toast == null && state.details == null }, Modifier.align(Alignment.BottomCenter).padding(MemixSpacing.space4)) { deleted ->
            DeletedMineToast(deleted, onIntent)
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
    pickers: MySoundPickers,
    modifier: Modifier = Modifier,
) {
    val language = LocalLocale.current.language
    LoadMoreWhenNearEnd(listState, state.content, onIntent)
    ScrollToTopOnAdded(listState, state.addedCount)
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(bottom = MemixSpacing.space4)) {
        item(key = "title", contentType = "title") { ScreenTitle(stringResource(Res.string.sounds_title)) }
        stickyHeader(key = "search-and-tabs", contentType = "search-and-tabs") {
            SearchAndTabs(state.tab, field, searchFocus, onSearchFocusChange, onIntent)
        }
        soundsBody(state, language, searchFocus, allChipFocus, fade, position, onIntent, pickers = pickers)
    }
}

/** A sound just added to My sounds is the first row: the list scrolls up to it (spec P3-07 → Entry and exit). */
@Composable
internal fun ScrollToTopOnAdded(listState: LazyListState, addedCount: Int) {
    LaunchedEffect(addedCount) { if (addedCount > 0) listState.animateScrollToItem(0) }
}

/**
 * Everything under the pinned search and tabs, shared by the Sounds tab and the editor's picker (P3-05): the spoken result
 * count, the chips (not in the picker), the status strips, the Local tab's region line and the rows. In the picker
 * ([picker]) the rows carry the Add block and sit on `surface`, and a full phone shows a strip with its action.
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.soundsBody(
    state: SoundsUiState,
    language: String,
    searchFocus: FocusRequester,
    allChipFocus: FocusRequester,
    fade: Modifier,
    position: State<Long>,
    onIntent: (SoundsIntent) -> Unit,
    picker: Boolean = false,
    onFreeUpSpace: () -> Unit = {},
    pickers: MySoundPickers = MySoundPickers.None,
) {
    state.spokenResultCount?.let { count ->
        item(key = "result-count", contentType = "spoken") {
            val spoken = pluralStringResource(Res.plurals.sounds_results_a11y, count, count)
            // Real bounds, nothing drawn: TalkBack ignores live regions on a node with no size.
            Box(Modifier.fillMaxWidth().height(MemixStroke.strokeHairline).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = spoken })
        }
    }
    if (state.showsChips && !picker) {
        item(key = "chips", contentType = "chips") {
            CategoryChips(state.categories, state.categoryId, language, { onIntent(SoundsIntent.SelectCategory(it)) }, allChipFocus, fade)
        }
    }
    state.notice?.let { notice -> item(key = "notice", contentType = "notice") { StatusStrip(notice) } }
    if (picker && state.storageFullNote) {
        item(key = "storage-full", contentType = "notice") { StorageFullStrip(state.canFreeUpSpace, onFreeUpSpace, fade) }
    }
    // My sounds: the two actions come first, scrolling with the list, empty or not; a search hides them (spec P3-07 → Layout).
    if (state.tab == SoundsTab.MINE && state.query.isEmpty()) {
        mySoundsHeader(hasSounds = state.content is SoundsContent.Rows, pickers = pickers, onIntent = onIntent)
    }
    if (state.tab == SoundsTab.LOCAL && state.query.isEmpty() && state.regionName != null) {
        item(key = "region", contentType = "region") { RegionLine(state.regionName, fade) }
    }
    // After "Clear search" focus goes to the search box, after "Show all" to the "All" chip (spec P3-02 → Accessibility).
    val onBlockIntent = { intent: SoundsIntent ->
        when {
            intent == SoundsIntent.ClearQuery -> searchFocus.requestFocusIfShown()
            intent is SoundsIntent.SelectCategory && intent.categoryId == null -> allChipFocus.requestFocusIfShown()
        }
        onIntent(intent)
    }
    contentItems(state, state.content, language, fade, position, onBlockIntent, picker)
}

private fun androidx.compose.foundation.lazy.LazyListScope.contentItems(
    state: SoundsUiState,
    content: SoundsContent,
    language: String,
    fade: Modifier,
    position: State<Long>,
    onIntent: (SoundsIntent) -> Unit,
    picker: Boolean,
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
                LibrarySoundRow(row, state.playbackOf(row.id), state.isFavorite(row.id), state.peaksFor(row.id), state.returnsFocusTo(row.id, fromRecent = false), state.lookOf(row.id), state.downloadProgressOf(row.id), language, fade, position, onIntent, picker = picker, adding = state.addingId == row.id)
            }
            item(key = "footer", contentType = "footer") { ListFooterItem(content.footer, { onIntent(SoundsIntent.TryAgain) }) }
        }
        is SoundsContent.Starters -> {
            item(key = "starters-heading", contentType = "section") { SectionHeading(Res.string.sounds_starter_heading, isFirst = true, modifier = fade) }
            items(content.rows, key = { it.id }, contentType = { "sound" }) { row ->
                LibrarySoundRow(row, state.playbackOf(row.id), state.isFavorite(row.id), state.peaksFor(row.id), state.returnsFocusTo(row.id, fromRecent = false), state.lookOf(row.id), state.downloadProgressOf(row.id), language, fade, position, onIntent, picker = picker, adding = state.addingId == row.id)
            }
        }
        is SoundsContent.Favorites -> favoriteSections(content, state, language, fade, position, onIntent, picker)
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
    picker: Boolean,
) {
    if (content.recent.isNotEmpty()) {
        item(key = "recent-heading", contentType = "section") { SectionHeading(Res.string.sounds_section_recent, isFirst = true, modifier = fade) }
        items(content.recent, key = { "recent-${it.id}" }, contentType = { "sound" }) { row ->
            LibrarySoundRow(row, state.playbackOf(row.id), state.isFavorite(row.id), state.peaksFor(row.id), state.returnsFocusTo(row.id, fromRecent = true), state.lookOf(row.id), state.downloadProgressOf(row.id), language, fade, position, onIntent, fromRecent = true, picker = picker, adding = state.addingId == row.id)
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
        LibrarySoundRow(row, state.playbackOf(row.id), state.isFavorite(row.id), state.peaksFor(row.id), state.returnsFocusTo(row.id, fromRecent = false), state.lookOf(row.id), state.downloadProgressOf(row.id), language, fade, position, onIntent, picker = picker, adding = state.addingId == row.id)
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
    picker: Boolean = false,
    adding: Boolean = false,
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
        name = row.nameIn(language),
        meta = soundMetaText(row, look),
        isPlaying = playback == RowPlayback.PLAYING,
        isFavorite = isFavorite,
        onPlayToggle = { onIntent(SoundsIntent.TogglePlay(row.id, fromRecent)) },
        // My sounds have no heart: they have their own tab (spec P3-07).
        onFavoriteChange = if (row.isMine) null else { on ->
            // One tick when the heart fills; taking it out is quiet (spec P3-02 → Interactions).
            if (on) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onIntent(SoundsIntent.ToggleFavorite(row.id))
        },
        // The picker's blue Add block lands the sound at the playhead; the Sounds tab's Use block starts a new video meme with it (P3-15).
        onAdd = if (picker) ({ onIntent(SoundsIntent.Add(row.id, fromRecent)) }) else ({ onIntent(SoundsIntent.Use(row.id, fromRecent)) }),
        usesInNewMeme = !picker,
        modifier = fade.padding(horizontal = MemixSpacing.space2, vertical = MemixSpacing.space2),
        // The picker is a sheet on `surface`, where the playing band must be one step up.
        onSurface = picker,
        isPaused = playback == RowPlayback.PAUSED,
        progress = progress,
        onShowDetails = { onIntent(SoundsIntent.OpenDetails(row.id, fromRecent)) },
        detailsFocus = detailsFocus,
        look = look,
        addInProgress = adding,
    )
}

// What a row needs from the state, as plain values, so a row whose own values are unchanged is skipped.
internal fun SoundsUiState.isFavorite(soundId: String) = soundId in favoriteIds
private fun SoundsUiState.peaksFor(soundId: String): List<Float>? = if (playbackOf(soundId) == RowPlayback.IDLE) null else waveforms[soundId]
private fun SoundsUiState.returnsFocusTo(soundId: String, fromRecent: Boolean) = focusReturn == RowFocusTarget(soundId, fromRecent)

/** The search box with the placeholder of [tab] (spec P3-02 → Layout 2); also the picker's, which may scroll it with the list. */
@Composable
internal fun SearchField(tab: SoundsTab, field: TextFieldState, searchFocus: FocusRequester, onSearchFocusChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val hint = stringResource(
        when (tab) {
            SoundsTab.FAVORITES -> Res.string.sounds_search_hint_favorites
            SoundsTab.MINE -> Res.string.sounds_search_hint_mine
            else -> Res.string.sounds_search_hint
        },
    )
    SoundsSearchField(
        field = field,
        hint = hint,
        focusRequester = searchFocus,
        onFocusChange = onSearchFocusChange,
        onClear = {
            field.clearText()
            searchFocus.requestFocusIfShown()
        },
        modifier = modifier.padding(top = MemixSpacing.space2),
    )
}

/** The pinned pair under the title: the search box, then the five tabs. [background] is the container's color; [showSearch] false leaves the search box to the list (the picker at large font). */
@Composable
internal fun SearchAndTabs(
    tab: SoundsTab,
    field: TextFieldState,
    searchFocus: FocusRequester,
    onSearchFocusChange: (Boolean) -> Unit,
    onIntent: (SoundsIntent) -> Unit,
    background: Color = MemixColors.canvas,
    showSearch: Boolean = true,
) {
    val labels = listOf(
        stringResource(Res.string.sounds_tab_trending),
        stringResource(Res.string.sounds_tab_local),
        stringResource(Res.string.sounds_tab_global),
        stringResource(Res.string.sounds_tab_favorites),
        stringResource(Res.string.sounds_tab_mine),
    )
    Column(Modifier.fillMaxWidth().background(background)) {
        if (showSearch) SearchField(tab, field, searchFocus, onSearchFocusChange)
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
internal fun SearchTextSync(field: TextFieldState, query: String, listState: LazyListState, onIntent: (SoundsIntent) -> Unit) {
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
internal fun LoadMoreWhenNearEnd(listState: LazyListState, content: SoundsContent, onIntent: (SoundsIntent) -> Unit) {
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
internal fun rememberTabFade(tab: SoundsTab): Modifier {
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

/** Asks for focus unless the target is not on screen (the picker's search box scrolled away at a large font). */
internal fun FocusRequester.requestFocusIfShown() {
    runCatching { requestFocus() }
}

/** A font scale from which two layouts change: the picker's search box scrolls with the list, and the details' time moves under the player (spec P3-03, P3-05). */
internal const val LARGE_FONT_SCALE = 1.5f
