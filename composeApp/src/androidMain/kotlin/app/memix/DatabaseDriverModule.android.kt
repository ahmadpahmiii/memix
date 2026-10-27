package app.memix

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.memix.core.data.MEMIX_DATABASE_FILE
import app.memix.core.data.db.MemixDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

// The driver opens the file lazily, on the first query, which the repository runs off the main thread.
internal actual val databaseDriverModule = module {
    single<SqlDriver> { AndroidSqliteDriver(MemixDatabase.Schema, androidContext(), MEMIX_DATABASE_FILE) }
}
