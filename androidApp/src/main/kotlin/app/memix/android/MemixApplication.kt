package app.memix.android

import android.app.Application
import app.memix.initKoin
import org.koin.android.ext.koin.androidContext

class MemixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hand-check hooks: on in debug and benchmark builds (res/values/hand_checks.xml per build type).
        initKoin(handChecks = resources.getBoolean(R.bool.memix_hand_checks)) { androidContext(this@MemixApplication) }
    }
}
