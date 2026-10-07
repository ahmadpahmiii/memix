package app.memix.platform.services

import app.memix.core.domain.DeviceRegion
import app.memix.core.domain.Logger
import org.koin.dsl.module
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale

actual val platformServicesModule = module {
    single<DeviceRegion> { IosDeviceRegion() }
    single<Logger> { IosLogger() }
}

// iOS keeps the system region in currentLocale even when the app has its own language.
private class IosDeviceRegion : DeviceRegion {
    override fun regionCode(): String? = NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String

    override fun displayName(regionCode: String): String =
        NSLocale.currentLocale.displayNameForKey(NSLocaleCountryCode, regionCode) ?: regionCode
}

private class IosLogger : Logger {
    override fun debug(tag: String, message: String) {
        println("D/$tag: $message")
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        println("E/$tag: $message${throwable?.let { "\n" + it.stackTraceToString() }.orEmpty()}")
    }
}
