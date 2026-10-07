package app.memix.android

import android.app.Application
import app.memix.initKoin

class MemixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}
