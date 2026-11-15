package app.memix.core.data.settings

import app.memix.core.domain.region.RegionPreferences
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [RegionPreferences] in the app's key-value settings (spec P3-11 → Entry and exit). Storage is read and written on
 * [ioDispatcher]. The language is not stored here: the platform keeps the app's language ([app.memix.core.domain.region.AppLanguageSwitch]).
 */
internal class SettingsRegionPreferences(
    private val settings: Settings,
    private val ioDispatcher: CoroutineDispatcher,
) : RegionPreferences {
    // Loaded from storage once; a confirm updates it, so a change can never fall between a read and a collector starting.
    private val region = MutableStateFlow<String?>(null)
    private val loadLock = Mutex()
    private var loaded = false

    override suspend fun savedRegionCode(): String? {
        load()
        return region.value
    }

    override suspend fun firstRunDone(): Boolean = withContext(ioDispatcher) { settings.getBoolean(FIRST_RUN_DONE, false) }

    override suspend fun confirm(regionCode: String) {
        withContext(ioDispatcher) {
            settings.putString(REGION_CODE, regionCode)
            settings.putBoolean(FIRST_RUN_DONE, true)
        }
        loadLock.withLock {
            region.value = regionCode
            loaded = true
        }
    }

    override fun observeRegionCode(): Flow<String?> = flow {
        load()
        emitAll(region)
    }

    private suspend fun load() = loadLock.withLock {
        if (loaded) return@withLock
        region.value = withContext(ioDispatcher) { settings.getStringOrNull(REGION_CODE) }
        loaded = true
    }

    // Keys are saved on phones: never rename them.
    private companion object {
        const val REGION_CODE = "region.code"
        const val FIRST_RUN_DONE = "region.firstRunDone"
    }
}
