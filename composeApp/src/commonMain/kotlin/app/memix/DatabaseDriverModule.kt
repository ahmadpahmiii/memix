package app.memix

import org.koin.core.module.Module

/**
 * Provides the SQLDelight `SqlDriver` for :core:data's database file. Opening a database is platform
 * code, and the composition root is where the app's platform entry code already lives, so :core:data
 * stays commonMain only (see TECHNICAL_DESIGN.md → Decisions).
 */
internal expect val databaseDriverModule: Module
