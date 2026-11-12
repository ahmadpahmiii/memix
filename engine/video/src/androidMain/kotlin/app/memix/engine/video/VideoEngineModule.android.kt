package app.memix.engine.video

import app.memix.core.domain.mysound.SoundImporter
import app.memix.core.domain.text.CaptionMeasurer
import app.memix.core.domain.video.VideoEngine
import app.memix.core.ui.scene.SceneCaptions
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val videoEngineModule = module {
    // Lazy like every Koin single: nothing Media3 loads until the first export.
    single<VideoEngine> {
        Media3VideoEngine(
            androidContext(),
            logger = get(),
            ioDispatcher = Dispatchers.IO,
            captionRenderer = get(),
            measureSoundOnsets = getProperty(HAND_CHECKS_PROPERTY, false),
        )
    }
    // My sounds' import (P3-07): converts a picked audio file, or a video's sound, to an .m4a.
    single<SoundImporter> { Media3SoundImporter(androidContext(), ioDispatcher = Dispatchers.IO, logger = get()) }
    // One renderer for the editor's caption measures and stand-in and the engine's overlays (P1-10).
    single { AndroidCaptionRenderer(fontSource = get(), ioDispatcher = Dispatchers.IO, logger = get()) }
    single<CaptionMeasurer> { get<AndroidCaptionRenderer>() }
    // The photo editor's text layers draw with the same renderer (P2-03).
    single<SceneCaptions> { AndroidSceneCaptions(get<AndroidCaptionRenderer>()) }
}
