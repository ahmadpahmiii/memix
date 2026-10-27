package app.memix.engine.video

import app.memix.core.domain.text.CaptionMeasurer
import app.memix.core.domain.video.VideoEngine
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
    // One renderer for the editor's caption measures and stand-in and the engine's overlays (P1-10).
    single { AndroidCaptionRenderer(fontSource = get(), ioDispatcher = Dispatchers.IO, logger = get()) }
    single<CaptionMeasurer> { get<AndroidCaptionRenderer>() }
}
