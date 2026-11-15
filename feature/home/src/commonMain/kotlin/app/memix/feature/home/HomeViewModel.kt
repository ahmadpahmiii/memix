package app.memix.feature.home

import androidx.lifecycle.viewModelScope
import app.memix.core.domain.region.ObserveRegionUseCase
import app.memix.core.model.Region
import app.memix.core.ui.MemixViewModel
import kotlinx.coroutines.launch

data class HomeUiState(val region: Region = Region.Global)

/** Home has no user actions of its own yet; navigation callbacks live in the screen. Trending rows add intents later. */
sealed interface HomeIntent

class HomeViewModel(observeRegion: ObserveRegionUseCase) :
    MemixViewModel<HomeUiState, HomeIntent>(HomeUiState()) {

    init {
        // The confirmed region, else the phone's; follows the Region sheet's Save (P3-11).
        viewModelScope.launch { observeRegion().collect { region -> updateState { it.copy(region = region) } } }
    }

    override fun onIntent(intent: HomeIntent) = Unit
}
