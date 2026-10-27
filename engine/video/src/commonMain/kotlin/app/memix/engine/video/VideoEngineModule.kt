package app.memix.engine.video

import org.koin.core.module.Module

/** Binds the domain's `VideoEngine` to this platform's implementation: Media3 on Android, a stub on iOS until P7. */
expect val videoEngineModule: Module

/**
 * The Koin property (a Boolean) that turns on the engine's hand-check logs, such as the preview's sound onsets. The
 * composition root sets it in builds with hand checks (debug, benchmark).
 */
const val HAND_CHECKS_PROPERTY = "memix.handChecks"
