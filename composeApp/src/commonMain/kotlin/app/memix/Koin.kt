package app.memix

import app.memix.core.domain.GetPhoneRegionUseCase
import app.memix.core.domain.RefreshAppConfigUseCase
import app.memix.feature.home.HomeViewModel
import app.memix.platform.services.platformServicesModule
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

private val appModule = module {
    factoryOf(::GetPhoneRegionUseCase)
    factoryOf(::RefreshAppConfigUseCase)
    viewModelOf(::HomeViewModel)
}

/** Called once per process: from the Android Application and from the iOS App init. */
fun initKoin(platformSetup: KoinAppDeclaration = {}) {
    startKoin {
        platformSetup()
        modules(platformServicesModule, appModule)
    }
}
