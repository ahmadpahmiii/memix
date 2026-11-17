package app.memix.engine.video

/**
 * Lets one look rebuild run at a time (P4-07). A slider that moves 60 times a second on a clip whose composition lacks the look's pass asks
 * for a rebuild on every move; only the first may start one, or the preview would cancel its own build each time and never show. When a build
 * is shown, [shown] says whether looks asked for meanwhile still need one more.
 */
internal class LookRebuildGate {
    private var pending = false

    /** True when the caller should start a rebuild now; false while one is already being built. */
    fun request(): Boolean {
        if (pending) return false
        pending = true
        return true
    }

    /** A composition was shown (or failed): true when [stillUncovered] looks need exactly one more rebuild, which is then pending. */
    fun shown(stillUncovered: Boolean): Boolean {
        pending = false
        return stillUncovered && request()
    }
}
