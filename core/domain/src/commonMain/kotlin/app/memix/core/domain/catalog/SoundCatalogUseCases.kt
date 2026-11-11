package app.memix.core.domain.catalog

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.sound.SoundDownloads
import app.memix.core.model.catalog.LibrarySound
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.model.catalog.SoundCategory
import app.memix.core.model.catalog.SoundFeed
import app.memix.core.model.catalog.SoundFeedContent
import app.memix.core.model.catalog.Waveform
import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.Project
import kotlinx.coroutines.flow.Flow

/** The category chips from the phone's copy (P3-01). */
class ObserveSoundCategoriesUseCase(private val repository: SoundCatalogRepository) {
    operator fun invoke(): Flow<List<SoundCategory>> = repository.observeCategories()
}

/** Refreshes the category chips when they are stale; [force] is the pull-to-refresh. */
class RefreshSoundCategoriesUseCase(private val repository: SoundCatalogRepository) {
    suspend operator fun invoke(force: Boolean = false): Outcome<Unit> = repository.refreshCategories(force)
}

/** One Sounds-screen list from the phone's copy; shows at once, offline included. */
class ObserveSoundFeedUseCase(private val repository: SoundCatalogRepository) {
    operator fun invoke(feed: SoundFeed): Flow<SoundFeedContent> = repository.observeFeed(feed)
}

/** Brings one page of a feed up to date from the network; see [SoundCatalogRepository.loadFeedPage]. */
class LoadSoundFeedPageUseCase(private val repository: SoundCatalogRepository) {
    suspend operator fun invoke(feed: SoundFeed, pageIndex: Int, force: Boolean = false): Outcome<Unit> =
        repository.loadFeedPage(feed, pageIndex, force)
}

/** The waveform for a playing row or the details sheet; null means draw the rail. */
class LoadWaveformUseCase(private val repository: SoundCatalogRepository) {
    suspend operator fun invoke(sound: LibrarySound): Waveform? = repository.waveformOf(sound)
}

/** The sounds the catalog has dropped, for the "No longer available" row look (P3-04). */
class ObserveRemovedSoundIdsUseCase(private val repository: SoundCatalogRepository) {
    operator fun invoke(): Flow<Set<String>> = repository.observeRemovedSoundIds()
}

/**
 * Asks the catalog whether these saved sounds (favorites, recents) are still listed. A sound that is not gets its
 * downloaded copy deleted, unless it is playing: the copy is no longer ours to keep (a project that added it has its own). The
 * check is best effort and says nothing when the catalog can't be reached.
 */
class CheckRemovedSoundsUseCase(private val repository: SoundCatalogRepository, private val downloads: SoundDownloads) {
    suspend operator fun invoke(soundIds: Set<String>, force: Boolean = false) {
        if (soundIds.isEmpty()) return
        val removed = (repository.checkStillListed(soundIds, force) as? Outcome.Success)?.value ?: return
        if (removed.isNotEmpty()) downloads.discardUnused(removed)
    }
}

/** Fewest characters that start a search; one character would match nearly everything. */
const val MIN_SEARCH_LENGTH = 2

private const val MAX_SEARCH_LENGTH = 100

/** One page of search results. [offline] is true when they come from the sounds saved on the phone. */
data class SoundSearchPage(
    val sounds: List<LibrarySound>,
    val offline: Boolean,
    val endReached: Boolean,
)

/**
 * The search box. Online it is the typo-tolerant catalog search; when the catalog can't be reached it falls back to a
 * contains-match over the sounds saved on the phone (PM rule, 10 Nov 2026), and says so with [SoundSearchPage.offline].
 * Any other failure is returned as it is, so the screen can show its error.
 */
class SearchSoundsUseCase(private val repository: SoundCatalogRepository) {
    /** [savedSoundIds] are the sounds whose audio is on the phone (starter pack, downloads, My sounds). */
    suspend operator fun invoke(
        query: String,
        regionCode: String?,
        pageIndex: Int,
        savedSoundIds: Set<String>,
    ): Outcome<SoundSearchPage> {
        val cleanQuery = query.trim().take(MAX_SEARCH_LENGTH)
        if (cleanQuery.length < MIN_SEARCH_LENGTH) return Outcome.Success(SoundSearchPage(emptyList(), offline = false, endReached = true))
        return when (val online = repository.search(cleanQuery, regionCode, pageIndex)) {
            is Outcome.Success -> Outcome.Success(
                SoundSearchPage(online.value, offline = false, endReached = online.value.size < SOUND_PAGE_SIZE),
            )
            is Outcome.Failure -> when (online.error) {
                AppError.Offline -> searchSaved(cleanQuery, pageIndex, savedSoundIds)
                else -> online
            }
        }
    }

    // The saved sounds fit one page, so a later page is empty rather than a repeat of the first.
    private suspend fun searchSaved(query: String, pageIndex: Int, savedSoundIds: Set<String>): Outcome<SoundSearchPage> {
        val sounds = if (pageIndex == 0) repository.searchSaved(query, savedSoundIds) else emptyList()
        return Outcome.Success(SoundSearchPage(sounds, offline = true, endReached = true))
    }
}

/**
 * The titles that label a project's library sounds on the timeline, which the project does not store (it keeps the catalog
 * id). Looks up, in the phone's copy of the catalog, every catalog sound the project uses except those in [known] (the
 * starter pack, whose titles are bundled). A sound the phone's copy lacks stays unlabelled. Phone only: works offline.
 */
class LoadSoundTitlesUseCase(private val repository: SoundCatalogRepository) {
    suspend operator fun invoke(project: Project, known: Set<String>): Map<String, LocalizedText> {
        val ids = project.video?.tracks.orEmpty().flatMap { it.items }
            .mapNotNull { ((it as? AudioClip)?.source?.origin as? MediaOrigin.CatalogItem)?.itemId }
            .filterNotTo(HashSet()) { it in known }
        return if (ids.isEmpty()) emptyMap() else repository.titlesOf(ids)
    }
}
