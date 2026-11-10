package app.memix.engine.segmentation

import org.koin.core.module.Module

/** Binds [app.memix.core.domain.segmentation.Segmenter] to this platform's implementation. */
expect val segmentationEngineModule: Module
