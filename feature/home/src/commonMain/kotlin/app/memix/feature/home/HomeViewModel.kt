package app.memix.feature.home

import app.memix.core.domain.GetPhoneRegionUseCase
import app.memix.core.model.Region
import app.memix.core.ui.MemixViewModel

data class HomeUiState(val region: Region)

/** Home has no user actions of its own yet; navigation callbacks live in the screen. Trending rows add intents later. */
sealed interface HomeIntent

class HomeViewModel(getPhoneRegion: GetPhoneRegionUseCase) :
    MemixViewModel<HomeUiState, HomeIntent>(HomeUiState(region = getPhoneRegion())) {

    override fun onIntent(intent: HomeIntent) = Unit
}
