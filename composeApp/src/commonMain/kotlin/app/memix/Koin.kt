package app.memix

import app.memix.core.data.dataModule
import app.memix.core.domain.GetPhoneRegionUseCase
import app.memix.core.domain.RefreshAppConfigUseCase
import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.domain.media.LeftoverMediaCleaner
import app.memix.core.domain.media.SaveGalleryProjectUseCase
import app.memix.core.domain.project.CreateVideoProjectUseCase
import app.memix.core.domain.project.DeleteProjectUseCase
import app.memix.core.domain.project.GetProjectUseCase
import app.memix.core.domain.project.ObserveProjectSummariesUseCase
import app.memix.core.domain.project.OpenVideoDraftUseCase
import app.memix.core.domain.project.SaveProjectUseCase
import app.memix.core.domain.project.StartEditSessionUseCase
import app.memix.core.domain.video.StartPreviewUseCase
import app.memix.debug.DebugEdits
import app.memix.debug.ImportSaveFailure
import app.memix.engine.video.videoEngineModule
import app.memix.feature.home.HomeViewModel
import app.memix.feature.videoeditor.EditorDebugEdit
import app.memix.feature.videoeditor.VideoEditorViewModel
import app.memix.feature.videoeditor.importmedia.MediaImportViewModel
import app.memix.platform.services.platformServicesModule
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
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
    factoryOf(::OpenVideoDraftUseCase)
    factoryOf(::StartPreviewUseCase)
    factory { StartEditSessionUseCase(get(), get(), get(), get(appScope)) }
    // Created at start-up so it clears last run's leftover media in the background before any import copies.
    single(createdAtStart = true) { LeftoverMediaCleaner(get(), get(), get(), get(appScope)) }
    factoryOf(::ImportMediaUseCase)
    factoryOf(::SaveGalleryProjectUseCase)
    viewModelOf(::HomeViewModel)
    viewModelOf(::MediaImportViewModel)
    viewModel { params ->
        VideoEditorViewModel(
            projectId = params.get(),
            openVideoDraft = get(),
            startEditSession = get(),
            startPreview = get(),
            // Bound only in builds with hand checks (handCheckModule).
            debugEdit = getOrNull(),
        )
    }
}

// Debug and benchmark builds: the editor's test edit for the P1-07 hand checks (TECHNICAL_DESIGN → Debug hand checks),
// and the import's save that can be made to fail once for the P1-02 N2 check. Loaded after appModule, so its
// SaveGalleryProjectUseCase replaces the plain one.
private val handCheckModule = module {
    single<EditorDebugEdit> { DebugEdits.shortenLastClip }
    factory { SaveGalleryProjectUseCase(SaveProjectUseCase(ImportSaveFailure.around(get()), get()), get()) }
}

/**
 * Called once per process: from the Android Application and from the iOS App init. [handChecks] turns on the
 * hand-check hooks (Android debug and benchmark builds).
 */
fun initKoin(handChecks: Boolean = false, platformSetup: KoinAppDeclaration = {}) {
    startKoin {
        platformSetup()
        modules(platformServicesModule, databaseDriverModule, dataModule, videoEngineModule, appModule)
        if (handChecks) modules(handCheckModule)
    }
}
