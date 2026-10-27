package app.memix.engine.video

import android.os.SystemClock
import app.memix.core.domain.Logger

/**
 * Logs how long a new composition takes to reach the screen, one line each under the tag MemixPreview:
 * - "Preview opened in N ms (build B ms, first frame F ms)": from creating the preview to its first picture.
 * - "Edit shown in N ms (rebuild R ms, first frame F ms)": from `update(project)` to the first picture of the edited
 *   project. Media3 1.11.1 rebuilds every player on each edit (P1-04 review R1), and P1-06's bar is 100 ms per edit.
 *
 * Build or rebuild runs until `setComposition` returns (planning, file checks and building on the IO thread, then
 * the player's own setup); first frame runs from there to the player reporting its first rendered frame. Main thread
 * only. A newer start replaces a measurement still running, so quick edits report only the last one.
 */
internal class ShowTimingLog(private val logger: Logger) {
    enum class Change(val label: String, val buildLabel: String) {
        OPEN("Preview opened", "build"),
        EDIT("Edit shown", "rebuild"),
    }

    private var change: Change? = null
    private var startedAtMs = 0L
    private var compositionSetAtMs = NOT_YET

    fun start(change: Change) {
        this.change = change
        startedAtMs = SystemClock.elapsedRealtime()
        compositionSetAtMs = NOT_YET
    }

    fun compositionSet() {
        if (change != null) compositionSetAtMs = SystemClock.elapsedRealtime()
    }

    fun firstFrameShown() {
        val shown = change ?: return
        if (compositionSetAtMs == NOT_YET) return
        val nowMs = SystemClock.elapsedRealtime()
        logger.debug(
            TAG,
            "${shown.label} in ${nowMs - startedAtMs} ms (${shown.buildLabel} ${compositionSetAtMs - startedAtMs} ms, " +
                "first frame ${nowMs - compositionSetAtMs} ms)",
        )
        change = null
    }

    /** Called a while after [compositionSet]: if the player never reported a first frame, says so once. */
    fun firstFrameMissing() {
        val shown = change ?: return
        if (compositionSetAtMs == NOT_YET) return
        logger.debug(
            TAG,
            "${shown.label}: no first frame reported ${SystemClock.elapsedRealtime() - compositionSetAtMs} ms after " +
                "the composition was set (${shown.buildLabel} ${compositionSetAtMs - startedAtMs} ms)",
        )
        change = null
    }

    private companion object {
        const val TAG = "MemixPreview"
        const val NOT_YET = -1L
    }
}
