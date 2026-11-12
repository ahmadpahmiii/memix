package app.memix.core.data

import app.memix.core.data.catalog.SqlDelightSoundCatalogRepository
import app.memix.core.data.catalog.SqlDelightSoundCollectionsRepository
import app.memix.core.data.supabase.SupabaseClientSource
import app.memix.core.data.supabase.SupabaseConfig
import app.memix.core.data.catalog.SupabaseSoundCatalogRemote
import app.memix.core.data.db.MemixDatabase
import app.memix.core.data.project.ProjectJsonCodec
import app.memix.core.data.project.ProjectJsonMigrator
import app.memix.core.data.project.SqlDelightProjectRepository
import app.memix.core.data.project.projectMigrations
import app.memix.core.data.report.SqlDelightSoundReports
import app.memix.core.data.report.SupabaseSoundReportRemote
import app.memix.core.data.settings.SettingsExportPreferences
import app.memix.core.data.sound.FileSoundDownloads
import app.memix.core.data.sound.KtorSoundFileSource
import app.memix.core.data.stickerpack.FileStickerPackRepository
import app.memix.core.data.settings.SettingsPhotoExportPreferences
import app.memix.core.data.settings.SettingsVideoMemeNotePreferences
import app.memix.core.domain.AppConfig
import app.memix.core.domain.catalog.SoundCatalogRepository
import app.memix.core.domain.photo.PhotoExportPreferences
import app.memix.core.domain.photo.VideoMemeNotePreferences
import app.memix.core.domain.project.ProjectRepository
import app.memix.core.domain.sound.SoundCollectionsRepository
import app.memix.core.domain.sound.SoundDownloads
import app.memix.core.domain.sound.SoundReports
import app.memix.core.domain.stickerpack.StickerPackRepository
import app.memix.core.domain.video.ExportPreferences
import app.memix.core.model.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.dsl.module

/** The app's one SQLite file. The app layer opens it with its platform's SqlDriver and [MemixDatabase.Schema]. */
const val MEMIX_DATABASE_FILE = "memix.db"

/**
 * Binds the domain's repository interfaces. Needs a `SqlDriver` for [MEMIX_DATABASE_FILE] and the app's key-value
 * `Settings` in the graph (the composition root creates both with its platform's storage), plus the platform's
 * `AppConfig`, `Logger` and a `Clock`.
 */
val dataModule = module {
    single<ExportPreferences> { SettingsExportPreferences(get(), Dispatchers.IO) }
    single<PhotoExportPreferences> { SettingsPhotoExportPreferences(get(), Dispatchers.IO) }
    single<VideoMemeNotePreferences> { SettingsVideoMemeNotePreferences(get(), Dispatchers.IO) }
    single<StickerPackRepository> { FileStickerPackRepository(get(), get()) }
    single { MemixDatabase(get()) }
    single<ProjectRepository> {
        SqlDelightProjectRepository(
            database = lazy { get<MemixDatabase>() },
            codec = ProjectJsonCodec(ProjectJsonMigrator(projectMigrations, Project.CURRENT_SCHEMA_VERSION)),
            ioDispatcher = Dispatchers.IO,
            logger = get(),
        )
    }
    // The one Supabase client of the catalog and the reports; the URL and key are read when the first request is made, not at start-up.
    single {
        val appConfig = get<AppConfig>()
        SupabaseClientSource(lazy { SupabaseConfig(appConfig.supabaseUrl, appConfig.supabasePublishableKey) })
    }
    single<SoundCatalogRepository> {
        SqlDelightSoundCatalogRepository(
            database = lazy { get<MemixDatabase>() },
            remote = SupabaseSoundCatalogRemote(get(), logger = get()),
            appConfig = get(),
            clock = get(),
            ioDispatcher = Dispatchers.IO,
            logger = get(),
        )
    }
    // Reports of library sounds (P3-10) and the phone's memory of them.
    single<SoundReports> {
        SqlDelightSoundReports(
            database = lazy { get<MemixDatabase>() },
            remote = SupabaseSoundReportRemote(get()),
            clock = get(),
            ioDispatcher = Dispatchers.IO,
            logger = get(),
        )
    }
    single<SoundCollectionsRepository> {
        SqlDelightSoundCollectionsRepository(database = lazy { get<MemixDatabase>() }, clock = get(), ioDispatcher = Dispatchers.IO)
    }
    // The downloaded sounds and their 500 MB cache (P3-04). Projects copy a sound when it lands (P3-05), so any file may go.
    single<SoundDownloads> {
        FileSoundDownloads(
            database = lazy { get<MemixDatabase>() },
            source = KtorSoundFileSource(),
            mediaFiles = get(),
            appConfig = get(),
            clock = get(),
            logger = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
}
