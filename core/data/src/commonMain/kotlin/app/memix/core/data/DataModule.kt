package app.memix.core.data

import app.memix.core.data.catalog.SqlDelightSoundCatalogRepository
import app.memix.core.data.catalog.SupabaseConfig
import app.memix.core.data.catalog.SupabaseSoundCatalogRemote
import app.memix.core.data.db.MemixDatabase
import app.memix.core.data.project.ProjectJsonCodec
import app.memix.core.data.project.ProjectJsonMigrator
import app.memix.core.data.project.SqlDelightProjectRepository
import app.memix.core.data.project.projectMigrations
import app.memix.core.data.settings.SettingsExportPreferences
import app.memix.core.data.stickerpack.FileStickerPackRepository
import app.memix.core.data.settings.SettingsPhotoExportPreferences
import app.memix.core.data.settings.SettingsVideoMemeNotePreferences
import app.memix.core.domain.AppConfig
import app.memix.core.domain.catalog.SoundCatalogRepository
import app.memix.core.domain.photo.PhotoExportPreferences
import app.memix.core.domain.photo.VideoMemeNotePreferences
import app.memix.core.domain.project.ProjectRepository
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
    single<SoundCatalogRepository> {
        val appConfig = get<AppConfig>()
        SqlDelightSoundCatalogRepository(
            database = lazy { get<MemixDatabase>() },
            // Read when the first request is made, not at start-up.
            remote = SupabaseSoundCatalogRemote(lazy { SupabaseConfig(appConfig.supabaseUrl, appConfig.supabasePublishableKey) }, logger = get()),
            appConfig = appConfig,
            clock = get(),
            ioDispatcher = Dispatchers.IO,
            logger = get(),
        )
    }
}
