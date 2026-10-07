package app.memix.platform.services

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.util.Log
import app.memix.core.domain.DeviceRegion
import app.memix.core.domain.Logger
import java.util.Locale
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformServicesModule = module {
    single<DeviceRegion> { AndroidDeviceRegion(androidContext()) }
    single<Logger> { AndroidLogger() }
}

private class AndroidDeviceRegion(private val context: Context) : DeviceRegion {
    override fun regionCode(): String? = systemLocale().country.ifEmpty { null }

    override fun displayName(regionCode: String): String =
        Locale.Builder().setRegion(regionCode).build().getDisplayCountry(Locale.getDefault())

    // A per-app language (Android 13+) replaces Locale.getDefault(), so the phone's region comes from the system list.
    private fun systemLocale(): Locale {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Locale.getDefault()
        val systemLocales = context.getSystemService(LocaleManager::class.java).systemLocales
        return if (systemLocales.isEmpty) Locale.getDefault() else systemLocales[0]
    }
}

private class AndroidLogger : Logger {
    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}
