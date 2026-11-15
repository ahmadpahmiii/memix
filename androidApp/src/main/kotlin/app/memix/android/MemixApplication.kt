package app.memix.android

import android.app.Application
import android.content.Context
import app.memix.initKoin
import app.memix.platform.services.LanguageOverride
import org.koin.android.ext.koin.androidContext

class MemixApplication : Application() {
    // Android 12 and below: the language chosen in the Region sheet (no-op on 13+, where the system keeps it).
    override fun attachBaseContext(base: Context) = super.attachBaseContext(LanguageOverride.wrap(base))

    override fun onCreate() {
        super.onCreate()
        // A language stored on Android 12 goes to the system now, before the first frame (no-op below 13).
        LanguageOverride.handOverToSystem(this)
        // Hand-check hooks: on in debug and benchmark builds (res/values/hand_checks.xml per build type).
        initKoin(handChecks = resources.getBoolean(R.bool.memix_hand_checks)) { androidContext(this@MemixApplication) }
    }
}
