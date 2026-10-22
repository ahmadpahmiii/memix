package app.memix

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

// SharedPreferences loads its file on the first read, which :core:data runs off the main thread.
internal actual val settingsModule = module {
    single<Settings> { SharedPreferencesSettings(androidContext().getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)) }
}

private const val SETTINGS_FILE = "memix_settings"
