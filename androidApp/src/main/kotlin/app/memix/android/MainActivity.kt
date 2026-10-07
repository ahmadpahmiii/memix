package app.memix.android

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.memix.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Memix is dark-only, so system bar icons stay light even when the phone uses a light theme.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val isDebugBuild = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // Debug-only Crashlytics check (P0-08): adb shell am start -n app.memix/.android.MainActivity --ez memix.testCrash true
        if (isDebugBuild && intent.getBooleanExtra("memix.testCrash", false)) throw RuntimeException("Memix test crash")
        setContent { App(isDebugBuild) }
    }
}
