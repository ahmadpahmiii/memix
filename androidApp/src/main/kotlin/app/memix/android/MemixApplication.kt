package app.memix.android

import android.app.Application
import app.memix.initKoin
import org.koin.android.ext.koin.androidContext

class MemixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@MemixApplication) }
    }
}
