package app.memix.platform.services

import org.koin.core.module.Module

/** Binds the domain's platform interfaces (DeviceRegion, Logger, AppConfig, Analytics, MediaFiles, MediaInspector, SoundLibrary, LocalCalendar, MediaVolume and the export services) to this platform's implementations. */
expect val platformServicesModule: Module
