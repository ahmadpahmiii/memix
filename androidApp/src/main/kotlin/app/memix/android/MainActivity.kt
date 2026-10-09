package app.memix.android

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.memix.App
import app.memix.debug.ExportCheck
import app.memix.debug.ImportSaveFailure
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Memix is dark-only, so system bar icons stay light even when the phone uses a light theme.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val isDebugBuild = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // Debug-only Crashlytics check (P0-08): adb shell am start -n app.memix/.android.MainActivity --ez memix.testCrash true
        if (isDebugBuild && intent.getBooleanExtra("memix.testCrash", false)) throw RuntimeException("Memix test crash")
        // Debug-only project checks (P1-01 round trip, P1-07 auto-save): --es memix.projectCheck <step>, see ProjectChecks.kt.
        val projectCheckStep = intent.getStringExtra("memix.projectCheck")
        if (isDebugBuild && projectCheckStep != null && savedInstanceState == null) startProjectCheck(projectCheckStep)
        // Debug-only export (P1-03): push the test media first (TECHNICAL_DESIGN), then --ez memix.exportCheck true. Logcat tag MemixExportCheck.
        if (isDebugBuild && intent.getBooleanExtra("memix.exportCheck", false)) lifecycleScope.launch { ExportCheck().run() }
        // Debug and benchmark builds (P1-04): --es memix.openEditor <project> opens a hand-check project in the editor.
        // Projects and steps: docs/qa/phase-1/engineer-hand-checks.md → P1-04. Logcat tag MemixEditorCheck.
        val handChecks = resources.getBoolean(R.bool.memix_hand_checks)
        val editorCheck = if (handChecks && savedInstanceState == null) intent.getStringExtra("memix.openEditor") else null
        // Debug and benchmark builds (P1-02 N2): --ez memix.failImportSave true makes the next import's first save fail as if
        // the phone were full. Steps: docs/qa/phase-1/engineer-hand-checks.md → P1-04.
        if (handChecks && savedInstanceState == null && intent.getBooleanExtra("memix.failImportSave", false)) ImportSaveFailure.arm()
        setContent { App(isDebugBuild, debugEditorProject = editorCheck) }
    }
}
