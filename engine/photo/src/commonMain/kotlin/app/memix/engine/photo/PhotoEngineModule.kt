package app.memix.engine.photo

import org.koin.core.module.Module

/** Binds the scene's picture decoder ([app.memix.core.ui.scene.SceneImageSource]) to this platform's implementation. */
expect val photoEngineModule: Module
