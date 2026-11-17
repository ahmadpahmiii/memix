package app.memix.engine.video

/**
 * The effect items the effects pass draws, read every frame by the GL thread and replaced whole by the main thread (one reference, so a
 * frame never sees half a list). An add, move, trim or Intensity change of an effect item replaces [spans] and the player draws the new
 * list from its next frame, with no new composition; only the first effect of a project, or its last one going, changes what the composition
 * is built of (see `Media3PreviewSession`). An export never changes it.
 */
internal class LiveEffects(initial: List<EffectSpan>) {
    @Volatile
    var spans: List<EffectSpan> = initial
}
