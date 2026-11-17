package app.memix.engine.video

import app.memix.core.model.project.ClipMask

/**
 * A clip's mask as the shader reads it every frame. The preview changes [mask] while a finger moves the mask on the stage or a Mask
 * slider drags, so the picture follows without rebuilding the composition (spec P4-03b → Live feedback); an export never changes it.
 * The GL thread reads, the main thread writes: one reference, replaced whole.
 */
internal class LiveMask(initial: ClipMask) {
    @Volatile
    var mask: ClipMask = initial
}
