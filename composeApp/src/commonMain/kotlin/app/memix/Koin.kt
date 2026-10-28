package app.memix

import app.memix.core.data.dataModule
import app.memix.core.designsystem.readCaptionFontFile
import app.memix.core.domain.GetPhoneRegionUseCase
import app.memix.core.domain.RefreshAppConfigUseCase
import app.memix.core.domain.drafts.DeleteDraftUseCase
import app.memix.core.domain.drafts.DescribeEditTimeUseCase
import app.memix.core.domain.drafts.DuplicateDraftUseCase
import app.memix.core.domain.drafts.ReadDraftThumbnailUseCase
import app.memix.core.domain.drafts.RenameDraftUseCase
import app.memix.core.domain.drafts.UpdateDraftThumbnailUseCase
import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.domain.media.LeftoverMediaCleaner
import app.memix.core.domain.media.SaveGalleryPhotoProjectUseCase
import app.memix.core.domain.media.SaveGalleryProjectUseCase
import app.memix.core.domain.project.CreatePhotoProjectUseCase
import app.memix.core.domain.project.CreateVideoProjectUseCase
import app.memix.core.domain.project.DeleteProjectUseCase
import app.memix.core.domain.project.GetProjectUseCase
import app.memix.core.domain.project.ObserveProjectSummariesUseCase
import app.memix.core.domain.project.OpenPhotoDraftUseCase
import app.memix.core.domain.project.OpenVideoDraftUseCase
import app.memix.core.domain.project.SaveProjectUseCase
import app.memix.core.domain.project.StartEditSessionUseCase
import app.memix.core.domain.share.ShareVideoUseCase
import app.memix.core.domain.sound.LoadStarterSoundsUseCase
import app.memix.core.domain.sound.LogSoundEventsUseCase
import app.memix.core.domain.sound.OpenSoundPlayerUseCase
import app.memix.core.domain.sound.PrepareSoundUseCase
import app.memix.core.domain.text.CaptionFontSource
import app.memix.core.domain.text.MeasureCaptionUseCase
import app.memix.core.domain.text.PrepareCaptionsUseCase
import app.memix.core.domain.video.ExportNotificationPermissionUseCase
import app.memix.core.domain.video.ExportSheetFactsUseCase
import app.memix.core.domain.video.ExportVideoUseCase
import app.memix.core.domain.video.GetExportCapabilitiesUseCase
import app.memix.core.domain.video.OpenResultPlayerUseCase
import app.memix.core.domain.video.RecoverUnfinishedExportUseCase
import app.memix.core.domain.video.StartPreviewUseCase
import app.memix.core.domain.video.StartThumbnailsUseCase
import app.memix.debug.DebugEdits
import app.memix.debug.EditorSaveDiffLog
import app.memix.debug.ImportSaveFailure
import app.memix.engine.photo.photoEngineModule
import app.memix.engine.video.HAND_CHECKS_PROPERTY
import app.memix.engine.video.videoEngineModule
import app.memix.feature.drafts.DraftsViewModel
import app.memix.feature.export.ExportViewModel
import app.memix.feature.home.HomeViewModel
import app.memix.feature.videoeditor.EditorCaptionRendering
import app.memix.feature.videoeditor.EditorDebugEdit
import app.memix.feature.videoeditor.EditorExport
import app.memix.feature.videoeditor.EditorSounds
import app.memix.feature.videoeditor.VideoEditorViewModel
import app.memix.feature.photoeditor.PhotoEditorViewModel
import app.memix.feature.videoeditor.importmedia.MediaImportViewModel
import app.memix.feature.videoeditor.importmedia.PhotoMemeImportViewModel
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
    factoryOf(::CreatePhotoProjectUseCase)
    factoryOf(::SaveProjectUseCase)
    factoryOf(::GetProjectUseCase)
    factoryOf(::DeleteProjectUseCase)
    factoryOf(::ObserveProjectSummariesUseCase)
    factoryOf(::OpenVideoDraftUseCase)
    factoryOf(::OpenPhotoDraftUseCase)
    factoryOf(::StartPreviewUseCase)
    factoryOf(::StartThumbnailsUseCase)
    factory { StartEditSessionUseCase(get(), get(), get(), get(appScope)) }
    // Created at start-up so it clears last run's leftover media in the background before any import copies.
    single(createdAtStart = true) { LeftoverMediaCleaner(get(), get(), get(), get(), get(appScope)) }
    factoryOf(::ImportMediaUseCase)
    factoryOf(::SaveGalleryProjectUseCase)
    factoryOf(::SaveGalleryPhotoProjectUseCase)
    // Drafts list (P1-14). The thumbnail writer and the delete with undo outlive every screen: singles in the app's scope.
    single { UpdateDraftThumbnailUseCase(get(), get(), get(), get(), get(appScope)) }
    single { DeleteDraftUseCase(get(), get(), get(), get(appScope)) }
    factoryOf(::RenameDraftUseCase)
    factoryOf(::DuplicateDraftUseCase)
    factoryOf(::DescribeEditTimeUseCase)
    factoryOf(::ReadDraftThumbnailUseCase)
    factoryOf(::LoadStarterSoundsUseCase)
    factoryOf(::PrepareSoundUseCase)
    factoryOf(::OpenSoundPlayerUseCase)
    factoryOf(::LogSoundEventsUseCase)
    factoryOf(::EditorSounds)
    // The caption fonts live with the interface fonts in the design system; the engine's renderer reads them (P1-10).
    single<CaptionFontSource> { CaptionFontSource(::readCaptionFontFile) }
    factoryOf(::PrepareCaptionsUseCase)
    factoryOf(::MeasureCaptionUseCase)
    factoryOf(::EditorCaptionRendering)
    // The one export at a time outlives every screen (the Exporting screen, the editor, the notification), so it's a
    // single in the app's scope (P1-12). Its state moves on the main thread.
    single { ExportVideoUseCase(get(), get(), get(), get(), get(appScope), Dispatchers.Main) }
    factoryOf(::GetExportCapabilitiesUseCase)
    factoryOf(::ExportSheetFactsUseCase)
    factoryOf(::ExportNotificationPermissionUseCase)
    factoryOf(::RecoverUnfinishedExportUseCase)
    factoryOf(::OpenResultPlayerUseCase)
    factoryOf(::ShareVideoUseCase)
    factoryOf(::EditorExport)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ExportViewModel)
    viewModelOf(::MediaImportViewModel)
    viewModelOf(::PhotoMemeImportViewModel)
    viewModel { DraftsViewModel(get(), get(), get(), get(), get(), get(), decodeDispatcher = Dispatchers.Default) }
    viewModel { params ->
        PhotoEditorViewModel(
            projectId = params.get(),
            newBlank = params.get(),
            openPhotoDraft = get(),
            createPhotoProject = get(),
            startEditSession = get(),
            updateDraftThumbnail = get(),
        )
    }
    viewModel { params ->
        VideoEditorViewModel(
            projectId = params.get(),
            openVideoDraft = get(),
            startEditSession = get(),
            startPreview = get(),
            startThumbnails = get(),
            sounds = get(),
            captionRendering = get(),
            export = get(),
            importMedia = get(),
            updateDraftThumbnail = get(),
            decodeDispatcher = Dispatchers.Default,
            // Bound only in builds with hand checks (handCheckModule).
            debugEdit = getOrNull(),
        )
    }
}

// Debug and benchmark builds: the editor's test edit for the P1-07 hand checks (TECHNICAL_DESIGN → Debug hand checks),
// the import's save that can be made to fail once for the P1-02 N2 check, and the editor saves' change log for the
// P1-16 Replace check. Loaded after appModule, so its SaveGalleryProjectUseCase and StartEditSessionUseCase replace
// the plain ones.
private val handCheckModule = module {
    single<EditorDebugEdit> { DebugEdits.shortenLastClip }
    factory { SaveGalleryProjectUseCase(SaveProjectUseCase(ImportSaveFailure.around(get()), get()), get(), get()) }
    factory { StartEditSessionUseCase(SaveProjectUseCase(EditorSaveDiffLog.around(get(), get()), get()), get(), get(), get(appScope)) }
}

/**
 * Called once per process: from the Android Application and from the iOS App init. [handChecks] turns on the
 * hand-check hooks (Android debug and benchmark builds).
 */
fun initKoin(handChecks: Boolean = false, platformSetup: KoinAppDeclaration = {}) {
    startKoin {
        platformSetup()
        properties(mapOf(HAND_CHECKS_PROPERTY to handChecks))
        modules(platformServicesModule, databaseDriverModule, settingsModule, dataModule, videoEngineModule, photoEngineModule, appModule)
        if (handChecks) modules(handCheckModule)
    }
}
