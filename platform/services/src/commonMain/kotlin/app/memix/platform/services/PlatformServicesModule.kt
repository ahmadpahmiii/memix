package app.memix.platform.services

import app.memix.core.domain.DeviceRegion
import app.memix.core.domain.Logger
import org.koin.dsl.module

val platformServicesModule = module {
    single<DeviceRegion> { PlatformDeviceRegion() }
    single<Logger> { PlatformLogger() }
}

internal expect class PlatformDeviceRegion() : DeviceRegion

internal expect class PlatformLogger() : Logger
