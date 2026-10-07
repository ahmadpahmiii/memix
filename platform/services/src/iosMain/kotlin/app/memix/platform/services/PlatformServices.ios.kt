package app.memix.platform.services

import app.memix.core.domain.DeviceRegion
import app.memix.core.domain.Logger
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale

internal actual class PlatformDeviceRegion : DeviceRegion {
    override fun regionCode(): String? = NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String

    override fun displayName(regionCode: String): String =
        NSLocale.currentLocale.displayNameForKey(NSLocaleCountryCode, regionCode) ?: regionCode
}

internal actual class PlatformLogger : Logger {
    override fun debug(tag: String, message: String) {
        println("D/$tag: $message")
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        println("E/$tag: $message${throwable?.let { "\n" + it.stackTraceToString() }.orEmpty()}")
    }
}
