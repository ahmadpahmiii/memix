package app.memix.feature.onboarding

import androidx.lifecycle.viewModelScope
import app.memix.core.designsystem.MemixMotion
import app.memix.core.domain.region.ConfirmRegionUseCase
import app.memix.core.domain.region.GetRegionSetupUseCase
import app.memix.core.domain.region.RegionChoice
import app.memix.core.domain.region.RegionSetup
import app.memix.core.model.AppLanguage
import app.memix.core.ui.MemixViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the Region sheet for the whole app: it opens by itself at the first launch, and the Local tab's header and Home's
 * region button send [RegionSheetIntent.Open]. The sheet is placed over the tabs by the app root.
 *
 * What it keeps and logs is [ConfirmRegionUseCase]'s job; this class decides what the sheet shows and when it closes.
 */
class RegionSheetViewModel(
    private val getSetup: GetRegionSetupUseCase,
    private val confirmRegion: ConfirmRegionUseCase,
) : MemixViewModel<RegionSheetUiState, RegionSheetIntent>(RegionSheetUiState()) {
    private var languageWhenOpened = AppLanguage.Default
    private var confirming = false
    private var setupJob: Job? = null

    init {
        // The first-launch step: shown once, before the person has confirmed anything (spec P3-11 → Entry and exit).
        setupJob = viewModelScope.launch {
            val setup = getSetup()
            if (setup.firstRun) show(setup)
        }
    }

    override fun onIntent(intent: RegionSheetIntent) = when (intent) {
        RegionSheetIntent.Open -> open()
        is RegionSheetIntent.SelectRegion -> updateState { it.copy(selectedRegionCode = intent.regionCode) }
        is RegionSheetIntent.SelectLanguage -> updateState { if (it.languageEditable) it.copy(language = intent.language) else it }
        RegionSheetIntent.Confirm -> confirm()
        RegionSheetIntent.Dismiss -> updateState { it.copy(visible = false) }
        RegionSheetIntent.AnnouncementShown -> updateState { it.copy(announcedRegionName = null) }
    }

    private fun open() {
        if (state.value.visible || confirming || setupJob?.isActive == true) return
        setupJob = viewModelScope.launch { show(getSetup()) }
    }

    private fun show(setup: RegionSetup) {
        languageWhenOpened = setup.language
        updateState {
            RegionSheetUiState(
                visible = true,
                firstRun = setup.firstRun,
                regions = setup.regions.toImmutableList(),
                selectedRegionCode = setup.selectedRegionCode,
                regionNote = setup.regionNote,
                language = setup.language,
                languageEditable = setup.canChangeLanguage,
                languageNote = setup.languageNote,
            )
        }
    }

    private fun confirm() {
        val shown = state.value
        if (!shown.visible || confirming) return
        confirming = true
        val choice = RegionChoice(shown.language, shown.selectedRegionCode)
        updateState { it.copy(visible = false) }
        viewModelScope.launch {
            // A new language redraws every screen, so the sheet finishes closing first (spec P3-11 → States).
            if (choice.language != languageWhenOpened) delay(MemixMotion.durationSheet.toLong())
            try {
                confirmRegion(choice)
                // Asked again so the name is written in the language just chosen.
                if (!shown.firstRun) announce(choice.regionCode)
            } finally {
                confirming = false
            }
        }
    }

    private suspend fun announce(regionCode: String) {
        val name = getSetup().regions.firstOrNull { it.code == regionCode }?.name
        updateState { it.copy(announcedRegionName = name) }
    }
}
