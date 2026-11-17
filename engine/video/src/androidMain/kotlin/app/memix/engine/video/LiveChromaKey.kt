package app.memix.engine.video

import app.memix.core.model.project.ChromaKey

/**
 * A clip's key as the shader reads it every frame. The preview changes [key] while a Strength or Edge softness slider moves, so the
 * matte follows the thumb without rebuilding the composition (spec P4-04 → Interactions); an export never changes it. The GL thread
 * reads, the main thread writes: one reference, replaced whole.
 */
internal class LiveChromaKey(initial: ChromaKey) {
    @Volatile
    var key: ChromaKey = initial
}
