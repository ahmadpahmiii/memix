package app.memix

import app.memix.core.data.dataModule
import app.memix.core.domain.GetPhoneRegionUseCase
import app.memix.core.domain.RefreshAppConfigUseCase
import app.memix.core.domain.project.CreateVideoProjectUseCase
import app.memix.core.domain.project.DeleteProjectUseCase
import app.memix.core.domain.project.GetProjectUseCase
import app.memix.core.domain.project.ObserveProjectSummariesUseCase
import app.memix.core.domain.project.SaveProjectUseCase
import app.memix.core.domain.project.StartEditSessionUseCase
import app.memix.engine.video.videoEngineModule
import app.memix.feature.home.HomeViewModel
import app.memix.platform.services.platformServicesModule
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

// Outlives every screen, so the save an editor starts as it closes still finishes (P1-07).
private val appScope = named("appScope")

private val appModule = module {
    single<Clock> { Clock.System }
    single(appScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    factoryOf(::GetPhoneRegionUseCase)
    factoryOf(::RefreshAppConfigUseCase)
    factoryOf(::CreateVideoProjectUseCase)
    factoryOf(::SaveProjectUseCase)
    factoryOf(::GetProjectUseCase)
    factoryOf(::DeleteProjectUseCase)
    factoryOf(::ObserveProjectSummariesUseCase)
    factory { StartEditSessionUseCase(get(), get(appScope)) }
    viewModelOf(::HomeViewModel)
}

/** Called once per process: from the Android Application and from the iOS App init. */
fun initKoin(platformSetup: KoinAppDeclaration = {}) {
    startKoin {
        platformSetup()
        modules(platformServicesModule, databaseDriverModule, dataModule, videoEngineModule, appModule)
    }
}
