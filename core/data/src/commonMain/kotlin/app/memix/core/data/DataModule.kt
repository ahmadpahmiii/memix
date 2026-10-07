package app.memix.core.data

import app.memix.core.data.db.MemixDatabase
import app.memix.core.data.project.ProjectJsonCodec
import app.memix.core.data.project.ProjectJsonMigrator
import app.memix.core.data.project.SqlDelightProjectRepository
import app.memix.core.data.project.projectMigrations
import app.memix.core.domain.project.ProjectRepository
import app.memix.core.model.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.dsl.module

/** The app's one SQLite file. The app layer opens it with its platform's SqlDriver and [MemixDatabase.Schema]. */
const val MEMIX_DATABASE_FILE = "memix.db"

/** Binds the domain's repository interfaces. Needs a `SqlDriver` for [MEMIX_DATABASE_FILE] in the graph. */
val dataModule = module {
    single { MemixDatabase(get()) }
    single<ProjectRepository> {
        SqlDelightProjectRepository(
            database = lazy { get<MemixDatabase>() },
            codec = ProjectJsonCodec(ProjectJsonMigrator(projectMigrations, Project.CURRENT_SCHEMA_VERSION)),
            ioDispatcher = Dispatchers.IO,
            logger = get(),
        )
    }
}
