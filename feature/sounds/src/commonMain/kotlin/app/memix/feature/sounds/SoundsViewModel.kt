package app.memix.feature.sounds

import androidx.lifecycle.viewModelScope
import app.memix.core.domain.AppError
import app.memix.core.domain.GetPhoneRegionUseCase
import app.memix.core.domain.Outcome
import app.memix.core.domain.SoundTab
import app.memix.core.domain.catalog.LoadSoundFeedPageUseCase
import app.memix.core.domain.catalog.LoadWaveformUseCase
import app.memix.core.domain.catalog.MIN_SEARCH_LENGTH
import app.memix.core.domain.catalog.ObserveSoundCategoriesUseCase
import app.memix.core.domain.catalog.ObserveSoundFeedUseCase
import app.memix.core.domain.catalog.RefreshSoundCategoriesUseCase
import app.memix.core.domain.catalog.SearchSoundsUseCase
import app.memix.core.domain.catalog.SoundSearchPage
import app.memix.core.domain.sound.GetSavedSoundIdsUseCase
import app.memix.core.domain.sound.LogSoundEventsUseCase
import app.memix.core.domain.sound.LowVolumeWarningUseCase
import app.memix.core.domain.sound.ObserveFavoriteSoundIdsUseCase
import app.memix.core.domain.sound.ObserveFavoriteSoundsUseCase
import app.memix.core.domain.sound.ObserveRecentSoundsUseCase
import app.memix.core.domain.sound.OpenSoundPlayerUseCase
import app.memix.core.domain.sound.PrepareSoundUseCase
import app.memix.core.domain.sound.SoundPlayer
import app.memix.core.domain.sound.ToggleFavoriteSoundUseCase
import app.memix.core.model.Region
import app.memix.core.model.catalog.LibrarySound
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.model.catalog.SoundFeed
import app.memix.core.model.catalog.SoundFeedContent
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import app.memix.core.ui.MemixViewModel
import app.memix.core.ui.soundLengthText
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val prepareSound: PrepareSoundUseCase,
    private val openSoundPlayer: OpenSoundPlayerUseCase,
    private val waveformOf: LoadWaveformUseCase,
    private val lowVolumeWarning: LowVolumeWarningUseCase,
    private val logEvents: LogSoundEventsUseCase,
    tabExit: SoundsTabExit,
) : MemixViewModel<SoundsUiState, SoundsIntent>(SoundsUiState()) {
    private val regionCode: String?

    /** Where the playing sound is; the screen reads it in draw code, 30 times a second at most. */
    val clock = PlaybackClock { player?.positionUs() ?: 0L }

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
    private var categoryNames: Map<String, LocalizedText> = emptyMap()
    // The open details sheet: its sound, and the `tab` a play from it is logged with (the screen that opened it).
    private var detailsSound: LibrarySound? = null
    private var detailsFromRecent = false
    private var detailsTab = SoundTab.TRENDING
    // A sound's file once it has been checked, so the next tap starts without measuring it again (300 ms budget).
    private val preparedSounds = HashMap<String, MediaRef>()
    private val waveformRequests = HashSet<String>()
    // A sound played again right after it ended or stopped is a replay: `sound_preview` counts only a new sound (spec P3-03 → Analytics).
    private var lastPreviewedId: String? = null

    private var feedJob: Job? = null
    private var moreJob: Job? = null
    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private var previewJob: Job? = null
    private var toastJob: Job? = null
    private var placeholderJob: Job? = null
    private var playerJobs: List<Job> = emptyList()
    private var player: SoundPlayer? = null

    init {
        val region = getPhoneRegion()
        regionCode = (region as? Region.Country)?.code
        updateState { it.copy(regionName = (region as? Region.Country)?.displayName) }
        // Rotation keeps the search and the preview; only a real move to another bottom-nav item ends them.
        viewModelScope.launch { tabExit.left.collect { leaveScreen() } }
    }

    // The first time the tab shows: nothing is read or fetched at app start, because this ViewModel is made with the tabs.
    private fun screenShown() {
        openedPlayer().warmUp()
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
        viewModelScope.launch { refreshCategories(force = false) }
        showFeed()
    }

    private fun observeCollections() {
        viewModelScope.launch {
            observeFavoriteIds().collect { ids -> updateState { it.copy(favoriteIds = ids.toImmutableSet()) } }
        }
        viewModelScope.launch {
            observeFavorites().collect { favorites ->
                latestFavorites = favorites
                val onFavorites = state.value.tab == SoundsTab.FAVORITES
                favoritesOnScreen = if (onFavorites) keepUnfavoritedRows(favorites, favoritesOnScreen) else favorites
                if (onFavorites) render()
            }
        }
        viewModelScope.launch {
            observeRecents().collect { recents ->
                recentSounds = recents
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
            SoundsIntent.SourceOpenFailed -> showToast(SoundsToast.SOURCE_FAILED)
            SoundsIntent.FocusReturned -> dropFocusReturn()
            SoundsIntent.ScreenShown -> screenShown()
            is SoundsIntent.ToggleFavorite -> viewModelScope.launch { toggleFavorite(intent.soundId) }
            SoundsIntent.LoadMore -> loadMore()
            SoundsIntent.TryAgain -> tryAgain()
            SoundsIntent.Refresh -> refresh()
            SoundsIntent.AppStopped -> backgrounded()
            SoundsIntent.LeftScreen -> leaveScreen()
        }
    }

    // A pending focus request must not fire later, when the user has moved on (a new tab, a typed query).
    private fun dropFocusReturn() {
        if (state.value.focusReturn != null) updateState { it.copy(focusReturn = null) }
    }

    private fun selectTab(tab: SoundsTab) {
        if (tab == state.value.tab) return
        updateState { it.copy(tab = tab, categoryId = null) }
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
    private fun backgrounded() {
        previewJob?.cancel()
        closePlayer()
    }

    // The player stays for the next visit (building an ExoPlayer on every tab entry is main-thread work); a sound stops.
    private fun leaveScreen() {
        previewJob?.cancel()
        player?.stop()
        lastPreviewedId = null
        detailsSound = null
        updateState { it.copy(details = null, focusReturn = null) }
        // Leaving the screen ends the visit: an un-hearted row is gone when the user comes back (spec P3-02 → Tab contents).
        favoritesOnScreen = latestFavorites
        if (state.value.query.isNotEmpty()) changeQuery("")
        else if (state.value.tab == SoundsTab.FAVORITES) render()
    }

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
            saved.loadedPages == 0 ->
                if (feedError != null) SoundsContent.LoadError else SoundsContent.Loading(showPlaceholders = placeholdersDue)
            saved.sounds.isEmpty() -> SoundsContent.Empty(emptyFeedReason(ui))
            else -> SoundsContent.Rows(saved.sounds.map { it.toRow() }.toImmutableList(), footer(saved.endReached))
        }
    }

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
        feedOffline && content !is SoundsContent.LoadError -> SoundsNotice.OFFLINE
        else -> null
    }

    private fun LibrarySound.toRow() = SoundItemUi(id, title, soundLengthText(durationUs), creditLine, durationUs)

    // ---- Previews ----

    // The disc: pause what plays, carry on what is paused, otherwise start this sound.
    private fun togglePreview(soundId: String, tab: SoundTab) {
        when (state.value.playbackOf(soundId)) {
            RowPlayback.PLAYING -> player?.pause()
            RowPlayback.PAUSED -> player?.resume()
            RowPlayback.IDLE -> startPreview(soundId, tab)
        }
    }

    private fun startPreview(soundId: String, tab: SoundTab) {
        val sound = shownSounds().firstOrNull { it.id == soundId } ?: detailsSound?.takeIf { it.id == soundId } ?: return
        loadWaveform(sound)
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            // TODO(P3-04): a sound that is not on the phone downloads here first; until then only the starter pack plays.
            val file = preparedFile(sound) ?: return@launch showToast(SoundsToast.PLAY_FAILED)
            // The sound plays anyway; the toast is a hint that a silent phone is not a broken app.
            if (lowVolumeWarning.isDue()) showToast(SoundsToast.VOLUME_LOW)
            openedPlayer().play(file)
            if (soundId != lastPreviewedId) logEvents.previewed(soundId, tab)
            lastPreviewedId = soundId
        }
    }

    private suspend fun preparedFile(sound: LibrarySound): MediaRef? {
        preparedSounds[sound.id]?.let { return it }
        return when (val prepared = prepareSound(sound.toCatalogSound())) {
            is Outcome.Success -> prepared.value.also {
                if (preparedSounds.size >= MAX_PREPARED_SOUNDS) preparedSounds.clear()
                preparedSounds[sound.id] = it
            }
            is Outcome.Failure -> null
        }
    }

    // The peaks arrive after the sound starts when they have to be fetched; until then the row shows the rail.
    // A failed load saves nothing, so the next play asks again.
    private fun loadWaveform(sound: LibrarySound) {
        if (sound.waveformPath == null || sound.id in state.value.waveforms || !waveformRequests.add(sound.id)) return
        viewModelScope.launch {
            val waveform = waveformOf(sound)
            waveformRequests.remove(sound.id)
            if (waveform == null) return@launch
            updateState { it.copy(waveforms = (it.waveforms + (sound.id to waveform.peaks)).toImmutableMap()) }
        }
    }

    // ---- Details sheet ----

    private fun openDetails(soundId: String, fromRecent: Boolean) {
        val sound = shownSounds().firstOrNull { it.id == soundId } ?: return
        detailsSound = sound
        detailsFromRecent = fromRecent
        detailsTab = tabOf(fromRecent)
        loadWaveform(sound)
        viewModelScope.launch {
            val onPhone = if (sound.id in getSavedSoundIds()) SoundOnPhone.INCLUDED else SoundOnPhone.NOT_SAVED
            // Closed (or another sound opened) while the saved ids were read: nothing to show.
            if (detailsSound?.id != sound.id) return@launch
            updateState { it.copy(details = sound.toDetails(onPhone)) }
        }
    }

    private fun closeDetails() {
        val sound = detailsSound ?: return
        detailsSound = null
        updateState { it.copy(details = null, focusReturn = RowFocusTarget(sound.id, detailsFromRecent)) }
    }

    private fun LibrarySound.toDetails(onPhone: SoundOnPhone): SoundDetailsUi {
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
            onPhone = onPhone,
        )
    }

    private fun shownSounds(): List<LibrarySound> = search?.sounds ?: when {
        state.value.tab.listsCatalog -> feedSaved.sounds
        state.value.tab == SoundsTab.FAVORITES -> recentSounds + favoritesOnScreen
        else -> emptyList()
    }

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

    private fun openedPlayer(): SoundPlayer = player ?: openSoundPlayer().also { opened ->
        player = opened
        playerJobs = listOf(
            viewModelScope.launch { opened.playing.collect { media -> updateState { it.copy(playingId = media?.catalogId()) } } },
            viewModelScope.launch { opened.paused.collect { media -> updateState { it.copy(pausedId = media?.catalogId()) } } },
            // A file that would not play (deleted, damaged) is checked again on the next tap instead of being trusted.
            viewModelScope.launch {
                opened.failures.collect { media ->
                    media.catalogId()?.let { preparedSounds.remove(it) }
                    showToast(SoundsToast.PLAY_FAILED)
                }
            },
        )
    }

    private fun MediaRef.catalogId(): String? = (origin as? MediaOrigin.CatalogItem)?.itemId

    // The player is opened on the first tap on the tab and lives only while the user is on this screen.
    private fun closePlayer() {
        playerJobs.forEach { it.cancel() }
        playerJobs = emptyList()
        player?.close()
        player = null
        updateState { it.copy(playingId = null, pausedId = null) }
    }

    private fun showToast(toast: SoundsToast) {
        updateState { it.copy(toast = toast) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(if (toast == SoundsToast.VOLUME_LOW) VOLUME_TOAST_MILLIS else TOAST_MILLIS)
            updateState { it.copy(toast = null) }
        }
    }

    override fun onCleared() {
        player?.close()
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

        /** Prepared files kept for quick replays; far more than a visit touches, so the map stays small. */
        const val MAX_PREPARED_SOUNDS = 100

        /** The volume hint is short and gone before the sound ends (spec P3-02 → States). */
        const val VOLUME_TOAST_MILLIS = 2_000L

        // Trending and Local are tabs here, so the catalog's categories of the same name are not chips (spec Q2).
        val TAB_CATEGORY_IDS = setOf("sound_trending", "sound_local")
    }
}
