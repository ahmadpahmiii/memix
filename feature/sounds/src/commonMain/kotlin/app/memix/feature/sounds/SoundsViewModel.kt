package app.memix.feature.sounds

import androidx.lifecycle.viewModelScope
import app.memix.core.designsystem.component.SoundRowLook
import app.memix.core.domain.AppError
import app.memix.core.domain.GetPhoneRegionUseCase
import app.memix.core.domain.Outcome
import app.memix.core.domain.SoundTab
import app.memix.core.domain.catalog.CheckRemovedSoundsUseCase
import app.memix.core.domain.catalog.LoadSoundFeedPageUseCase
import app.memix.core.domain.catalog.MIN_SEARCH_LENGTH
import app.memix.core.domain.catalog.ObserveRemovedSoundIdsUseCase
import app.memix.core.domain.catalog.ObserveSoundCategoriesUseCase
import app.memix.core.domain.catalog.ObserveSoundFeedUseCase
import app.memix.core.domain.catalog.RefreshSoundCategoriesUseCase
import app.memix.core.domain.catalog.SearchSoundsUseCase
import app.memix.core.domain.catalog.SoundSearchPage
import app.memix.core.domain.sound.GetSavedSoundIdsUseCase
import app.memix.core.domain.sound.ObserveFavoriteSoundIdsUseCase
import app.memix.core.domain.sound.ObserveFavoriteSoundsUseCase
import app.memix.core.domain.sound.ObserveRecentSoundsUseCase
import app.memix.core.domain.sound.PickedSound
import app.memix.core.domain.sound.SoundsOnPhone
import app.memix.core.domain.sound.ToggleFavoriteSoundUseCase
import app.memix.core.model.Region
import app.memix.core.model.catalog.CatalogSound
import app.memix.core.model.catalog.LibrarySound
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.model.catalog.SoundFeed
import app.memix.core.model.catalog.SoundFeedContent
import app.memix.core.ui.MemixViewModel
import app.memix.core.ui.soundLengthText
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The Sounds screen (spec P3-02): five tabs, category chips, typo-tolerant search and previews. Lists come from the
 * phone's copy of the catalog and show at once; the network only refreshes them. Tab and chip survive a trip to
 * another bottom-nav item; the search text and a playing preview do not ([SoundsIntent.LeftScreen]).
 *
 * Favorites and Recently used are kept on the phone (P3-06) and fill the Favorites tab; My sounds has no storage yet
 * (P3-07), so it shows its empty state.
 *
 * Previews (P3-03): a second tap on the playing disc pauses and the next resumes; the details sheet plays through the
 * same player, so one sound plays at a time. The app places the sheet over the bottom nav, so this ViewModel lives with
 * the tabs, and the catalog is not touched until the tab first shows ([SoundsIntent.ScreenShown]).
 *
 * Downloads and offline (P3-04): a sound that is not on the phone downloads when its disc is tapped, then plays; the
 * rows show the Downloading, Failed, offline and removed looks ([SoundsUiState.lookOf]). The starter pack and downloaded
 * sounds play with no network at all.
 *
 * Playing, downloading and preparing sounds is [SoundPreviewController]'s job; this class decides what the lists show.
 * The editor's picker (P3-05) is a second instance in [SoundsMode.PICKER]: the same lists, tabs and search, with an Add
 * that hands a ready sound to [picks] instead of a Use, the tab it last showed remembered ([SoundPickerMemory]), and
 * a reset when the sheet closes ([SoundsIntent.PickerClosed]) instead of when a bottom-nav item is left.
 */
class SoundsViewModel(
    private val observeCategories: ObserveSoundCategoriesUseCase,
    private val refreshCategories: RefreshSoundCategoriesUseCase,
    private val observeFeed: ObserveSoundFeedUseCase,
    private val loadFeedPage: LoadSoundFeedPageUseCase,
    private val searchSounds: SearchSoundsUseCase,
    getPhoneRegion: GetPhoneRegionUseCase,
    private val getSavedSoundIds: GetSavedSoundIdsUseCase,
    private val observeFavoriteIds: ObserveFavoriteSoundIdsUseCase,
    private val observeFavorites: ObserveFavoriteSoundsUseCase,
    private val observeRecents: ObserveRecentSoundsUseCase,
    private val toggleFavorite: ToggleFavoriteSoundUseCase,
    previewTools: SoundPreviewTools,
    private val onPhone: SoundsOnPhone,
    private val observeRemovedIds: ObserveRemovedSoundIdsUseCase,
    private val checkRemoved: CheckRemovedSoundsUseCase,
    reportTools: SoundReportTools,
    tabExit: SoundsTabExit,
    private val mode: SoundsMode,
    private val pickerMemory: SoundPickerMemory,
) : MemixViewModel<SoundsUiState, SoundsIntent>(SoundsUiState()) {
    private val regionCode: String?
    private val logEvents = previewTools.logEvents

    private val preview = SoundPreviewController(
        scope = viewModelScope,
        tools = previewTools,
        isOnPhone = { soundId -> state.value.onPhoneOf(soundId) != SoundOnPhone.NOT_SAVED },
        onNotice = ::showToast,
        onChanged = ::showPreview,
    )

    // The Report pane's form (P3-10). A report the server took closes the pane, and the Sounds tab's sheet with it.
    private val report = SoundReportController(
        scope = viewModelScope,
        tools = reportTools,
        onChanged = { form -> updateState { it.copy(report = form) } },
        onSent = ::reportSent,
    )
    private val observeReported = reportTools.observeReported

    /** Where the playing sound is; the screen reads it in draw code, 30 times a second at most. */
    val clock = PlaybackClock { preview.positionUs() }

    private val _picks = Channel<PickedSound>(Channel.BUFFERED)

    /**
     * A sound the user added in the picker, or chose with Use on the Sounds tab, with its file ready. The editor lands it
     * and closes the sheet; the app root starts a new video meme with it ([SoundsIntent.UseFinished] ends the Use).
     */
    val picks: Flow<PickedSound> = _picks.receiveAsFlow()

    private val _notices = Channel<SoundsToast>(Channel.BUFFERED)

    /** The picker only: a toast the editor shows in its own message slot, above the sheet's scrim (a sound that won't play). */
    val unplayable: Flow<SoundsToast> = _notices.receiveAsFlow()

    // The sound whose Add is being prepared (downloading, or checking the file), and whether it has been handed over. After the
    // hand-over the sheet is closing and every tap is ignored; while a download runs, another row's Add takes over (spec P3-05).
    private var addingId: String? = null
    private var pickSent = false

    // What the lists hold. The screen state is derived from these by render(), always on the main thread.
    private var feedSaved = SoundFeedContent.NotLoaded
    private var feedError: AppError? = null
    private var placeholdersDue = false
    private var search: SearchState? = null
    private var searchWaiting = false
    // The offline notes: one for the lists (a refresh failed), one for a search answered from the saved sounds.
    private var feedOffline = false
    private var loadingMore = false
    private var moreFailed = false
    private var refreshFailureToasted = false
    // The Favorites tab: the latest recents, and the favorites as drawn (an un-hearted row stays until the user leaves).
    private var recentSounds: List<LibrarySound> = emptyList()
    private var latestFavorites: List<LibrarySound> = emptyList()
    private var favoritesOnScreen: List<LibrarySound> = emptyList()
    private var started = false
    private var removedChecked = false
    // The bundled pack, which the lists fall back to when the catalog has never loaded and the phone is offline.
    private var starterSounds: List<CatalogSound> = emptyList()
    private var categoryNames: Map<String, LocalizedText> = emptyMap()
    // The open details sheet: its sound, and the `tab` a play from it is logged with (the screen that opened it).
    private var detailsSound: LibrarySound? = null
    private var detailsFromRecent = false
    private var detailsTab = SoundTab.TRENDING
    private var feedJob: Job? = null
    private var moreJob: Job? = null
    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private var toastJob: Job? = null
    private var placeholderJob: Job? = null

    init {
        val region = getPhoneRegion()
        regionCode = (region as? Region.Country)?.code
        val firstTab = if (mode == SoundsMode.PICKER) pickerMemory.tab else SoundsTab.TRENDING
        updateState { it.copy(regionName = (region as? Region.Country)?.displayName, tab = firstTab) }
        // Rotation keeps the search and the preview; only a real move to another bottom-nav item ends them.
        if (mode == SoundsMode.LIBRARY) viewModelScope.launch { tabExit.left.collect { leaveScreen() } }
    }

    // The first time the tab shows: nothing is read or fetched at app start, because this ViewModel is made with the tabs.
    private fun screenShown() {
        preview.warmUp()
        if (started) return
        started = true
        viewModelScope.launch {
            observeCategories().collect { categories ->
                categoryNames = categories.associate { it.id to it.name }
                val chips = categories.filter { it.id !in TAB_CATEGORY_IDS }.map { CategoryChipUi(it.id, it.name) }
                updateState { it.copy(categories = chips.toImmutableList()) }
            }
        }
        observeCollections()
        observeOnPhone()
        viewModelScope.launch { refreshCategories(force = false) }
        showFeed()
    }

    // What is on the phone, whether the phone is online, and what the catalog dropped: the inputs of every row's look.
    private fun observeOnPhone() {
        viewModelScope.launch {
            starterSounds = onPhone.starters()
            updateState { it.copy(starterIds = starterSounds.map { starter -> starter.id }.toImmutableSet()) }
            // The offline first launch lists the pack once it is known.
            render()
        }
        viewModelScope.launch {
            onPhone.observeDownloadedIds().collect { ids ->
                // A file the cleanup removed must not be trusted from the prepared cache.
                preview.retainPrepared { it in ids || it in state.value.starterIds }
                updateState { it.copy(downloadedIds = ids.toImmutableSet()) }
            }
        }
        viewModelScope.launch {
            onPhone.observeOnline().collect { online ->
                updateState { it.copy(online = online) }
                render()
            }
        }
        viewModelScope.launch { observeRemovedIds().collect { ids -> updateState { it.copy(removedIds = ids.toImmutableSet()) } } }
    }

    // Asks the catalog whether the favorites and recents are still listed, so dropped ones say so. Once per ViewModel, when
    // the first non-empty list arrives (the repository adds its own 24 h rule); best effort, online only.
    private fun checkRemovedSoundsOnce() {
        if (removedChecked || !state.value.online) return
        val ids = (latestFavorites + recentSounds).mapTo(HashSet()) { it.id }
        if (ids.isEmpty()) return
        removedChecked = true
        viewModelScope.launch { checkRemoved(ids) }
    }

    private fun observeCollections() {
        viewModelScope.launch {
            observeFavoriteIds().collect { ids -> updateState { it.copy(favoriteIds = ids.toImmutableSet()) } }
        }
        viewModelScope.launch {
            observeFavorites().collect { favorites ->
                latestFavorites = favorites
                checkRemovedSoundsOnce()
                val onFavorites = state.value.tab == SoundsTab.FAVORITES
                favoritesOnScreen = if (onFavorites) keepUnfavoritedRows(favorites, favoritesOnScreen) else favorites
                if (onFavorites) render()
            }
        }
        viewModelScope.launch {
            observeReported().collect { ids -> updateState { it.copy(reportedIds = ids.toImmutableSet()) } }
        }
        viewModelScope.launch {
            observeRecents().collect { recents ->
                recentSounds = recents
                checkRemovedSoundsOnce()
                if (state.value.tab == SoundsTab.FAVORITES) render()
            }
        }
    }

    override fun onIntent(intent: SoundsIntent) {
        when (intent) {
            is SoundsIntent.SelectTab -> { dropFocusReturn(); selectTab(intent.tab) }
            is SoundsIntent.SelectCategory -> { dropFocusReturn(); selectCategory(intent.categoryId) }
            is SoundsIntent.QueryChanged -> { dropFocusReturn(); changeQuery(intent.text) }
            SoundsIntent.ClearQuery -> changeQuery("")
            is SoundsIntent.TogglePlay -> togglePreview(intent.soundId, tabOf(intent.fromRecent))
            is SoundsIntent.OpenDetails -> { dropFocusReturn(); openDetails(intent.soundId, intent.fromRecent) }
            SoundsIntent.CloseDetails -> closeDetails()
            SoundsIntent.ToggleDetailsPlay -> detailsSound?.let { togglePreview(it.id, detailsTab) }
            SoundsIntent.OpenReport -> openReport()
            SoundsIntent.CloseReport -> updateState { it.copy(reportOpen = false) }
            is SoundsIntent.ReportReasonChosen -> report.chooseReason(intent.reason)
            is SoundsIntent.ReportNoteChanged -> report.changeNote(intent.text)
            SoundsIntent.SendReport -> detailsSound?.let { report.send(it.id) }
            SoundsIntent.SourceOpenFailed -> showToast(SoundsToast.SOURCE_FAILED)
            SoundsIntent.FocusReturned -> dropFocusReturn()
            SoundsIntent.ScreenShown -> screenShown()
            is SoundsIntent.ToggleFavorite -> viewModelScope.launch { toggleFavorite(intent.soundId) }
            SoundsIntent.LoadMore -> loadMore()
            SoundsIntent.TryAgain -> tryAgain()
            SoundsIntent.Refresh -> refresh()
            SoundsIntent.DismissStorageNote -> preview.dismissStorageNote()
            SoundsIntent.FreeUpSpaceUnavailable -> updateState { it.copy(canFreeUpSpace = false) }
            SoundsIntent.AppStopped -> backgrounded()
            SoundsIntent.LeftScreen -> leaveScreen()
            is SoundsIntent.Add -> { dropFocusReturn(); add(intent.soundId, tabOf(intent.fromRecent)) }
            SoundsIntent.AddFromDetails -> detailsSound?.let { add(it.id, detailsTab) }
            is SoundsIntent.Use -> { dropFocusReturn(); use(intent.soundId, tabOf(intent.fromRecent)) }
            SoundsIntent.UseFromDetails -> detailsSound?.let { use(it.id, detailsTab) }
            SoundsIntent.UseFinished -> finishUse()
            SoundsIntent.PickerClosed -> closePicker()
            SoundsIntent.PickerExited -> resetPickerVisit()
        }
    }

    // A pending focus request must not fire later, when the user has moved on (a new tab, a typed query).
    private fun dropFocusReturn() {
        if (state.value.focusReturn != null) updateState { it.copy(focusReturn = null) }
    }

    private fun selectTab(tab: SoundsTab) {
        if (tab == state.value.tab) return
        updateState { it.copy(tab = tab, categoryId = null) }
        if (mode == SoundsMode.PICKER) pickerMemory.tab = tab
        // Coming to or leaving Favorites starts its rows afresh: no un-hearted row is kept.
        favoritesOnScreen = latestFavorites
        showFeed()
        // The tab decides what a search covers (the whole catalog, or only this tab's own list).
        if (state.value.query.isNotEmpty()) startSearch(debounceMillis = 0, logResult = false)
    }

    private fun selectCategory(categoryId: String?) {
        if (categoryId == state.value.categoryId) return
        updateState { it.copy(categoryId = categoryId) }
        showFeed()
    }

    // In the background the player is released; coming back sends ScreenShown, which makes it again.
    private fun backgrounded() = preview.release()

    // The player stays for the next visit (building an ExoPlayer on every tab entry is main-thread work); a sound stops.
    private fun leaveScreen() {
        preview.stop()
        detailsSound = null
        clearReport()
        updateState { it.copy(details = null, focusReturn = null) }
        // Leaving the screen ends the visit: an un-hearted row is gone when the user comes back (spec P3-02 → Tab contents).
        favoritesOnScreen = latestFavorites
        if (state.value.query.isNotEmpty()) changeQuery("")
        else if (state.value.tab == SoundsTab.FAVORITES) render()
    }

    // The picker's sheet closed (a sound landed, or the user left): a playing sound, a download, the player and any search or
    // page still loading go. The lists, the search text and the pane stay as they are while the sheet slides out; the next
    // visit starts fresh once it is gone ([resetPickerVisit]). The player is rebuilt on the next opening, so the editor
    // keeps just its own.
    private fun closePicker() {
        preview.stop()
        preview.endAdd()
        preview.release()
        searchJob?.cancel()
        moreJob?.cancel()
        refreshJob?.cancel()
        searchWaiting = false
        loadingMore = false
        setAdding(null)
        pickSent = false
    }

    // The sheet is gone: the details pane closes, the search clears and an un-hearted row goes.
    private fun resetPickerVisit() = leaveScreen()

    // ---- Feeds ----

    private fun currentFeed(): SoundFeed? {
        val ui = state.value
        return when (ui.tab) {
            SoundsTab.TRENDING -> SoundFeed.Trending(regionCode, ui.categoryId)
            SoundsTab.LOCAL -> regionCode?.let { SoundFeed.Local(it, ui.categoryId) }
            SoundsTab.GLOBAL -> SoundFeed.Global(ui.categoryId)
            SoundsTab.FAVORITES, SoundsTab.MINE -> null
        }
    }

    /** Starts over on the list the tab and chip now name: the saved copy shows at once, the network refreshes it. */
    private fun showFeed() {
        feedJob?.cancel()
        moreJob?.cancel()
        refreshJob?.cancel()
        feedSaved = SoundFeedContent.NotLoaded
        feedError = null
        loadingMore = false
        moreFailed = false
        placeholdersDue = false
        feedOffline = false
        placeholderJob?.cancel()
        val feed = currentFeed()
        render()
        if (feed == null) return
        feedJob = viewModelScope.launch {
            placeholderJob = launch {
                delay(PLACEHOLDER_DELAY_MILLIS)
                placeholdersDue = true
                render()
            }
            launch {
                observeFeed(feed).collect {
                    feedSaved = it
                    if (it.loadedPages > 0) placeholderJob?.cancel()
                    render()
                }
            }
            reloadFirstPage(feed, force = false)
        }
    }

    private suspend fun reloadFirstPage(feed: SoundFeed, force: Boolean) {
        when (val result = loadFeedPage(feed, pageIndex = 0, force = force)) {
            is Outcome.Success -> {
                feedOffline = false
                feedError = null
            }
            is Outcome.Failure -> onRefreshFailed(result.error)
        }
        render()
    }

    private fun onRefreshFailed(error: AppError) {
        feedOffline = error == AppError.Offline
        if (feedSaved.loadedPages == 0) {
            feedError = error
            return
        }
        // Rows are on screen: the strip already explains being offline; any other failure gets one toast a session.
        if (!feedOffline && !refreshFailureToasted) {
            refreshFailureToasted = true
            showToast(SoundsToast.REFRESH_FAILED)
        }
    }

    private fun refresh() {
        if (state.value.tab == SoundsTab.FAVORITES) return dropUnfavoritedRows()
        if (refreshJob?.isActive == true) return
        if (search != null || state.value.query.isNotEmpty()) {
            // One long pull can ask many times; one search at a time.
            if (searchJob?.isActive != true) startSearch(debounceMillis = 0, logResult = false)
            return
        }
        val feed = currentFeed() ?: return
        refreshJob = viewModelScope.launch {
            refreshCategories(force = true)
            reloadFirstPage(feed, force = true)
        }
    }

    // A pull on Favorites has nothing to fetch: it is the moment the un-hearted rows go.
    private fun dropUnfavoritedRows() {
        favoritesOnScreen = latestFavorites
        render()
    }

    private fun tryAgain() {
        when {
            state.value.query.isNotEmpty() && state.value.tab.listsCatalog && moreFailed -> loadMore()
            state.value.query.isNotEmpty() && state.value.tab.listsCatalog -> startSearch(debounceMillis = 0, logResult = false)
            moreFailed -> loadMore()
            else -> {
                viewModelScope.launch { refreshCategories(force = false) }
                showFeed()
            }
        }
    }

    private fun loadMore() {
        if (loadingMore) return
        val current = search
        if (current != null) {
            if (!current.endReached) loadMoreSearch(current)
            return
        }
        val feed = currentFeed() ?: return
        val saved = feedSaved
        if (saved.loadedPages == 0 || saved.endReached) return
        loadingMore = true
        moreFailed = false
        render()
        moreJob = viewModelScope.launch {
            val result = loadFeedPage(feed, pageIndex = saved.loadedPages, force = false)
            loadingMore = false
            moreFailed = result is Outcome.Failure
            render()
        }
    }

    // ---- Search ----

    private fun changeQuery(text: String) {
        if (text == state.value.query) return
        updateState { it.copy(query = text, spokenResultCount = null) }
        startSearch(debounceMillis = SEARCH_PAUSE_MILLIS, logResult = true)
    }

    /**
     * Searches [query] once the user pauses typing. Trending, Local and Global search the whole catalog (a server
     * call with an offline fallback); Favorites and My sounds filter their own list on the phone. The old rows stay
     * until the answer comes, and a "Searching…" strip appears if it takes long. Only a query typed by the user is
     * logged as `sound_search` ([logResult]); a retry, a refresh or a tab change with the same text is not a new search.
     */
    private fun startSearch(debounceMillis: Long, logResult: Boolean) {
        searchJob?.cancel()
        searchWaiting = false
        moreJob?.cancel()
        loadingMore = false
        moreFailed = false
        val query = state.value.query.trim()
        if (!state.value.tab.listsCatalog || query.length < MIN_SEARCH_LENGTH) {
            search = null
            searchWaiting = false
            render()
            return
        }
        searchJob = viewModelScope.launch {
            delay(debounceMillis)
            val slowNotice = launch {
                delay(SLOW_NOTICE_MILLIS)
                searchWaiting = true
                render()
            }
            val outcome = searchSounds(query, regionCode, pageIndex = 0, savedSoundIds = getSavedSoundIds())
            slowNotice.cancel()
            searchWaiting = false
            when (outcome) {
                is Outcome.Success -> onSearchSettled(outcome.value, logResult)
                is Outcome.Failure -> search = SearchState(emptyList(), offline = false, endReached = true, nextPage = 1, failed = true)
            }
            render()
        }
    }

    private fun onSearchSettled(page: SoundSearchPage, logResult: Boolean) {
        search = SearchState(page.sounds, page.offline, page.endReached, nextPage = 1, failed = false)
        if (logResult) logEvents.searched(results = page.sounds.size, offline = page.offline)
        updateState { it.copy(spokenResultCount = page.sounds.size) }
    }

    private fun loadMoreSearch(current: SearchState) {
        loadingMore = true
        moreFailed = false
        render()
        moreJob = viewModelScope.launch {
            val outcome = searchSounds(state.value.query.trim(), regionCode, current.nextPage, getSavedSoundIds())
            loadingMore = false
            when (outcome) {
                is Outcome.Success -> search = current.copy(
                    sounds = current.sounds + outcome.value.sounds.filter { next -> current.sounds.none { it.id == next.id } },
                    endReached = outcome.value.endReached,
                    nextPage = current.nextPage + 1,
                )
                is Outcome.Failure -> moreFailed = true
            }
            render()
        }
    }

    // ---- What the screen shows ----

    private fun render() {
        val content = contentFor(state.value)
        val notice = noticeFor(content)
        updateState { it.copy(content = content, notice = notice) }
    }

    private fun contentFor(ui: SoundsUiState): SoundsContent {
        search?.let { return searchContent(it) }
        if (!ui.tab.listsCatalog) return ownListContent(ui)
        if (ui.tab == SoundsTab.LOCAL && regionCode == null) return SoundsContent.Empty(EmptyReason.LOCAL_UNKNOWN)
        val saved = feedSaved
        return when {
            // Offline with nothing saved: the bundled pack instead of an error, so the app still has sounds to play.
            saved.loadedPages == 0 && offlineNow() && starterSounds.isNotEmpty() ->
                SoundsContent.Starters(starterSounds.map { it.toRow() }.toImmutableList())
            saved.loadedPages == 0 ->
                if (feedError != null) SoundsContent.LoadError else SoundsContent.Loading(showPlaceholders = placeholdersDue)
            saved.sounds.isEmpty() -> SoundsContent.Empty(emptyFeedReason(ui))
            else -> SoundsContent.Rows(saved.sounds.map { it.toRow() }.toImmutableList(), footer(saved.endReached))
        }
    }

    private fun offlineNow(): Boolean = !state.value.online || feedError == AppError.Offline

    private fun searchContent(result: SearchState): SoundsContent = when {
        result.failed -> SoundsContent.LoadError
        result.sounds.isEmpty() ->
            SoundsContent.Empty(if (result.offline) EmptyReason.SEARCH_NONE_OFFLINE else EmptyReason.SEARCH_NONE)
        else -> SoundsContent.Rows(result.sounds.map { it.toRow() }.toImmutableList(), footer(result.endReached))
    }

    // My sounds (TODO(P3-07)) has no storage yet: its list is empty, and a search over an empty list finds nothing.
    private fun ownListContent(ui: SoundsUiState): SoundsContent = when {
        ui.tab == SoundsTab.FAVORITES -> favoritesContent(ui.query)
        ui.query.isNotBlank() -> SoundsContent.Empty(EmptyReason.SEARCH_NONE)
        else -> SoundsContent.Empty(EmptyReason.MINE)
    }

    // On Favorites a search filters this tab's own two lists, on the phone (spec P3-02 → Search → Scope).
    private fun favoritesContent(query: String): SoundsContent {
        val needle = query.trim().lowercase()
        val recent = recentSounds.filter { it.titleContains(needle) }
        val favorites = favoritesOnScreen.filter { it.titleContains(needle) }
        return when {
            recent.isEmpty() && favorites.isEmpty() ->
                SoundsContent.Empty(if (needle.isEmpty()) EmptyReason.FAVORITES else EmptyReason.SEARCH_NONE)
            else -> SoundsContent.Favorites(recent.map { it.toRow() }.toImmutableList(), favorites.map { it.toRow() }.toImmutableList())
        }
    }

    private fun LibrarySound.titleContains(needle: String): Boolean =
        needle.isEmpty() || title.byLanguage.values.any { it.lowercase().contains(needle) }

    private fun emptyFeedReason(ui: SoundsUiState): EmptyReason = when {
        ui.categoryId != null -> EmptyReason.CATEGORY
        ui.tab == SoundsTab.TRENDING -> EmptyReason.TRENDING
        ui.tab == SoundsTab.LOCAL -> EmptyReason.LOCAL
        else -> EmptyReason.GLOBAL
    }

    private fun footer(endReached: Boolean): ListFooter = when {
        endReached -> ListFooter.NONE
        loadingMore -> ListFooter.LOADING_MORE
        moreFailed -> ListFooter.MORE_FAILED
        else -> ListFooter.NONE
    }

    private fun noticeFor(content: SoundsContent): SoundsNotice? = when {
        searchWaiting -> SoundsNotice.SEARCHING
        search?.offline == true -> SoundsNotice.SEARCH_OFFLINE
        (feedOffline || !state.value.online) && content !is SoundsContent.LoadError -> SoundsNotice.OFFLINE
        else -> null
    }

    private fun CatalogSound.toRow() = SoundItemUi(id, title, soundLengthText(durationUs), credit, durationUs)

    private fun LibrarySound.toRow() = SoundItemUi(id, title, soundLengthText(durationUs), creditLine, durationUs)

    // ---- Previews ----

    // The disc: pause what plays, carry on what is paused, otherwise start this sound.
    private fun togglePreview(soundId: String, tab: SoundTab) {
        when (state.value.lookOf(soundId)) {
            // The disc is Cancel while it downloads: stop, keep nothing, no toast (spec P3-04).
            SoundRowLook.DOWNLOADING -> return preview.cancelDownload()
            // Nothing to play right now; the row says why.
            SoundRowLook.OFFLINE_NOT_SAVED, SoundRowLook.UNAVAILABLE -> return
            SoundRowLook.NORMAL, SoundRowLook.FAILED, SoundRowLook.FAILED_STORAGE -> Unit
        }
        when (state.value.playbackOf(soundId)) {
            RowPlayback.PLAYING -> preview.pause()
            RowPlayback.PAUSED -> preview.resume()
            RowPlayback.IDLE -> soundById(soundId)?.let { preview.play(it, tab) }
        }
    }

    private fun soundById(soundId: String): LibrarySound? =
        shownSounds().firstOrNull { it.id == soundId } ?: detailsSound?.takeIf { it.id == soundId }

    // What the controller reports goes into the screen state as it is.
    private fun showPreview(preview: PreviewState) = updateState {
        it.copy(
            playingId = preview.playingId,
            pausedId = preview.pausedId,
            downloads = preview.downloads,
            waveforms = preview.waveforms,
            storageFullNote = preview.storageFullNote,
        )
    }

    // ---- Adding (the picker) ----

    // The row whose Add block shows as disabled (and is disabled for TalkBack) while its sound is being made ready.
    private fun setAdding(soundId: String?) {
        addingId = soundId
        updateState { it.copy(addingId = soundId) }
    }

    // The picker's Add block: the sound's file is made ready (downloaded first when needed) and handed to the editor, which lands it.
    private fun add(soundId: String, tab: SoundTab) {
        if (mode == SoundsMode.PICKER) handOver(soundId, tab)
    }

    // The Sounds tab's Use block (P3-15): the same, handed to the app root, which opens the system picker for the media.
    private fun use(soundId: String, tab: SoundTab) {
        if (mode == SoundsMode.LIBRARY) handOver(soundId, tab)
    }

    // A row that downloads, has no room or cannot be reached has no working block; a second tap on the same row is ignored.
    private fun handOver(soundId: String, tab: SoundTab) {
        if (pickSent || addingId == soundId) return
        when (state.value.lookOf(soundId)) {
            SoundRowLook.DOWNLOADING, SoundRowLook.FAILED_STORAGE, SoundRowLook.OFFLINE_NOT_SAVED, SoundRowLook.UNAVAILABLE -> return
            SoundRowLook.NORMAL, SoundRowLook.FAILED -> Unit
        }
        val sound = soundById(soundId) ?: return
        setAdding(soundId)
        preview.prepareForAdd(
            sound,
            onReady = { prepared ->
                pickSent = true
                _picks.trySend(PickedSound(soundId, sound.title, prepared.media, tab, prepared.bundled))
                // The details sheet closes as the system picker opens (spec P3-15 → The flow).
                if (mode == SoundsMode.LIBRARY) closeDetails()
            },
            onAbort = { if (addingId == soundId) setAdding(null) },
        )
    }

    // The picker a Use opened has gone: the app holds the sound itself from the hand-over on, so this pin can go.
    private fun finishUse() {
        if (mode != SoundsMode.LIBRARY) return
        preview.endAdd()
        setAdding(null)
        pickSent = false
    }

    // ---- Details sheet ----

    private fun openDetails(soundId: String, fromRecent: Boolean) {
        val sound = shownSounds().firstOrNull { it.id == soundId } ?: return
        detailsSound = sound
        detailsFromRecent = fromRecent
        detailsTab = tabOf(fromRecent)
        preview.loadWaveform(sound)
        updateState { it.copy(details = sound.toDetails()) }
    }

    private fun closeDetails() {
        val sound = detailsSound ?: return
        detailsSound = null
        clearReport()
        updateState { it.copy(details = null, focusReturn = RowFocusTarget(sound.id, detailsFromRecent)) }
    }

    // ---- Report pane (P3-10) ----

    // Only a sound the catalog still lists and this phone has not reported yet has a Report row, so there is nothing to open otherwise.
    private fun openReport() {
        val sound = detailsSound ?: return
        if (sound.id in state.value.reportedIds || state.value.lookOf(sound.id) == SoundRowLook.UNAVAILABLE) return
        updateState { it.copy(reportOpen = true, report = report.current) }
    }

    // The form is for one visit of the sheet: closing the sheet, or leaving the Sounds tab, empties it and drops a send still running.
    private fun clearReport() {
        report.reset()
        updateState { it.copy(reportOpen = false) }
    }

    // The server took the report. The Sounds tab's sheet closes; the picker goes back to the details, whose Report row now says
    // "Reported. Thank you." (closing the picker would throw away a sound the person was choosing to add).
    private fun reportSent() {
        clearReport()
        if (mode == SoundsMode.LIBRARY) closeDetails()
        showToast(SoundsToast.REPORT_SENT)
    }

    private fun LibrarySound.toDetails(): SoundDetailsUi {
        val host = sourceHostOf(sourceUrl)
        return SoundDetailsUi(
            id = id,
            title = title,
            durationUs = durationUs,
            length = soundLengthText(durationUs),
            categoryName = categoryNames[categoryId],
            credit = credit?.takeIf { it.isNotBlank() },
            licenseType = licenseType,
            sourceUrl = sourceUrl.takeIf { host != null },
            sourceHost = host,
        )
    }

    private fun shownSounds(): List<LibrarySound> = search?.sounds ?: when {
        state.value.content is SoundsContent.Starters -> starterSounds.map { it.toLibrarySound() }
        state.value.tab.listsCatalog -> feedSaved.sounds
        state.value.tab == SoundsTab.FAVORITES -> recentSounds + favoritesOnScreen
        else -> emptyList()
    }

    // The pack has no catalog row on a phone that never loaded the catalog: the bundled sound's own facts fill one in.
    private fun CatalogSound.toLibrarySound() = LibrarySound(
        id = id, title = title, categoryId = "", filePath = "", durationUs = durationUs, waveformPath = null,
        licenseType = credit, sourceUrl = "", credit = credit,
    )

    private fun tabOf(fromRecent: Boolean): SoundTab = if (fromRecent) SoundTab.RECENT else analyticsTab()

    private fun analyticsTab(): SoundTab = when {
        search != null -> SoundTab.SEARCH
        else -> when (state.value.tab) {
            SoundsTab.TRENDING -> SoundTab.TRENDING
            SoundsTab.LOCAL -> SoundTab.LOCAL
            SoundsTab.GLOBAL -> SoundTab.GLOBAL
            SoundsTab.FAVORITES -> SoundTab.FAVORITES
            SoundsTab.MINE -> SoundTab.MY_SOUNDS
        }
    }

    private fun showToast(toast: SoundsToast) {
        // In the picker a sound that won't play is the editor's toast, in its stage slot above the scrim (spec P3-05).
        if (mode == SoundsMode.PICKER && toast == SoundsToast.PLAY_FAILED) {
            _notices.trySend(toast)
            return
        }
        updateState { it.copy(toast = toast) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(if (toast == SoundsToast.VOLUME_LOW) VOLUME_TOAST_MILLIS else TOAST_MILLIS)
            updateState { it.copy(toast = null) }
        }
    }

    override fun onCleared() {
        preview.close()
        super.onCleared()
    }

    private data class SearchState(
        val sounds: List<LibrarySound>,
        val offline: Boolean,
        val endReached: Boolean,
        val nextPage: Int,
        val failed: Boolean,
    )

    private companion object {
        /** Quiet time after the last keystroke before the search starts (spec P3-02 → Search). */
        const val SEARCH_PAUSE_MILLIS = 300L

        /** A search that takes longer than this shows the "Searching…" strip. */
        const val SLOW_NOTICE_MILLIS = 300L

        /** The first load shows placeholder rows only when it takes longer than this. */
        const val PLACEHOLDER_DELAY_MILLIS = 300L
        const val TOAST_MILLIS = 4_000L

        /** The volume hint is short and gone before the sound ends (spec P3-02 → States). */
        const val VOLUME_TOAST_MILLIS = 2_000L

        // Trending and Local are tabs here, so the catalog's categories of the same name are not chips (spec Q2).
        val TAB_CATEGORY_IDS = setOf("sound_trending", "sound_local")
    }
}
