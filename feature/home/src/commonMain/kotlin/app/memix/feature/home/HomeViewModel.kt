package app.memix.feature.home

import app.memix.core.domain.GetPhoneRegionUseCase
import app.memix.core.model.Region
import app.memix.core.ui.MemixViewModel

data class HomeUiState(val region: Region)

sealed interface HomeIntent

class HomeViewModel(getPhoneRegion: GetPhoneRegionUseCase) :
    MemixViewModel<HomeUiState, HomeIntent>(HomeUiState(region = getPhoneRegion())) {

    override fun onIntent(intent: HomeIntent) = Unit
}
