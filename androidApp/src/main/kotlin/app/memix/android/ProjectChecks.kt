package app.memix.android

import android.os.Process
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import app.memix.debug.AutosaveCheck
import app.memix.debug.ProjectRoundTripCheck
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch

/**
 * Debug-only project hand checks, started with `--es memix.projectCheck <step>` (TECHNICAL_DESIGN.md →
 * Project data model). MainActivity calls this in debug builds only. Logcat tag MemixProjectCheck.
 * - P1-01 round trip: `save`, force-stop, then `verify`.
 * - P1-07 auto-save: `autosave`, optionally with `--ei memix.killAfterMs <ms>` or `--ez memix.background true`,
 *   then `autosave-verify` in a new process.
 */
internal fun ComponentActivity.startProjectCheck(step: String) {
    when (step) {
        ProjectRoundTripCheck.SAVE, ProjectRoundTripCheck.VERIFY -> lifecycleScope.launch { ProjectRoundTripCheck().run(step) }
        AutosaveCheck.RUN, AutosaveCheck.VERIFY -> startAutosaveCheck(step)
        else -> Log.e(ProjectRoundTripCheck.TAG, "Unknown memix.projectCheck step '$step'. Use save, verify, autosave or autosave-verify.")
    }
}

private fun ComponentActivity.startAutosaveCheck(step: String) {
    val check = AutosaveCheck(
        // SIGKILL, the same signal as kill -9: the app gets no callback and no chance to save.
        killProcess = { Process.killProcess(Process.myPid()) },
        moveToBackground = { moveTaskToBack(true) },
    )
    // Stands in for the editor screen, which saves at once when the app goes to the background.
    lifecycle.addObserver(
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) lifecycleScope.launch { check.onAppStopped() }
        },
    )
    val killAfter = intent.getIntExtra(EXTRA_KILL_AFTER_MS, -1).takeIf { it >= 0 }?.milliseconds
    val background = intent.getBooleanExtra(EXTRA_BACKGROUND, false)
    lifecycleScope.launch {
        if (step == AutosaveCheck.RUN) check.run(killAfter, background) else check.verify()
    }
}

private const val EXTRA_KILL_AFTER_MS = "memix.killAfterMs"
private const val EXTRA_BACKGROUND = "memix.background"
