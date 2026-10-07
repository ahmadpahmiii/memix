package app.memix.platform.services

import android.util.Log
import app.memix.core.domain.DeviceRegion
import app.memix.core.domain.Logger
import java.util.Locale

internal actual class PlatformDeviceRegion : DeviceRegion {
    override fun regionCode(): String? = Locale.getDefault().country.ifEmpty { null }

    override fun displayName(regionCode: String): String =
        Locale.Builder().setRegion(regionCode).build().displayCountry
}

internal actual class PlatformLogger : Logger {
    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}
