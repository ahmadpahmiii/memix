package app.memix.feature.onboarding

import androidx.compose.runtime.Immutable
import app.memix.core.domain.region.RegionNote
import app.memix.core.domain.region.RegionOption
import app.memix.core.model.AppLanguage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The Region sheet (spec P3-11). The fields below [visible] keep the last content while the sheet slides away, so it never
 * goes blank on the way out. [selectedRegionCode] and [language] are pending until Continue or Save.
 */
@Immutable
data class RegionSheetUiState(
    val visible: Boolean = false,
    /** First launch: the blocking variant with the hint and Continue; the first confirm logs `onboarding_complete`. */
    val firstRun: Boolean = false,
    val regions: ImmutableList<RegionOption> = persistentListOf(),
    val selectedRegionCode: String = "",
    val regionNote: RegionNote? = null,
    val language: AppLanguage = AppLanguage.Default,
    /** False where the platform cannot switch one app's language: the language shows as a read-only line. */
    val languageEditable: Boolean = false,
    /** Name of the phone's language when it is not one of the five (first launch). */
    val languageNote: String? = null,
    /** The region just saved (a later Save), spoken politely once; null otherwise. */
    val announcedRegionName: String? = null,
)

sealed interface RegionSheetIntent {
    /** From the Local tab's header and Home's region button. */
    data object Open : RegionSheetIntent
    data class SelectRegion(val regionCode: String) : RegionSheetIntent
    data class SelectLanguage(val language: AppLanguage) : RegionSheetIntent

    /** Continue or Save, and system back on the blocking first-launch sheet. */
    data object Confirm : RegionSheetIntent

    /** The polite announcement of a saved region was shown; it is not repeated. */
    data object AnnouncementShown : RegionSheetIntent

    /** Close, a scrim tap, a drag down, back and Escape on the standard sheet: nothing applies. */
    data object Dismiss : RegionSheetIntent
}
