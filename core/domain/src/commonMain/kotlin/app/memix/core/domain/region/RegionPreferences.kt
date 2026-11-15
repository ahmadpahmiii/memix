package app.memix.core.domain.region

import kotlinx.coroutines.flow.Flow

/** The region the user confirmed in the Region sheet, and whether the first-launch step is done. Kept on the phone only. */
interface RegionPreferences {
    /** The confirmed region code, or null until the first confirm. Safe to call from any thread. */
    suspend fun savedRegionCode(): String?

    /** False until the first confirm; true forever after (also after an update), so the first-launch sheet shows once. */
    suspend fun firstRunDone(): Boolean

    /** Stores the confirmed region and marks the first run done. */
    suspend fun confirm(regionCode: String)

    /** The saved region code now (null before the first confirm), then every code confirmed later. */
    fun observeRegionCode(): Flow<String?>
}
