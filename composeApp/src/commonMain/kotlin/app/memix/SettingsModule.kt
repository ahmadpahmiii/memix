package app.memix

import org.koin.core.module.Module

/**
 * Provides the app's key-value `Settings` (multiplatform-settings) for :core:data's small preferences, such as the
 * export settings (P1-12): each platform's own store, created here like the database driver.
 */
internal expect val settingsModule: Module
