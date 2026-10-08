package app.memix.engine.video

import android.view.Choreographer

/**
 * Calls [onFrame] once per display frame between [start] and [stop], on the main thread, in step with the frames
 * the screen draws. Create, start and stop it on the main thread.
 */
internal class FrameTicker(private val onFrame: () -> Unit) : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private var running = false

    fun start() {
        if (running) return
        running = true
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        running = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        onFrame()
        choreographer.postFrameCallback(this)
    }
}
