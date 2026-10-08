package app.memix.engine.video

import org.koin.core.module.Module

/** Binds the domain's `VideoEngine` to this platform's implementation: Media3 on Android, a stub on iOS until P7. */
expect val videoEngineModule: Module
