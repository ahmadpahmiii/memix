package app.memix

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.memix.core.data.MEMIX_DATABASE_FILE
import app.memix.core.data.db.MemixDatabase
import org.koin.dsl.module

// The framework is static, so the Xcode app links SQLite itself (-lsqlite3 in iosApp's OTHER_LDFLAGS).
internal actual val databaseDriverModule = module {
    single<SqlDriver> { NativeSqliteDriver(MemixDatabase.Schema, MEMIX_DATABASE_FILE) }
}
